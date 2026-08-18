package com.offway.core.notification.service;

import com.offway.core.notification.service.dto.PushTarget;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 내일 떠나는 여행을 전날 알린다(#269) — 만들고, 보낸다(#270).
 *
 * <p>{@code #263} 이 알림을 보여주는 자리를 만들었지만 아무도 알림을 만들지 않아 모든 사용자의 목록이
 * 비어 있었다. 여기가 그 알림을 만들고, 앱을 열지 않은 사람에게도 닿게 푸시로 내보낸다.
 *
 * <p><b>두 단계를 나눠 부른다.</b> 생성은 트랜잭션 안(DB), 발송은 트랜잭션 밖(외부 호출)이라야 한다.
 * 한 메서드에 두면 발송하는 동안 DB 커넥션을 잡고 있게 되고, 같은 빈에서 트랜잭션 메서드를 직접
 * 호출하면 프록시를 우회해 트랜잭션이 아예 안 걸린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripTomorrowNotifier {

    /**
     * 사용자가 이 알림을 받는 시각. <b>이 값이 곧 사용자 경험이다</b> — "전날" 이 몇 시인지가 여기서 정해진다.
     *
     * <p>저녁 8시로 둔다. 짐을 싸는 시간대이면서, 자정 직전처럼 알림이 하루를 넘겨 "내일" 이 어긋날 위험이
     * 없다. 새벽에 보내면 자는 사람을 깨우고, 낮에 보내면 퇴근 전이라 할 수 있는 게 없다.
     */
    private static final String DAILY_AT_EVENING = "0 0 20 * * *";

    /** 서비스 기준 시간대. 여행 날짜는 한국 사용자의 달력 기준이라 서버 로케일에 맡기지 않는다. */
    private static final String SERVICE_ZONE_ID = "Asia/Seoul";

    private static final ZoneId SERVICE_ZONE = ZoneId.of(SERVICE_ZONE_ID);

    private final TripTomorrowNotificationCreator creator;
    private final PushDispatcher pushDispatcher;

    /** 매일 저녁, 내일 떠나는 코스의 주인에게 알림을 만들고 푸시로 보낸다. */
    @Scheduled(cron = DAILY_AT_EVENING, zone = SERVICE_ZONE_ID)
    public void notifyTripsStartingTomorrow() {
        notifyTripsStartingTomorrow(LocalDate.now(SERVICE_ZONE));
    }

    /**
     * 기준일 다음 날 떠나는 코스에 알림을 만들고 보낸다.
     *
     * <p><b>새로 만들어진 것만 보낸다.</b> 이미 있던 알림까지 보내면 재실행 때마다 같은 푸시가 다시 간다.
     *
     * <p>발송이 실패해도 알림 자체는 남는다 — 앱을 열면 목록에서 보인다. 푸시는 "앱을 안 열고 있을 때도
     * 닿게" 하는 보강이지 알림의 유일한 통로가 아니다.
     *
     * @return 이 실행으로 새로 만들어진 알림 수
     */
    public int notifyTripsStartingTomorrow(LocalDate today) {
        List<PushTarget> created = creator.createForTripsStartingTomorrow(today);
        pushDispatcher.dispatch(created);
        return created.size();
    }
}
