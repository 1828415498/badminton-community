package com.badmintoncommunity.module.registration.service;

import com.badmintoncommunity.common.BusinessException;
import com.badmintoncommunity.common.ResultCode;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    @DisplayName("满员时取消与新报名同时发生：允许释放后报名或保守拒绝，但不能超员")
    void cancellationAndNewRegistrationKeepCapacityConsistent() throws Exception {
        long creator = fx.user("cancel-race-creator");
        long leavingMember = fx.user("cancel-race-leaver");
        long newMember = fx.user("cancel-race-new");
        long activityId = publishedActivity(creator, "cancel-race", 2);
        fx.registration(creator, activityId, 1);
        fx.registration(leavingMember, activityId, 1);

        Map<String, Integer> outcome = Concurrency.race(2, i -> i == 0
                ? cancelAttempt(leavingMember, activityId)
                : attempt(newMember, activityId, "NEW_OK"));

        int newMemberJoined = outcome.getOrDefault("NEW_OK", 0);
        assertEquals(1, outcome.getOrDefault("CANCEL_OK", 0), outcome.toString());
        assertEquals(1, newMemberJoined + outcome.getOrDefault("ACTIVITY_FULL", 0), outcome.toString());
        assertEquals(1L + newMemberJoined, fx.countActiveRegistrations(activityId),
                "取消后活动只可能剩创建者，或创建者加新成员");
        assertEquals(1, fx.countRegistrationRows(leavingMember, activityId),
                "取消应保留原报名行");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE user_id = ? AND activity_id = ? AND status = 2",
                Long.class, leavingMember, activityId));
        assertEquals(newMemberJoined, fx.countRegistrationRows(newMember, activityId),
                "被满员拒绝时不得留下报名行");
    }

    @Test
    @DisplayName("满员被拒绝后，成员取消并提交，新用户重试可使用释放的名额")
    void retryAfterCommittedCancellationUsesReleasedPlace() {
        long creator = fx.user("retry-creator");
        long leavingMember = fx.user("retry-leaver");
        long newMember = fx.user("retry-new");
        long activityId = publishedActivity(creator, "retry-after-cancel", 2);
        fx.registration(creator, activityId, 1);
        fx.registration(leavingMember, activityId, 1);

        BusinessException full = assertThrows(BusinessException.class,
                () -> registrationService.register(newMember, activityId));
        assertEquals(ResultCode.ACTIVITY_FULL, full.getResultCode());
        assertEquals(0, fx.countRegistrationRows(newMember, activityId));

        registrationService.cancelRegistration(leavingMember, activityId);
        registrationService.register(newMember, activityId);

        assertEquals(2, fx.countActiveRegistrations(activityId));
        assertEquals(1, fx.countRegistrationRows(newMember, activityId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE user_id = ? AND activity_id = ? AND status = 2",
                Long.class, leavingMember, activityId));
    }

    @Test
    @DisplayName("同一成员取消与重报、另一成员争名额同时发生时，报名行和容量保持一致")
    void cancellationRejoinAndNewRegistrationKeepOnePlaceConsistent() throws Exception {
        long creator = fx.user("three-way-creator");
        long member = fx.user("three-way-member");
        long newcomer = fx.user("three-way-newcomer");
        long activityId = publishedActivity(creator, "three-way-place", 2);
        fx.registration(creator, activityId, 1);
        fx.registration(member, activityId, 1);

        Map<String, Integer> outcome = Concurrency.race(3, i -> switch (i) {
            case 0 -> cancelAttempt(member, activityId);
            case 1 -> "REJOIN_" + attempt(member, activityId, "OK");
            default -> "NEW_" + attempt(newcomer, activityId, "OK");
        });

        assertEquals(1, outcome.getOrDefault("CANCEL_OK", 0), outcome.toString());
        assertEquals(1, outcome.getOrDefault("REJOIN_OK", 0)
                + outcome.getOrDefault("REJOIN_ACTIVITY_FULL", 0), outcome.toString());
        assertEquals(1, outcome.getOrDefault("NEW_OK", 0)
                + outcome.getOrDefault("NEW_ACTIVITY_FULL", 0), outcome.toString());
        assertEquals(3, outcome.values().stream().mapToInt(Integer::intValue).sum(), outcome.toString());

        assertEquals(1, fx.countRegistrationRows(member, activityId), "取消与重报不能产生第二条历史行");
        assertEquals(outcome.getOrDefault("NEW_OK", 0).longValue(),
                fx.countRegistrationRows(newcomer, activityId), "新成员被拒绝时不能留下报名行");
        long memberActive = jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE user_id = ? AND activity_id = ? AND status = 1",
                Long.class, member, activityId);
        long newcomerActive = jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE user_id = ? AND activity_id = ? AND status = 1",
                Long.class, newcomer, activityId);
        long active = fx.countActiveRegistrations(activityId);
        assertEquals(1L + memberActive + newcomerActive, active, "有效人数须与每条报名行一致");
        assertTrue(active <= 2, "三方争抢不能超过活动人数上限");
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

    private String cancelAttempt(long userId, long activityId) {
        try {
            registrationService.cancelRegistration(userId, activityId);
            return "CANCEL_OK";
        } catch (BusinessException ex) {
            return ex.getResultCode().getCode();
        } catch (Exception ex) {
            return "UNEXPECTED:" + ex.getClass().getSimpleName();
        }
    }
}
