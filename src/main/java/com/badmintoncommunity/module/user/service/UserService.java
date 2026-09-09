package com.badmintoncommunity.module.user.service;

import com.badmintoncommunity.common.BusinessException;
import com.badmintoncommunity.common.ResultCode;
import com.badmintoncommunity.module.user.dto.LoginRequest;
import com.badmintoncommunity.module.user.dto.LoginResult;
import com.badmintoncommunity.module.user.dto.RegisterRequest;
import com.badmintoncommunity.module.user.dto.UserUpdateRequest;
import com.badmintoncommunity.module.user.dto.UserVO;
import com.badmintoncommunity.module.user.entity.User;
import com.badmintoncommunity.module.user.mapper.UserMapper;
import com.badmintoncommunity.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户/认证服务：注册、登录、当前用户查询/修改（business-flows 动作 1/2，api.md §2）。
 *
 * <h2>依赖注入是怎么发生的（本类用「构造器注入」）</h2>
 * <pre>
 * @Service                     // 1. 把这个类交给 Spring 管理（容器里有一个 UserService 对象）
 * @RequiredArgsConstructor     // 2. Lombok：为下面所有 final 字段自动生成构造器
 * public class UserService {
 *     private final UserMapper userMapper;        // 3. 声明「我需要一个 UserMapper」
 *     ...
 * }
 * </pre>
 * 运行时流程：
 * 1) Spring 启动时扫描到 @Service，准备创建 UserService；
 * 2) 发现类上只有「一个构造器」（由 Lombok 生成，参数 = 所有 final 字段）；
 * 3) Spring 到容器里找 UserMapper / PasswordEncoder / JwtUtil 这三个 Bean，
 *    调用该构造器把它们"塞"进来 —— 这就是「构造器注入」；
 * 4) 之后任何地方注入 UserService，拿到的都是这个已被填好依赖的对象。
 *
 * <p>对比另一种写法 {@code @Autowired private UserMapper userMapper;}（字段注入）：
 * 构造器注入的字段是 final、对象创建后不可被换掉、单测可直接 new 传假依赖，官方推荐。</p>
 */
@Service
@RequiredArgsConstructor
public class UserService {

    /**
     * 数据访问层：只有接口定义，实现由 MyBatis 在运行时生成（见 UserMapper 注释）
     */
    private final UserMapper userMapper;

    /**
     * 密码编码器（真正的实现是 BCryptPasswordEncoder，见 WebConfig 的 @Bean）。
     * 这里只看得到接口，不关心具体算法 —— 这就是"面向接口编程"。
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * JWT 工具：签发/解析登录令牌
     */
    private final JwtUtil jwtUtil;

    /**
     * 注册。
     *
     * <p>@Transactional：本方法是一个事务 —— 里面所有 SQL 要么全部成功，要么全部回滚。
     * 虽然这里只有一次 insert，但写上注解是约定：凡涉及"写数据库"的 Service 方法都要有事务。</p>
     */
    @Transactional
    public void register(RegisterRequest req) {
        // 业务规则 1：用户名不能重复。
        // 先查一次是为了给出友好错误；即使并发下两个人同时注册，
        // 数据库的 username 唯一索引仍会兜底（查与插之间存在极短窗口）。
        if (userMapper.findByUsername(req.getUsername()) != null) {
            // 抛业务异常 → 会被 GlobalExceptionHandler 捕获并转成 Result(code=USERNAME_TAKEN)
            throw new BusinessException(ResultCode.USERNAME_TAKEN);
        }

        User user = new User();
        user.setUsername(req.getUsername());
        // 绝不能存明文密码：BCrypt 是不可逆哈希，且每次生成带随机盐（同一密码两次结果不同）
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname());
        user.setAvatarUrl(null);      // 注册阶段没有头像
        user.setRole(0);              // 注册用户一律普通用户；管理员只能手工/初始化写入（database.md §4.1）
        userMapper.insert(user);      // MyBatis 自动把 user.id 回填（见 Mapper @Options）
    }

    /**
     * 登录：校验凭据，成功则签发 JWT。
     *
     * <p>readOnly = true：本方法只读不写，数据库连接可用只读模式（性能提示，非强制）。</p>
     */
    @Transactional(readOnly = true)
    public LoginResult login(LoginRequest req) {
        User user = userMapper.findByUsername(req.getUsername());

        // 统一错误提示：用户名不存在 与 密码错误 返回同一个错误码，
        // 避免攻击者通过报错差异判断"这个账号是否存在"（账号枚举）。
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS);
        }

        // 认证通过 → 签发令牌。JWT 是无状态的（ADR-0007）：
        // 服务端不保存会话，后续请求只要带这个 token，服务端解析即可识别身份。
        String token = jwtUtil.generateToken(user.getId(), user.getRole());
        return new LoginResult(UserVO.from(user), token);
    }

    /**
     * 获取当前登录用户资料（api.md §2.3）。userId 来自 JWT，由拦截器写入 UserContext。
     */
    @Transactional(readOnly = true)//表明这个事务是处于只读的 这个方法是查询 查询的时候事务通常都会加上这个
    public UserVO getCurrentUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            // 理论上已认证的用户必然存在；防御性兜底
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return UserVO.from(user);
    }

    /**
     * 修改当前用户资料（api.md §2.4）：只允许改 nickname / avatar_url，username 与 role 不可改。
     */
    @Transactional
    public UserVO updateCurrentUser(Long userId, UserUpdateRequest req) {
        // 接口契约：两项至少要传一项（否则没有内容可改）
        // 第一个是检查是否为空或者是一堆空格
        if (!StringUtils.hasText(req.getNickname()) && req.getAvatarUrl() == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "nickname 与 avatar_url 至少传一项");

        }
            // 先读回完整实体，再就地修改 → 保证 update 时其它字段不被覆盖
            User user = userMapper.findById(userId);
            if (user == null) {
                throw new BusinessException(ResultCode.UNAUTHORIZED);
            }
            if (StringUtils.hasText(req.getNickname())) {//用户传递进来的不是空也不是一堆空格
                user.setNickname(req.getNickname());
            }
            user.setAvatarUrl(req.getAvatarUrl());

            userMapper.updateProfile(user);
            return UserVO.from(user);
        }

    }

