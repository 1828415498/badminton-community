package com.badmintoncommunity.security;

/**
 * 请求级用户上下文。
 *
 * <h2>为什么需要它</h2>
 * Controller / Service 经常需要"当前登录的是谁"。与其在每个方法签名里层层传参，
 * 不如认证拦截器把用户放进一个"只属于本请求"的变量里，任何层随时可取。
 *
 * <h2>ThreadLocal 是什么</h2>
 * 一个"线程私有"的盒子：每个 HTTP 请求由 Tomcat 的一个工作线程处理，
 * ThreadLocal 保证不同请求（不同线程）互不干扰。
 *
 * <p>生命周期：AuthInterceptor.preHandle 写入 → 请求处理中可读 → afterCompletion 清理。
 * 清理很重要：工作线程会被复用，不清理会把上一个用户的身份带给下一个请求。</p>
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
        // 工具类禁止实例化
    }

    /** 认证拦截器调用：把当前用户写入本请求的上下文 */
    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    /** 读取当前用户（可能为 null——表示匿名/未设置） */
    public static LoginUser get() {
        return HOLDER.get();
    }

    /**
     * 读取当前用户 id（供已认证接口使用）。
     * 若上下文里没有用户（理论上不会发生，因为被拦截器挡在门外），抛出异常暴露问题。
     */
    public static Long requireUserId() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new IllegalStateException("no authenticated user in context");
        }
        return user.userId();
    }

    /** 请求结束时清理，防止线程复用串号 */
    public static void clear() {
        HOLDER.remove();
    }
}
