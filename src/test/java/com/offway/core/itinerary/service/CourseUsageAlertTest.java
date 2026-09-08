package com.offway.core.itinerary.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.RequestUsage;
import com.offway.core.common.notification.Notifier;
import com.offway.core.itinerary.domain.Density;
import com.offway.core.leave.domain.StartDayLeave;
import com.offway.core.itinerary.service.dto.GenerateCourse;
import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.transport.domain.TransportMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 코스 한 건이 태운 호출을 알린다(#421).
 *
 * <p><b>관측이 기능을 막으면 안 된다</b> 는 것이 여기서 지키는 핵심이다 — 알림이 터져도 코스는 나가야 한다.
 */
class CourseUsageAlertTest {

    private static final UUID USER = UUID.fromString("a1b2c3d4-5e6f-7890-abcd-ef1234567890");

    /** 무엇을 보냈는지 기억하는 외부 경계 stub. */
    private static final class RecordingNotifier implements Notifier {

        private final List<String> sent = new ArrayList<>();

        @Override
        public void send(String message) {
            sent.add(message);
        }
    }

    /** 터지는 stub — 알림 실패가 코스 생성을 막는지 본다. */
    private static final class FailingNotifier implements Notifier {

        @Override
        public void send(String message) {
            throw new IllegalStateException("디스코드가 죽었다");
        }
    }

    /** 지역 하나만 아는 저장소 — 알림에 지역명이 실리는지만 본다. */
    private static RegionRepository regions(Region region) {
        return new RegionRepository() {
            @Override
            public long count() {
                return region == null ? 0 : 1;
            }

            @Override
            public List<Region> findAll() {
                return region == null ? List.of() : List.of(region);
            }

            @Override
            public List<Region> findByIds(List<Long> ids) {
                return region == null ? List.of() : List.of(region);
            }
        };
    }

    private static GenerateCourse command() {
        // 이름을 붙여 조립한다 — 필드가 늘 때마다 위치를 세지 않게(#453 에서 transitMode 가 늘었다).
        return GenerateCourse.builder()
                .regionId(16L)
                .travelDays(3)
                .density(Density.PACKED)
                .transport(TransportMode.TRANSIT)
                .originLat(37.5547)
                .originLng(126.9707)
                .travelDate(LocalDate.of(2026, 9, 11))
                .startDayLeave(StartDayLeave.FULL_DAY)
                .seed(1L)
                .excludePoiContentIds(Set.of())
                .build();
    }

    private static RequestUsage usage() {
        RequestUsage usage = new RequestUsage();
        for (int i = 0; i < 12; i++) {
            usage.record(ExternalApi.TOUR_API);
        }
        for (int i = 0; i < 4; i++) {
            usage.record(ExternalApi.TMAP_ROUTE);
        }
        return usage;
    }

    @Test
    void 코스와_사용자와_건수를_싣는다() {
        RecordingNotifier notifier = new RecordingNotifier();
        CourseUsageAlert alert = new CourseUsageAlert(notifier, regions(null));

        alert.send(USER, command(), usage(), true, false);

        String message = notifier.sent.get(0);
        assertTrue(message.contains("코스 생성"), message);
        assertTrue(message.contains("3일"), message);
        assertTrue(message.contains("대중교통"), message);
        // 전문으로 싣는다 — 복붙으로 바로 조회되는 것이 이 값의 쓸모다.
        assertTrue(message.contains(USER.toString()), message);
        assertTrue(message.contains("외부 호출 16건"), message);
        assertTrue(message.contains("국문관광정보 12"), message);
        assertTrue(message.contains("TMAP 경로 4"), message);
    }

    /**
     * <b>실패해도 보낸다.</b> 한도는 이미 깎였고, 오히려 "쓰고도 결과가 없는" 쪽이 더 봐야 하는 숫자다.
     */
    @Test
    void 실패한_생성도_알린다() {
        RecordingNotifier notifier = new RecordingNotifier();
        CourseUsageAlert alert = new CourseUsageAlert(notifier, regions(null));

        alert.send(USER, command(), usage(), false, false);

        assertTrue(notifier.sent.get(0).contains("실패"), notifier.sent.get(0));
    }

    @Test
    void 재생성은_따로_표시한다() {
        RecordingNotifier notifier = new RecordingNotifier();
        CourseUsageAlert alert = new CourseUsageAlert(notifier, regions(null));

        alert.send(USER, command(), usage(), true, true);

        assertTrue(notifier.sent.get(0).contains("재생성"), notifier.sent.get(0));
    }

    /** 0건도 정상이다 — 전부 캐시·DB 로 끝난 요청이고, 그게 보이는 것이 이 알림의 쓸모 중 하나다. */
    @Test
    void 한_건도_안_나갔으면_내역_없이_0건이다() {
        RecordingNotifier notifier = new RecordingNotifier();
        CourseUsageAlert alert = new CourseUsageAlert(notifier, regions(null));

        alert.send(USER, command(), new RequestUsage(), true, false);

        String message = notifier.sent.get(0);
        assertTrue(message.contains("외부 호출 0건"), message);
        assertTrue(message.endsWith("0건"), "0건이면 내역 줄을 안 붙인다: " + message);
    }

    /**
     * <b>관측이 기능을 막으면 안 된다.</b> 디스코드가 죽어도 코스는 나가야 한다.
     */
    @Test
    void 알림이_터져도_밖으로_던지지_않는다() {
        CourseUsageAlert alert = new CourseUsageAlert(new FailingNotifier(), regions(null));

        alert.send(USER, command(), usage(), true, false);
        // 예외가 안 나면 통과다 — 여기까지 왔다는 것이 곧 단언이다.
        assertEquals(1, 1);
    }
}
