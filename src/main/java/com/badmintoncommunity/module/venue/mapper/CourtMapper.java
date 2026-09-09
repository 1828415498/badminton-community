package com.badmintoncommunity.module.venue.mapper;

import com.badmintoncommunity.module.venue.entity.Court;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 球场数据访问。球场名称在同场馆内唯一（数据库唯一索引兜底）。
 */
@Mapper
public interface CourtMapper {

    @Select("SELECT id, venue_id, name, status, create_time, update_time FROM court WHERE id = #{id}")
    Court findById(Long id);

    /** 加排他行锁的读取：停用球场等操作需要先锁住球场行，串行化并发写入（business-flows 动作 12） */
    @Select("SELECT id, venue_id, name, status, create_time, update_time FROM court "
            + "WHERE id = #{id} FOR UPDATE")
    Court findByIdForUpdate(Long id);

    /**
     * 查询某场馆下的球场，可选按 status 过滤。
     * MyBatis 注解动态 SQL 必须用 &lt;script&gt; 包裹整条 SQL。
     */
    @Select("<script>"
            + "SELECT id, venue_id, name, status, create_time, update_time FROM court "
            + "WHERE venue_id = #{venueId} "
            + "<if test='status != null'>AND status = #{status}</if> "
            + "ORDER BY id"
            + "</script>")
    List<Court> findByVenue(@Param("venueId") Long venueId, @Param("status") Integer status);

    @Insert("INSERT INTO court (venue_id, name, status) VALUES (#{venueId}, #{name}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Court court);

    @Update("UPDATE court SET name = #{name} WHERE id = #{id}")
    int rename(@Param("id") Long id, @Param("name") String name);

    @Update("UPDATE court SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
