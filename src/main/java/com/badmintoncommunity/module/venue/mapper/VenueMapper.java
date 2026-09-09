package com.badmintoncommunity.module.venue.mapper;

import com.badmintoncommunity.module.venue.entity.Venue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 场馆数据访问（V1 只有一家馆，查询极简单）。
 */
@Mapper
public interface VenueMapper {

    @Select("SELECT id, name, address, create_time, update_time FROM venue")
    List<Venue> findAll();

    @Select("SELECT id, name, address, create_time, update_time FROM venue WHERE id = #{id}")
    Venue findById(Long id);
}
