package com.badmintoncommunity.module.activity.service;

import com.badmintoncommunity.common.BusinessException;
import com.badmintoncommunity.module.activity.dto.CreateActivityRequest;
import com.badmintoncommunity.module.reservation.dto.CreateReservationRequest;
import com.badmintoncommunity.module.reservation.service.ReservationService;
import com.badmintoncommunity.support.Concurrency;
import com.badmintoncommunity.support.Fixtures;
import com.badmintoncommunity.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("活动与预约占场并发竞争")
class ActivityReservationConcurrencyTest extends IntegrationTestBase {

    @Autowired
    private ActivityService activityService;

    @Autowired
    private ReservationService reservationService;

    @Test
    void onlyOneOccupancyAndNoOrphanActivity() throws Exception {
        long venueId = fx.venue("mixed-venue");
        long courtId = fx.court(venueId, "mixed-court", 1);
        long creatorId = fx.user("mixed-creator");
        long bookerId = fx.user("mixed-booker");
        LocalDateTime start = LocalDate.now().plusDays(1).atTime(10, 0);
        LocalDateTime end = start.plusHours(1);

        Map<String, Integer> outcome = Concurrency.race(2, i -> i == 0
                ? createActivity(creatorId, courtId, start, end)
                : createReservation(bookerId, courtId, start, end));

        assertEquals(1, outcome.getOrDefault("ACTIVITY_OK", 0)
                + outcome.getOrDefault("RESERVATION_OK", 0), outcome.toString());
        assertEquals(1, outcome.getOrDefault("COURT_ALREADY_RESERVED", 0), outcome.toString());
        assertEquals(1, fx.countActiveReservations(courtId, start, end));
        Long activities = jdbc.queryForObject(
                "SELECT COUNT(*) FROM activity WHERE creator_id = ?", Long.class, creatorId);
        Long activityOccupancies = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reservation WHERE court_id = ? AND activity_id IS NOT NULL AND status = 1",
                Long.class, courtId);
        assertEquals(activities, activityOccupancies, "活动创建失败时不能留下活动主体或占场");
        Long creatorRegistrations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE user_id = ? AND status = 1",
                Long.class, creatorId);
        assertEquals(activities, creatorRegistrations, "活动主体与创建者报名必须一起提交");
    }

    @Test
    @DisplayName("两块场的活动与第二块场的个人预约竞争：活动只能完整成功或完整失败")
    void multiCourtActivityCommitsAllOrNothingAgainstPersonalReservation() throws Exception {
        long venueId = fx.venue("multi-venue");
        long firstCourt = fx.court(venueId, "multi-first", 1);
        long secondCourt = fx.court(venueId, "multi-second", 1);
        long creatorId = fx.user("multi-creator");
        long bookerId = fx.user("multi-booker");
        LocalDateTime start = LocalDate.now().plusDays(1).atTime(10, 0);
        LocalDateTime end = start.plusHours(1);

        Map<String, Integer> outcome = Concurrency.race(2, i -> i == 0
                ? createActivity(creatorId, List.of(secondCourt, firstCourt), start, end)
                : createReservation(bookerId, secondCourt, start, end));

        int activityWon = outcome.getOrDefault("ACTIVITY_OK", 0);
        assertEquals(1, activityWon + outcome.getOrDefault("RESERVATION_OK", 0), outcome.toString());
        assertEquals(1, outcome.getOrDefault("COURT_ALREADY_RESERVED", 0), outcome.toString());
        assertEquals(activityWon, fx.countActiveReservations(firstCourt, start, end),
                "活动失败时第一块场不得留下占用");
        assertEquals(1, fx.countActiveReservations(secondCourt, start, end));
        assertEquals(activityWon, jdbc.queryForObject(
                "SELECT COUNT(*) FROM activity WHERE creator_id = ?", Long.class, creatorId));
        assertEquals(2L * activityWon, jdbc.queryForObject(
                "SELECT COUNT(*) FROM reservation WHERE activity_id IN "
                        + "(SELECT id FROM activity WHERE creator_id = ?) AND status = 1",
                Long.class, creatorId));
        assertEquals(activityWon, jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE user_id = ? AND status = 1",
                Long.class, creatorId));
    }

    @Test
    @DisplayName("第二块场已有个人预约：活动失败后第一块场仍空闲")
    void conflictOnSecondCourtLeavesNoActivityResources() {
        long venueId = fx.venue("conflict-venue");
        long firstCourt = fx.court(venueId, "conflict-first", 1);
        long secondCourt = fx.court(venueId, "conflict-second", 1);
        long creatorId = fx.user("conflict-creator");
        long bookerId = fx.user("conflict-booker");
        LocalDateTime start = LocalDate.now().plusDays(1).atTime(10, 0);
        LocalDateTime end = start.plusHours(1);
        fx.personalReservation(secondCourt, bookerId, start, end, 1);

        BusinessException error = assertThrows(BusinessException.class,
                () -> activityService.create(creatorId,
                        activityRequest(List.of(firstCourt, secondCourt), start, end)));

        assertEquals("COURT_ALREADY_RESERVED", error.getResultCode().getCode());
        assertEquals(0, fx.countActiveReservations(firstCourt, start, end));
        assertEquals(1, fx.countActiveReservations(secondCourt, start, end));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM activity WHERE creator_id = ?", Long.class, creatorId));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE user_id = ? AND status = 1",
                Long.class, creatorId));
    }

    @Test
    @DisplayName("两个多球场活动只共享一块场：恰好一个完整创建，失败方不留资源")
    void overlappingMultiCourtActivitiesCommitOnlyOneCompleteActivity() throws Exception {
        long venueId = fx.venue("overlap-venue");
        long leftCourt = fx.court(venueId, "overlap-left", 1);
        long sharedCourt = fx.court(venueId, "overlap-shared", 1);
        long rightCourt = fx.court(venueId, "overlap-right", 1);
        long leftCreator = fx.user("overlap-left-creator");
        long rightCreator = fx.user("overlap-right-creator");
        LocalDateTime start = LocalDate.now().plusDays(1).atTime(10, 0);
        LocalDateTime end = start.plusHours(1);

        Map<String, Integer> outcome = Concurrency.race(2, i -> i == 0
                ? createActivity(leftCreator, List.of(sharedCourt, leftCourt), start, end)
                : createActivity(rightCreator, List.of(rightCourt, sharedCourt), start, end));

        assertEquals(1, outcome.getOrDefault("ACTIVITY_OK", 0), outcome.toString());
        assertEquals(1, outcome.getOrDefault("COURT_ALREADY_RESERVED", 0), outcome.toString());
        long leftCreated = jdbc.queryForObject(
                "SELECT COUNT(*) FROM activity WHERE creator_id = ?", Long.class, leftCreator);
        long rightCreated = jdbc.queryForObject(
                "SELECT COUNT(*) FROM activity WHERE creator_id = ?", Long.class, rightCreator);
        assertEquals(1L, leftCreated + rightCreated, "失败方不能留下活动主体");
        assertEquals(leftCreated, fx.countActiveReservations(leftCourt, start, end));
        assertEquals(1, fx.countActiveReservations(sharedCourt, start, end));
        assertEquals(rightCreated, fx.countActiveReservations(rightCourt, start, end));
        assertEquals(2L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM reservation WHERE activity_id IN "
                        + "(SELECT id FROM activity WHERE creator_id IN (?, ?)) AND status = 1",
                Long.class, leftCreator, rightCreator));
        assertEquals(leftCreated + rightCreated, jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE user_id IN (?, ?) AND status = 1",
                Long.class, leftCreator, rightCreator));
    }

    private String createActivity(long creatorId, long courtId, LocalDateTime start, LocalDateTime end) {
        return createActivity(creatorId, List.of(courtId), start, end);
    }

    private String createActivity(long creatorId, List<Long> courtIds, LocalDateTime start, LocalDateTime end) {
        try {
            activityService.create(creatorId, activityRequest(courtIds, start, end));
            return "ACTIVITY_OK";
        } catch (BusinessException ex) {
            return ex.getResultCode().getCode();
        } catch (Exception ex) {
            return "UNEXPECTED:" + ex.getClass().getSimpleName();
        }
    }

    private CreateActivityRequest activityRequest(List<Long> courtIds, LocalDateTime start, LocalDateTime end) {
        CreateActivityRequest req = new CreateActivityRequest();
        req.setTitle(Fixtures.MARK + "mixed-race");
        req.setStartTime(start);
        req.setEndTime(end);
        req.setCourtIds(courtIds);
        req.setMaxMembers(2);
        return req;
    }

    private String createReservation(long bookerId, long courtId, LocalDateTime start, LocalDateTime end) {
        CreateReservationRequest req = new CreateReservationRequest();
        req.setCourtId(courtId);
        req.setStartTime(start);
        req.setEndTime(end);
        try {
            reservationService.create(bookerId, req);
            return "RESERVATION_OK";
        } catch (BusinessException ex) {
            return ex.getResultCode().getCode();
        } catch (Exception ex) {
            return "UNEXPECTED:" + ex.getClass().getSimpleName();
        }
    }
}
