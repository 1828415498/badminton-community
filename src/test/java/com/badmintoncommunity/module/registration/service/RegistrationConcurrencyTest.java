package com.badmintoncommunity.module.registration.service;

import com.badmintoncommunity.common.BusinessException;
import com.badmintoncommunity.support.Concurrency;
import com.badmintoncommunity.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("活动报名最后名额并发")
class RegistrationConcurrencyTest extends IntegrationTestBase {

    @Autowired
    private RegistrationService registrationService;

    @Test
    void onlyOneOfFourUsersGetsLastPlace() throws Exception {
        long creator = fx.user("last-creator");
        long activityId = publishedActivity(creator, "last-place", 2);
        fx.registration(creator, activityId, 1);
        List<Long> candidates = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            candidates.add(fx.user("last-candidate-" + i));
        }

        Map<String, Integer> outcome = Concurrency.race(4,
                i -> attempt(candidates.get(i), activityId, "OK"));

        assertEquals(1, outcome.getOrDefault("OK", 0), outcome.toString());
        assertEquals(3, outcome.getOrDefault("ACTIVITY_FULL", 0), outcome.toString());
        assertEquals(2, fx.countActiveRegistrations(activityId));
        long candidateRows = candidates.stream()
                .mapToLong(id -> fx.countRegistrationRows(id, activityId)).sum();
        assertEquals(1, candidateRows, "失败请求不得留下报名行");
    }

    @Test
    void cancelledMemberAndNewMemberCompeteForLastPlace() throws Exception {
        long creator = fx.user("restore-creator");
        long cancelledMember = fx.user("restore-cancelled");
        long newMember = fx.user("restore-new");
        long activityId = publishedActivity(creator, "restore-place", 2);
        fx.registration(creator, activityId, 1);
        fx.registration(cancelledMember, activityId, 2);

        Map<String, Integer> outcome = Concurrency.race(2, i -> i == 0
                ? attempt(cancelledMember, activityId, "RESTORED")
                : attempt(newMember, activityId, "NEW"));

        assertEquals(1, outcome.getOrDefault("RESTORED", 0)
                + outcome.getOrDefault("NEW", 0), outcome.toString());
        assertEquals(1, outcome.getOrDefault("ACTIVITY_FULL", 0), outcome.toString());
        assertEquals(2, fx.countActiveRegistrations(activityId));
        assertEquals(1, fx.countRegistrationRows(cancelledMember, activityId),
                "重新报名只能恢复历史行");
        assertEquals(outcome.getOrDefault("NEW", 0).longValue(), fx.countRegistrationRows(newMember, activityId),
                "新用户失败时不能插入记录");
    }

    private long publishedActivity(long creator, String suffix, int maxMembers) {
        LocalDateTime start = LocalDate.now().plusDays(1).atTime(10, 0);
        return fx.activity(creator, suffix, start, start.plusHours(1), maxMembers, 1);
    }

    private String attempt(long userId, long activityId, String successLabel) {
        try {
            registrationService.register(userId, activityId);
            return successLabel;
        } catch (BusinessException ex) {
            return ex.getResultCode().getCode();
        } catch (Exception ex) {
            return "UNEXPECTED:" + ex.getClass().getSimpleName();
        }
    }
}
