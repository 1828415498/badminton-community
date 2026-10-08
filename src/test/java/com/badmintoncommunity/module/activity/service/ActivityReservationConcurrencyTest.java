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

@DisplayName("活动创建与个人预约同时抢场")
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

    private String createActivity(long creatorId, long courtId, LocalDateTime start, LocalDateTime end) {
        CreateActivityRequest req = new CreateActivityRequest();
        req.setTitle(Fixtures.MARK + "mixed-race");
        req.setStartTime(start);
        req.setEndTime(end);
        req.setCourtIds(List.of(courtId));
        req.setMaxMembers(2);
        try {
            activityService.create(creatorId, req);
            return "ACTIVITY_OK";
        } catch (BusinessException ex) {
            return ex.getResultCode().getCode();
        } catch (Exception ex) {
            return "UNEXPECTED:" + ex.getClass().getSimpleName();
        }
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
