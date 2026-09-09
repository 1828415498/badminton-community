package com.badmintoncommunity.module.venue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员修改球场名请求（api.md §3.5）。
 */
@Data
public class CourtUpdateRequest {

    @NotBlank(message = "球场名不能为空")
    @Size(max = 32, message = "球场名长度不能超过 32")
    private String name;
}
