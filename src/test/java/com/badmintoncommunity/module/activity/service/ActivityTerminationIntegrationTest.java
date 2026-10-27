package com.badmintoncommunity.module.activity.service;

import com.badmintoncommunity.common.BusinessException;
import com.badmintoncommunity.common.ResultCode;
import com.badmintoncommunity.module.registration.service.RegistrationService;
import com.badmintoncommunity.security.LoginUser;
import com.badmintoncommunity.support.Concurrency;
import com.badmintoncommunity.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActivityTerminationIntegrationTest extends IntegrationTestBase {

    private static final String FAIL_TRIGGER = "tjtest_fail_registration_update";

    @Autowired
    private ActivityService activityService;

    @Autowired
    private RegistrationService registrationService;

    @Test
    void rejectionReleasesAllCourtsAndRegistrations() {
        ActivityFixture fixture = pendingActivity("reject");
        long adminId = fx.user("reject-admin");
        jdbc.update("UPDATE `user` SET role = 1 WHERE id = ?", adminId);

        activityService.reject(new LoginUser(adminId, 1), fixture.activityId(), "不符合要求");

        assertActivityState(fixture.activityId(), 2, 0, 0);
    }

    @Test
    void creatorCancellationReleasesAllCourtsAndRegistrations() {
        ActivityFixture fixture = pendingActivity("cancel");

        activityService.cancelByCreator(fixture.creatorId(), fixture.activityId());

        assertActivityState(fixture.activityId(), 3, 0, 0);
    }

    @Test
    void creatorCancellationAndRegistrationCannotLeaveActiveMember() throws Exception {
        ActivityFixture fixture = activityFixture("cancel-signup",
                LocalDate.now().plusDays(1).atTime(10, 0), 1);
        long newMember = fx.user("cancel-signup-new");

        Map<String, Integer> outcome = Concurrency.race(2, i -> i == 0
                ? cancelAttempt(fixture)
                : registerAttempt(newMember, fixture.activityId()));

        int registrationSucceeded = outcome.getOrDefault("REGISTER_OK", 0);
        assertEquals(1, outcome.getOrDefault("CANCEL_OK", 0), outcome.toString());
        assertEquals(1, registrationSucceeded + outcome.getOrDefault("ACTIVITY_STATE_INVALID", 0),
                outcome.toString());
        assertActivityState(fixture.activityId(), 3, 0, 0);
        assertEquals(registrationSucceeded, fx.countRegistrationRows(newMember, fixture.activityId()),
                "取消先提交时不得留下新报名行");
        assertEquals(registrationSucceeded, jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE user_id = ? AND activity_id = ? AND status = 2",
                Long.class, newMember, fixture.activityId()),
                "报名先提交时，其记录也必须被活动取消作废");
        BusinessException afterCancellation = assertThrows(BusinessException.class,
                () -> registrationService.register(newMember, fixture.activityId()));
        assertEquals(ResultCode.ACTIVITY_STATE_INVALID, afterCancellation.getResultCode());
    }

    @Test
    void rejectionRollsBackActivityAndCourtChangesWhenRegistrationUpdateFails() {
        ActivityFixture fixture = pendingActivity("rollback");
        long adminId = fx.user("rollback-admin");
        jdbc.update("UPDATE `user` SET role = 1 WHERE id = ?", adminId);

        jdbc.execute("DROP TRIGGER IF EXISTS " + FAIL_TRIGGER);
        try {
            jdbc.execute("CREATE TRIGGER " + FAIL_TRIGGER
                    + " BEFORE UPDATE ON registration FOR EACH ROW "
                    + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected registration failure'");

            assertThrows(DataAccessException.class,
                    () -> activityService.reject(new LoginUser(adminId, 1), fixture.activityId(), "不符合要求"));
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS " + FAIL_TRIGGER);
        }

        assertActivityState(fixture.activityId(), 0, 2, 2);
        Long reviewerId = jdbc.queryForObject("SELECT reviewer_id FROM activity WHERE id = ?",
                Long.class, fixture.activityId());
        assertNull(reviewerId, "审核痕迹也必须回滚");
    }

    @Test
    void approvalKeepsCourtsAndRegistrationsActive() {
        ActivityFixture fixture = pendingActivity("approve");
        long adminId = fx.user("approve-admin");
        jdbc.update("UPDATE `user` SET role = 1 WHERE id = ?", adminId);

        activityService.approve(new LoginUser(adminId, 1), fixture.activityId());

        assertActivityState(fixture.activityId(), 1, 2, 2);
        Long reviewerId = jdbc.queryForObject("SELECT reviewer_id FROM activity WHERE id = ?",
                Long.class, fixture.activityId());
        LocalDateTime reviewTime = jdbc.queryForObject("SELECT review_time FROM activity WHERE id = ?",
                LocalDateTime.class, fixture.activityId());
        assertEquals(adminId, reviewerId);
        assertNotNull(reviewTime);
    }

    @Test
    void secondReviewIsRejectedWithoutReleasingResources() {
        ActivityFixture fixture = pendingActivity("review-twice");
        long adminId = fx.user("review-twice-admin");
        jdbc.update("UPDATE `user` SET role = 1 WHERE id = ?", adminId);
        LoginUser admin = new LoginUser(adminId, 1);
        activityService.approve(admin, fixture.activityId());

        BusinessException error = assertThrows(BusinessException.class,
                () -> activityService.reject(admin, fixture.activityId(), "重复审核"));

        assertEquals(ResultCode.ACTIVITY_STATE_INVALID, error.getResultCode());
        assertActivityState(fixture.activityId(), 1, 2, 2);
        String reason = jdbc.queryForObject("SELECT reject_reason FROM activity WHERE id = ?",
                String.class, fixture.activityId());
        assertNull(reason);
    }

    @Test
    void adminCanCancelAlreadyStartedActivity() {
        LocalDateTime start = LocalDateTime.now().minusMinutes(30);
        ActivityFixture fixture = activityFixture("admin-cancel", start, 1);
        long adminId = fx.user("admin-cancel-admin");
        jdbc.update("UPDATE `user` SET role = 1 WHERE id = ?", adminId);

        activityService.cancelByAdmin(new LoginUser(adminId, 1), fixture.activityId());

        assertActivityState(fixture.activityId(), 3, 0, 0);
    }

    private ActivityFixture pendingActivity(String suffix) {
        return activityFixture(suffix, LocalDate.now().plusDays(1).atTime(10, 0), 0);
    }

    private String cancelAttempt(ActivityFixture fixture) {
        try {
            activityService.cancelByCreator(fixture.creatorId(), fixture.activityId());
            return "CANCEL_OK";
        } catch (BusinessException ex) {
            return ex.getResultCode().getCode();
        } catch (Exception ex) {
            return "UNEXPECTED:" + ex.getClass().getSimpleName();
        }
    }

    private String registerAttempt(long userId, long activityId) {
        try {
            registrationService.register(userId, activityId);
            return "REGISTER_OK";
        } catch (BusinessException ex) {
            return ex.getResultCode().getCode();
        } catch (Exception ex) {
            return "UNEXPECTED:" + ex.getClass().getSimpleName();
        }
    }

    private ActivityFixture activityFixture(String suffix, LocalDateTime start, int status) {
        long creatorId = fx.user(suffix + "-creator");
        long memberId = fx.user(suffix + "-member");
        long venueId = fx.venue(suffix + "-venue");
        long courtA = fx.court(venueId, suffix + "-court-a", 1);
        long courtB = fx.court(venueId, suffix + "-court-b", 1);
        LocalDateTime end = start.plusHours(1);
        long activityId = fx.activity(creatorId, suffix + "-activity", start, end, 3, status);
        fx.activityReservation(courtA, activityId, start, end);
        fx.activityReservation(courtB, activityId, start, end);
        fx.registration(creatorId, activityId, 1);
        fx.registration(memberId, activityId, 1);
        return new ActivityFixture(activityId, creatorId);
    }

    private void assertActivityState(long activityId, int activityStatus,
                                     int activeCourts, int activeRegistrations) {
        Integer status = jdbc.queryForObject("SELECT status FROM activity WHERE id = ?",
                Integer.class, activityId);
        Long courtCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reservation WHERE activity_id = ? AND status = 1",
                Long.class, activityId);
        assertEquals(activityStatus, status);
        assertEquals(activeCourts, courtCount);
        assertEquals(activeRegistrations, fx.countActiveRegistrations(activityId));
    }

    private record ActivityFixture(long activityId, long creatorId) {
    }
}
