package com.badmintoncommunity.module.venue.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 场馆（表 venue，database.md §4.2）。V1 只有一行数据。
 */
@Data
public class Venue {

    private Long id;
    private String name;
    private String address;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
