package com.badmintoncommunity.config;

import com.badmintoncommunity.security.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：
 * 1) 注册认证拦截器并声明"哪些接口可以匿名访问"（白名单）；
 * 2) 提供 PasswordEncoder Bean（密码哈希算法）。
 *
 * <h2>@Configuration 与 @Bean</h2>
 * <pre>
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    /** 认证拦截器（也是 Spring 容器里的 Bean，见 AuthInterceptor） */
    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        // 认证（匿名）
                        "/api/auth/register",
                        "/api/auth/login",
                        // 场馆/球场公开只读（api.md §3）
                        "/api/venues",
                        "/api/venues/**",
                        "/api/courts/*/occupancy"
                );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt：不可逆哈希，自带随机盐，同一明文每次结果不同，抗彩虹表。
        // 强度 10（默认）对 MVP 足够。
        return new BCryptPasswordEncoder();
    }
}
