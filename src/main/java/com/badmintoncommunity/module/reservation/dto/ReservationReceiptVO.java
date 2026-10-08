package com.badmintoncommunity.module.reservation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预约凭证响应（报表打印用）。
 *
 * <p>数据来源是数据库视图 <b>v_reservation_detail</b>（见 {@code sql/views.sql}），
 * 而不是直接查 reservation 表。原因：</p>
 * <ul>
 *   <li>reservation 三端全是外键（court_id / user_id / activity_id），直查只有编号，
 *       打不出一张人看得懂的凭证；视图已经把场馆名、球场名、占用方 JOIN 好了。</li>
 *   <li>{@code duration_hours} 与 {@code status_text} 是<b>导出项</b> ——
 *       它们由视图实时算出来，reservation 表里没有对应字段。</li>
 *   <li>{@code source_type} 把「个人预约 / 活动占场」这个二选一语义翻译成人话。</li>
 * </ul>
 *
 * <p>与 {@link ReservationVO} 的区别：VO 用于接口返回与业务判断，本类专供打印排版，
 * 因此额外带了场馆名、时长、状态文本等展示字段。字段名与视图列名一一对应
 * （下划线转驼峰，由 MyBatis 的 map-underscore-to-camel-case 自动完成）。</p>
 */
@Data
public class ReservationReceiptVO {

    /** 占用编号（视图列 reservation_id，即 reservation.id） */
    private Long reservationId;

    private Long courtId;

    /** 场馆名（视图 JOIN venue 得到） */
    private String venueName;

    /** 球场名，如「3 号场」（视图 JOIN court 得到） */
    private String courtName;

    /** 个人预约时为本人的 id；活动占场行为空 */
    private Long userId;

    private String userNickname;

    /** 活动占场时为活动 id；个人预约行为空 */
    private Long activityId;

    private String activityTitle;

    /** 占用方展示名：个人预约显示昵称，活动占场显示活动标题（视图用 COALESCE 得到） */
    private String ownerDisplay;

    /** 占用来源：个人预约 / 活动占场（视图由 activity_id 是否为空推导） */
    private String sourceType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 占用时长（小时）—— 导出项，由视图 TIMESTAMPDIFF 实时算出 */
    private Integer durationHours;

    /** 1=有效，2=已取消 */
    private Integer status;

    /** 状态文本：有效 / 已取消（导出项，由视图实时映射） */
    private String statusText;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cancelTime;

    /** 下单时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
