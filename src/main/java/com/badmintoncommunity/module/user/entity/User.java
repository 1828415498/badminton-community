package com.badmintoncommunity.module.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体 —— 对应数据库表 user 的「一行」记录（database.md §4.1）。
 *
 * <h2>三层中的位置</h2>
 * Controller（接收 HTTP）→ Service（业务）→ Mapper（SQL）→ Entity：数据载体
 *
 * <p>Entity 的属性名与表字段一一对应（下划线→驼峰由 MyBatis 自动映射）：
 * 表字段 username ↔ 属性 username；表字段 password_hash ↔ 属性 passwordHash。</p>
 *
 * <h2>@Data 是什么</h2>
 * Lombok 注解：自动生成 getter / setter / equals / hashCode / toString。
 * 所以代码里能直接写 user.getUsername()、user.setNickname(...)，
 * 这些方法在编译时由 Lombok 补全，源码里看不到。
 */
@Data
public class User {

    /** 主键，自增；insert 后由 MyBatis 回填 */
    private Long id;

    /** 登录名，唯一（数据库唯一索引兜底） */
    private String username;

    /** 密码哈希（BCrypt）。注意：这是"哈希"，不是密码本身，永远不反推明文 */
    private String passwordHash;

    /** 展示昵称（社区/活动列表对外显示用） */
    private String nickname;

    /** 头像 URL，可空 */
    private String avatarUrl;

    /** 角色：0=普通用户，1=管理员（database.md 枚举集合） */
    private Integer role;

    /** 创建时间（数据库 DEFAULT CURRENT_TIMESTAMP 自动填充，查询时读回） */
    private LocalDateTime createTime;

    /** 更新时间（数据库 ON UPDATE 自动维护） */
    private LocalDateTime updateTime;
}
