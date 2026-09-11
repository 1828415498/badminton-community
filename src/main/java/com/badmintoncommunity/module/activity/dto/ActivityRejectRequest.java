package com.badmintoncommunity.module.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员驳回活动请求（api.md §5.8）。
 *
 * <p>驳回必须给出原因：原因会持久化到 activity.reject_reason 并展示给创建者，
 * 让创建者知道"哪里不合规"，而不是只看到一个状态变化。</p>
 */
@Data
public class ActivityRejectRequest {

    @NotBlank(message = "reject_reason 不能为空")
    @Size(max = 255, message = "reject_reason 长度不能超过 255")
    private String rejectReason;
}
