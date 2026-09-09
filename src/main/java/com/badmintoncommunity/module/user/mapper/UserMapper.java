package com.badmintoncommunity.module.user.mapper;

import com.badmintoncommunity.module.user.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户数据访问层（DAO）——原生 MyBatis（ADR-0008）。
 *
 * <h2>为什么 Mapper 是"接口"而不是类？</h2>
 * <pre>
 * @Mapper                       // 告诉 MyBatis-Spring：扫描并注册这个接口
 * public interface UserMapper {
 *     @Select("...")            // SQL 直接写在注解里（简单 SQL 用注解，复杂 SQL 进 XML）
 *     User findByUsername(String username);
 * }
 * </pre>
 * MyBatis 在运行时为接口生成一个实现类（动态代理）：你调用 {@code findByUsername(...)}
 * 时，代理对象按注解里的 SQL 执行查询，把结果行自动映射成 {@link User} 对象。
 *
 * <h2>#{xxx} 是什么</h2>
 * 是 SQL 占位符（不是字符串拼接！）。MyBatis 用 PreparedStatement 传参，天然防 SQL 注入。
 * 例如 #{username} 会把参数 username 的值作为 ? 绑定进去。
 *
 * <h2>字段映射约定</h2>
 * 数据库列 snake_case（user_name）→ 实体属性 camelCase（userName），
 * 由 application.yml 里 mybatis.configuration.map-underscore-to-camel-case=true 自动完成。
 */
@Mapper
public interface UserMapper {

    /** 按登录名查用户（登录/重名校验用）。结果若不存在返回 null。 */
    @Select("SELECT id, username, password_hash, nickname, avatar_url, role, create_time, update_time "
            + "FROM user WHERE username = #{username}")
    User findByUsername(String username);

    /** 按主键查用户。 */
    @Select("SELECT id, username, password_hash, nickname, avatar_url, role, create_time, update_time "
            + "FROM user WHERE id = #{id}")
    User findById(Long id);

    /**
     * 插入新用户。
     *
     * @param user 入参实体；插入成功后其 id 会被自动回填
     * @return 受影响行数（通常为 1）
     * @Options(useGeneratedKeys=true)  = 让数据库自增主键
     * @Options(keyProperty="id")       = 把生成的自增 id 写回 user.id 属性
     *                                    这样 insert 之后能立刻拿到 user.getId()
     */
    @Insert("INSERT INTO user (username, password_hash, nickname, avatar_url, role) "
            + "VALUES (#{username}, #{passwordHash}, #{nickname}, #{avatarUrl}, #{role})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    /** 更新昵称与头像（username/role/password 不可经此修改）。 */
    @Update("UPDATE user SET nickname = #{nickname}, avatar_url = #{avatarUrl} WHERE id = #{id}")
    int updateProfile(User user);
}
