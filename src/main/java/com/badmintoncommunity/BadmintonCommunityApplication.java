package com.badmintoncommunity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 羽毛球社区管理系统入口。
 *
 * <p>{@code @EnableScheduling} 打开 Spring 的定时任务能力，
 * 用于把"已过期的活动"定期置为「已结束」（见 ActivityStatusScheduler）。</p>
 *
 * @see docs/business-flows.md
 * @see docs/api.md
 */
@SpringBootApplication
@EnableScheduling
public class BadmintonCommunityApplication {

    public static void main(String[] args) {
        SpringApplication.run(BadmintonCommunityApplication.class, args);
    }
}
