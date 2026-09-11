package com.badmintoncommunity.module.registration.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动成员列表项（api.md §6.3）。
 *
 * <p>从 registration 出发 join user 取昵称与头像 —— 报名行只有 user_id，
 * 展示所需的资料属于 user 表，查询时关联，不做冗余存储。</p>
 */
@Data
public class ActivityMemberVO {

    /** registration.id（不是 user.id —— 成员列表的粒度是"报名行"） */
    private Long id;

    private Long userId;
    private String nickname;
    private String avatarUrl;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
