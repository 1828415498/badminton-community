package com.badmintoncommunity.module.activity.scheduler;

import com.badmintoncommunity.module.activity.mapper.ActivityMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ActivityStatusSchedulerProfileTest {

    @Test
    void normalProfileStillRegistersScheduler() {
        new ApplicationContextRunner()
                .withBean(ActivityMapper.class, () -> mock(ActivityMapper.class))
                .withUserConfiguration(ActivityStatusScheduler.class)
                .run(context -> assertThat(context).hasSingleBean(ActivityStatusScheduler.class));
    }
}
