package com.badmintoncommunity.module.venue.dto;

import com.badmintoncommunity.module.venue.entity.Venue;
import lombok.Data;

/**
 * 场馆信息（响应，api.md §3.1）。
 */
@Data
public class VenueVO {

    private Long id;
    private String name;
    private String address;

    public static VenueVO from(Venue v) {
        VenueVO vo = new VenueVO();
        vo.setId(v.getId());
        vo.setName(v.getName());
        vo.setAddress(v.getAddress());
        return vo;
    }
}
