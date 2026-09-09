package com.badmintoncommunity.security;

import com.badmintoncommunity.common.Result;
import com.badmintoncommunity.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * JWT 认证拦截器（ADR-0007）。
 *
 * <h2>拦截器 = 请求的"安检门"</h2>
 * 每个 /api 请求在到达 Controller <b>之前</b>，会先经过这里：
 * <pre>
 * 请求 → AuthInterceptor.preHandle()
 *          ├─ 白名单路径？          → 直接放行（注册/登录等，见 WebConfig）
 *          ├─ 没带 Authorization？ → 直接返回 401，请求到此为止
 *          ├─ token 解析失败/过期？ → 直接返回 401
 *          └─ 解析成功 → 把用户写入 UserContext（ThreadLocal）→ 放行进 Controller
 *
 * Controller 处理完 → afterCompletion() 清理 UserContext（防止线程复用残留）
 * </pre>
 *
 * <h2>认证 ≠ 授权</h2>
 * 拦截器只负责"证明你是谁"（解析 token）。"你是否有权限做这件事"
 * （管理员、资源归属等）由各 Service 方法判断 —— 与 ADR-0007 一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    /** 解析 token 用 */
    private final JwtUtil jwtUtil;

    /** 用来把 401 响应体序列化成 JSON（Spring 提供，可直接注入） */
    private final ObjectMapper objectMapper;

    /**
     * 请求进入 Controller 前执行。
     *
     * @return true=放行；false=拦截（已写好 401 响应）
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 浏览器跨域预检请求（OPTIONS）不需要认证，直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 约定格式：Authorization: Bearer <token>
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response);
            return false;
        }

        // 去掉 "Bearer " 前缀，拿到纯 token
        String token = header.substring(BEARER_PREFIX.length());
        try {
            Claims claims = jwtUtil.parse(token);
            // 把「当前用户」放进线程局部变量：本请求内任何 Service 都能通过 UserContext 拿到
            UserContext.set(new LoginUser(jwtUtil.userIdOf(claims), jwtUtil.roleOf(claims)));
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // token 非法/过期/被篡改
            log.debug("invalid jwt: {}", e.getMessage());
            writeUnauthorized(response);
            return false;
        }
    }

    /**
     * 请求处理完后执行（无论成功失败）。必须清理 ThreadLocal，
     * 否则容器线程池复用线程时会读到上一个请求残留的用户。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    /** 手动写一个 401 的 JSON 响应（此时还没到 Controller，普通异常处理器管不到这里） */
    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ResultCode.UNAUTHORIZED)));
    }
}
