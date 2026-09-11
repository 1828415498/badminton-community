package com.badmintoncommunity.module.activity.scheduler;

import com.badmintoncommunity.module.activity.mapper.ActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 活动状态维护任务（Q1=A 的落地实现）。
 *
 * <h2>解决什么业务</h2>
 * <p>活动"结束"是一个<b>由时间自然发生</b>的状态：到了 end_time，活动就该从"已发布"变成"已结束"。
 * 但数据库不会自己变 —— 必须有个东西定期把过期的活动挑出来改状态，否则活动永远停在"已发布"，
 * 列表里一直显示"可报名"。</p>
 *
 * <h2>为什么用定时任务，而不是"查询时顺便改"</h2>
 * <p>另一种做法是"谁读到过期活动谁就顺手 UPDATE 一下"（懒置位）。它的坏处是把<b>写操作混进读路径</b>：
 * 一个只读接口突然开始改数据，既让权限边界含糊，也让并发下的更新互相打架。
 * 集中到定时任务后，读接口保持纯净、只读，状态推进只有这一个入口。</p>
 *
 * <h2>为什么是"每分钟、固定延迟"，不是 cron 整点</h2>
 * <p>{@code fixedDelay} 表示"上一次执行结束后再等 60 秒"，不会出现任务堆积；
 * 60 秒的粒度对"活动结束"这种业务足够精确（最坏情况晚 1 分钟体现，无实际影响）。</p>
 *
 * <p><b>注意</b>：状态 4 只影响"能不能报名"和"列表怎么展示"，它<b>不影响场地占用</b> ——
 * 活动结束后占场行仍是 status=1，因为那些时段本来就是被这个活动用掉的，不该凭空释放。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityStatusScheduler {

    private final ActivityMapper activityMapper;

    /**
     * 每分钟扫描一次：把「已发布(1) 且 end_time 已过」的活动批量置为「已结束(4)」。
     *
     * <p>这是一条批量 UPDATE，单条 SQL 完成，不需要遍历 —— 无论有多少活动都是一次往返。</p>
     */
    @Scheduled(fixedDelay = 60_000L)
    @Transactional
    public void finishExpiredActivities() {
        int affected = activityMapper.finishExpired(LocalDateTime.now());
        if (affected > 0) {
            log.info("已将 {} 个到期活动置为「已结束」", affected);
        }
    }
}
