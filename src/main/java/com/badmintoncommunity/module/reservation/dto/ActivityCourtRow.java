package com.badmintoncommunity.module.reservation.dto;

import lombok.Data;

/**
 * 「活动 × 占用球场」的平铺行（MyBatis 直接映射用，不对外）。
 *
 * <p>用途：列表接口一次性拿到多个活动各占了哪几块场。SQL 返回的是
 * 「一行一个 (activityId, courtId) 组合」，Service 在 Java 里聚合成
 * {@code Map<活动 id, List<球场 id>>} 再回填到 {@code ActivityItemVO.courtIds}。</p>
 *
 * <p>为什么不在列表 SQL 里 join：一个活动可占多块场，join 会把主表行数
 * 打散成笛卡尔积（一个活动变成 N 行），分页 LIMIT 立刻失真。
 * 所以必须"主表查询 + 批量补字段"两步走。</p>
 */
@Data
public class ActivityCourtRow {

    private Long activityId;
    private Long courtId;
}
