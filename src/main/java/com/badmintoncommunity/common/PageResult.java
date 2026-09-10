package com.badmintoncommunity.common;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 统一分页结构（api.md §1.3 列表响应）。JSON 序列化后为 list/total/page/page_size。
 */
@Data
@AllArgsConstructor
public class PageResult<T> {

    private List<T> list;
    private long total;
    private long page;
    private long pageSize;
}
