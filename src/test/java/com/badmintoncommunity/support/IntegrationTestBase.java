package com.badmintoncommunity.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试基类：接好测试库、停掉定时任务、维护夹具数据。
 *
 * <h2>为什么单独抽一个基类</h2>
 * <p>这三件事每一个用例都需要，写错任何一个都会得到"看起来在测、其实没测对"的结果：
 * 忘了 @ActiveProfiles 会连到开发库；忘了关定时任务会中途改掉被测数据；
 * 忘了清夹具会让第二个用例看到第一个用例留下的行。</p>
 *
 * <h2>关于定时任务（ActivityStatusScheduler）</h2>
 * <p>它以 {@code fixedDelay = 60s} 运行，把「已发布且 end_time 已过」的活动刷成「已结束」。
 * 这在测"已发布活动能报名"时是<b>敌对行为</b> —— 它会在用例跑到一半时把被测对象改掉。
 * 所以基类在第一个用例开始前就把所有已注册的定时任务取消掉。</p>
 *
 * <p>之所以用 {@link ObjectProvider} 而不是直接 {@code @Autowired}：万一哪天 Spring 改了
 * 调度器的注册方式导致该 bean 不存在，也只会"少一层保护"，不会让整套测试启动失败。</p>
 *
 * <h2>关于事务</h2>
 * <p>本基类<b>刻意不加</b> {@code @Transactional}：并发用例必须让被测方法提交真实事务，
 * 否则多线程互相看不见对方的数据，测的就成了空气。需要自动回滚的纯逻辑用例，
 * 在自己类上加 {@code @Transactional} 即可。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected Fixtures fx;

    @Autowired
    private ObjectProvider<ScheduledTaskHolder> scheduledTaskHolderProvider;

    @BeforeEach
    void prepareFixtureData() {
        disarmScheduledTasks();
        fx.cleanAll();
    }

    @AfterEach
    void clearFixtureData() {
        fx.cleanAll();
    }

    /**
     * 取消全部已注册的定时任务。
     *
     * <p>幂等，可重复调用。必须在上下文启动后 60 秒内执行 —— 也就是第一个用例的
     * {@code @BeforeEach}，正常情况下远早于这个时间。</p>
     */
    private void disarmScheduledTasks() {
        scheduledTaskHolderProvider.ifAvailable(holder -> {
            for (ScheduledTask task : holder.getScheduledTasks()) {
                task.cancel();
            }
        });
    }
}
