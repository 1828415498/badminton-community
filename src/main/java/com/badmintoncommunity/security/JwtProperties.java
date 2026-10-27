package com.badmintoncommunity.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置（app.jwt.*）。签名密钥由 APP_JWT_SECRET 环境变量提供。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** 签名密钥，HS256 要求不少于 32 字节 */
    private String secret;

    /** 令牌有效期（小时） */
    private long expireHours = 24;
}
