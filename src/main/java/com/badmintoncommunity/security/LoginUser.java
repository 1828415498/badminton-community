package com.badmintoncommunity.security;

/**
 * 当前登录用户（一个不可变的小对象）。
 *
 * <p>用 record 定义：自动生成构造器、equals/hashCode/toString，且字段天然不可变。
 * 等价于传统写法 {@code class LoginUser { private final Long userId; private final Integer role; ...getter } }，
 * 只是更简洁。</p>
 *
 * <p>JWT 解析成功后由 AuthInterceptor 放入 UserContext（ADR-0007：认证层只回答"你是谁"）。</p>
 */
public record LoginUser(Long userId, Integer role) {
}
