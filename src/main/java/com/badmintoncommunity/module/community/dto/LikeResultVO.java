package com.badmintoncommunity.module.community.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 点赞/取消点赞的响应（api.md §7.8 / §7.9）：{@code data = {liked: true|false}}。
 *
 * <p>只回一个布尔值：调用方（前端）只需要知道"操作完成后是什么状态"，
 * 点赞总数由帖子详情/列表的 like_count 提供，不在这里重复下发。</p>
 */
@Data
@AllArgsConstructor
public class LikeResultVO {

    private Boolean liked;
}
