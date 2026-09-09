package com.badmintoncommunity.module.venue.dto;

import com.badmintoncommunity.module.venue.entity.Court;
import lombok.Data;

/**
 * 球场信息（响应，api.md §3.2 / §3.4-3.7）。
 */
@Data
public class CourtVO {

    private Long id;
    private Long venueId;
    private String name;
    private Integer status;

    public static CourtVO from(Court c) {
        CourtVO vo = new CourtVO();
        vo.setId(c.getId());
        vo.setVenueId(c.getVenueId());
        vo.setName(c.getName());
        vo.setStatus(c.getStatus());
        return vo;
    }
}
