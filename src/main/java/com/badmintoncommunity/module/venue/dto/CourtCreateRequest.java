package com.badmintoncommunity.module.venue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员新增球场请求（api.md §3.4）。
 */
@Data
public class CourtCreateRequest {

    @NotNull(message = "venue_id 不能为空")
    private Long venueId;

    @NotBlank(message = "球场名不能为空")
    @Size(max = 32, message = "球场名长度不能超过 32")
    private String name;
}
