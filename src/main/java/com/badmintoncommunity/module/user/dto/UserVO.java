package com.badmintoncommunity.module.user.dto;

import com.badmintoncommunity.module.user.entity.User;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息（响应，api.md §2.2/§2.3/§2.4）。
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
    private Integer role;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setRole(user.getRole());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
