package com.badmintoncommunity.module.venue.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 球场（表 court，database.md §4.3）。
 * 球场是预约冲突的最小单位；status 表示能否被预约。
 */
@Data
public class Court {

    private Long id;
    private Long venueId;
    /** 球场名，如「1 号场」；同场馆内唯一 */
    private String name;
    /** 1=可预约，2=不可预约 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
