package com.badmintoncommunity.config;

import com.badmintoncommunity.module.file.config.UploadProperties;
import com.badmintoncommunity.security.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web 配置：
 * 1) 注册认证拦截器并声明「哪些接口可以匿名访问」（白名单）；
 * 2) 把上传目录挂成静态资源，让 &lt;img&gt; 能直接读到；
 * 3) 提供 PasswordEncoder Bean（密码哈希算法）。
 *
 * <h2>@Configuration 与 @Bean 的分工</h2>
 * <pre>
 * @Configuration          —— 这是一个「配置类」，Spring 启动时会读它
 * implements WebMvcConfigurer —— 实现它就能改 Spring MVC 的行为（加拦截器、挂静态资源…）
 * @Bean                  —— 方法返回值交给 Spring 容器管理，别处可直接注入
 * </pre>
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    /** 认证拦截器（也是 Spring 容器里的 Bean，见 AuthInterceptor） */
    private final AuthInterceptor authInterceptor;

    /** 上传配置，用来定位磁盘上的图片目录 */
    private final UploadProperties uploadProperties;

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
                        "/api/courts/*/occupancy",
                        // 已上传文件的读取。必须匿名：&lt;img src&gt; 不会带 Authorization 头，
                        // 若要求认证则页面上所有头像都会变成裂图。
                        // 安全性由 32 位随机文件名兜底（猜不到 = 看不到）。
                        "/api/files/**"
                );
    }

    /**
     * 把磁盘上的上传目录挂到 /api/files/** 下，供 &lt;img&gt; 直接访问。
     *
     * <p>上传的文件是运行时写到磁盘的，不在打包好的 classpath 里，
     * Spring 默认的 /static 找不到它们，必须显式指路。路径前缀与
     * {@link UploadProperties#getUrlPrefix()} 同源，避免手写字符串两处对不上。</p>
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize();
        String location = root.toUri().toString();
        // 结尾没有 "/" 时，Spring 会把它当成「单个文件」而不是目录
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler(uploadProperties.getUrlPrefix() + "/**")
                .addResourceLocations(location);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt：不可逆哈希，自带随机盐，同一明文每次结果不同，抗彩虹表。
        // 强度 10（默认）对 MVP 足够。
        return new BCryptPasswordEncoder();
    }
}
