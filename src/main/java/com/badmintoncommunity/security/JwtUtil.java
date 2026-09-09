package com.badmintoncommunity.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 的签发与解析（ADR-0007：认证采用 JWT，无状态）。
 *
 * <h2>JWT 是什么</h2>
 * 登录成功后服务端签发的一串"令牌"。形如：{头部}.{载荷}.{签名}，内容可被任何人读取
 * （不要放敏感信息），但签名只有持有密钥的服务端能验证——篡改会校验失败。
 *
 * <h2>本类职责</h2>
 * <pre>
 * generateToken(userId, role)  登录成功后调用：把 userId 放进 subject、role 放进 claim，用密钥签名
 * parse(token)                 后续每个请求调用：验证签名+有效期，返回载荷（Claims）
 * </pre>
 *
 * <p>载荷只放最小信息。解析只回答「你是谁」；能不能做某事（角色/归属）由 Service 层判断
 * （认证与授权分离，见 ADR-0007）。</p>
 */
@Component
public class JwtUtil {

    /** 我们把角色放在 JWT 的 claim 里的键名 */
    public static final String CLAIM_ROLE = "role";

    /** 配置（密钥、有效期），来自 application.yml 的 app.jwt.* */
    private final JwtProperties props;

    /**
     * 由配置字符串构造的 HMAC 签名密钥。构造器里算好一次，之后所有方法复用。
     * 注意：这里没有 @RequiredArgsConstructor，因为需要在构造器里做额外初始化（生成 key）。
     * Spring 看到唯一构造器（带 JwtProperties 参数）依然会自动注入。
     */
    private final SecretKey key;

    public JwtUtil(JwtProperties props) {
        this.props = props;
        // HS256 要求密钥 >= 32 字节；把配置里的 secret 字符串转成 SecretKey
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 签发 token：载荷 = 用户 id（subject）+ 角色（claim），附带签发/过期时间并签名。 */
    public String generateToken(Long userId, Integer role) {
        Date now = new Date();
        // 过期时间 = 当前时间 + 配置的小时数（application.yml: app.jwt.expire-hours: 24）
        Date expiry = new Date(now.getTime() + props.getExpireHours() * 3600_000L);
        return Jwts.builder()
                .subject(String.valueOf(userId))     // 标准字段 subject 放用户 id
                .claim(CLAIM_ROLE, role)             // 自定义 claim 放角色
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)                       // 用密钥签名（防篡改）
                .compact();                          // 序列化成最终 token 字符串
    }

    /**
     * 解析并校验 token（验证签名 → 校验有效期 → 返回载荷）。
     *
     * @param token 请求头里 Bearer 后面的部分
     * @return 载荷（可从中取 userId / role）
     * @throws JwtException 签名不合法 / 已过期 / 格式错误
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)     // 用同一把密钥验证签名
                .build()
                .parseSignedClaims(token)
                .getPayload();       // 校验通过 → 返回 claims
    }

    /** 从载荷里取用户 id（我们放进了 subject）。 */
    public Long userIdOf(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    /** 从载荷里取角色（我们放进了 role claim）。 */
    public Integer roleOf(Claims claims) {
        return claims.get(CLAIM_ROLE, Integer.class);
    }
}
