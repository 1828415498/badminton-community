package com.badmintoncommunity.module.registration.dto;

import com.badmintoncommunity.module.activity.dto.ActivityItemVO;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「我参加的活动」列表项（api.md §6.4）。
 *
 * <p>响应按 api.md 约定做成嵌套结构：外层是"我的报名"，内层是"被报名的活动"。
 * 这样前端既能显示"我什么时候报的名、报名还有效吗"，也能显示活动本身的信息。</p>
 */
@Data
public class MyRegistrationVO {

    private RegInfo registration;
    private ActivityItemVO activity;

    /** 内层：只暴露报名行自身的信息（不含用户/活动 id，外层已隐含归属） */
    @Data
    public static class RegInfo {

        private Long id;

        /** 1=已报名，2=已取消 */
        private Integer status;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
    }
}
