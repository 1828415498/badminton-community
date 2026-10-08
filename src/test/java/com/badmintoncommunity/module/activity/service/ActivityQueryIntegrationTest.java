package com.badmintoncommunity.module.activity.service;

import com.badmintoncommunity.common.BusinessException;
import com.badmintoncommunity.common.PageResult;
import com.badmintoncommunity.module.activity.dto.ActivityItemVO;
import com.badmintoncommunity.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActivityQueryIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ActivityService activityService;

    @Test
    void combinedFiltersKeepCountAndPaginationConsistentIncludingEndDate() {
        long creator = fx.user("query-owner");
        LocalDate day = LocalDate.now().plusDays(1);
        long earlier = fx.activity(creator, "周末双打早场", day.atTime(8, 0), day.atTime(9, 0), 4, 1);
        long later = fx.activity(creator, "周末双打晚场", day.atTime(23, 0), day.atTime(23, 30), 4, 1);
        fx.activity(creator, "单打练习", day.atTime(10, 0), day.atTime(11, 0), 4, 1);
        fx.activity(creator, "周末双打结束", day.atTime(12, 0), day.atTime(13, 0), 4, 4);
        fx.activity(creator, "周末双打次日", day.plusDays(1).atTime(0, 0), day.plusDays(1).atTime(1, 0), 4, 1);

        PageResult<ActivityItemVO> first = activityService.listPublic(
                "  周末双打  ", 1, day.atStartOfDay(), day.atTime(23, 59, 59), null, 1, 1);
        PageResult<ActivityItemVO> second = activityService.listPublic(
                "周末双打", 1, day.atStartOfDay(), day.atTime(23, 59, 59), null, 2, 1);

        assertEquals(2, first.getTotal());
        assertEquals(2, second.getTotal());
        assertEquals(1, first.getList().size());
        assertEquals(1, second.getList().size());
        assertEquals(later, first.getList().get(0).getId());
        assertEquals(earlier, second.getList().get(0).getId());
    }

    @Test
    void keywordSearchTreatsWildcardCharactersLiterallyAndHidesNonpublicActivities() {
        long creator = fx.user("query-literal-owner");
        LocalDateTime start = LocalDate.now().plusDays(1).atTime(10, 0);
        long visible = fx.activity(creator, "查询%_字面值", start, start.plusHours(1), 4, 1);
        fx.activity(creator, "查询普通标题", start, start.plusHours(1), 4, 1);
        fx.activity(creator, "查询%_待审核", start, start.plusHours(1), 4, 0);

        PageResult<ActivityItemVO> result = activityService.listPublic(
                "%_", null, null, null, null, 1, 20);

        assertEquals(1, result.getTotal());
        assertEquals(visible, result.getList().get(0).getId());
        assertThrows(BusinessException.class,
                () -> activityService.listPublic("%_", 0, null, null, null, 1, 20));
    }

    @Test
    void invalidQueryConditionsAreRejected() {
        LocalDateTime start = LocalDate.now().plusDays(1).atStartOfDay();
        assertThrows(BusinessException.class,
                () -> activityService.listPublic(null, null, start, start.minusSeconds(1), null, 1, 20));
        assertThrows(BusinessException.class,
                () -> activityService.listPublic("球".repeat(101), null, null, null, null, 1, 20));
    }
}
