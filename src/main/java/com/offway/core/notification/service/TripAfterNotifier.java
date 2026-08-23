package com.offway.core.notification.service;

import com.offway.core.common.batch.repository.BatchRunRepository;
import com.offway.core.notification.service.dto.PushTarget;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 여행이 끝난 다음 날 "다녀오셨나요?" 를 알린다(#302) — 만들고, 보낸다.
 *
 * <p><b>모달만으로는 놓친다.</b> 그 질문은 홈·내 연차에 들어가는 순간에만 뜨고(#116), 딴 데를 누르면 그걸로
 * 끝이다. 알림함에 흔적이 없어 사용자는 물어본 적이 있었는지조차 모르고, 그 사이 연차는 안 깎인 채 남는다.
 * 여행이 쌓일수록 잔액이 실제와 벌어지는데 알아챌 경로가 없다.
 *
 * <p><b>전날 배치({@link TripTomorrowNotifier})와 빈을 나눠 둔다.</b> 같은 시각에 도는 다른 일이라 합칠 수도
 * 있지만, 한 메서드에 두면 앞의 것이 터질 때 뒤의 것이 통째로 안 돈다. 스케줄러가 따로 부르면 한쪽 실패가
 * 다른 쪽을 물지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripAfterNotifier {

    /**
     * 사용자가 이 알림을 받는 시각 — 전날 알림과 같은 저녁 8시다.
     *
     * <p>맞춘 이유는 <b>이 알림이 요구하는 행동이 저녁에 할 만한 것</b>이기 때문이다. 눌러 들어가면 연차를
     * 기록하는 화면이 뜨는데, 그건 여행에서 돌아와 정리하는 시간대에 맞는다. 새벽은 자는 사람을 깨우고,
     * 낮은 근무 중이다.
     */
    private static final String DAILY_AT_EVENING = "0 0 20 * * *";

    /** 서비스 기준 시간대. 여행 날짜는 한국 사용자의 달력 기준이라 서버 로케일에 맡기지 않는다. */
    private static final String SERVICE_ZONE_ID = "Asia/Seoul";

    private static final ZoneId SERVICE_ZONE = ZoneId.of(SERVICE_ZONE_ID);

    /**
     * 실행 기록의 배치 이름 — {@code batch_run} 의 키다.
     *
     * <p><b>건너뛰기 판정에 쓰지 않는다.</b> 다른 배치들은 이 기록으로 "오늘 이미 돌았나" 를 물어 외부 API
     * 한도를 아끼는데(#226), 여기는 외부를 부르지 않고 유니크 키 {@code (소유자, type, course_id)} 가
     * 이미 재실행을 막는다. 가드로 쓰면 오히려 배치가 반쯤 돌다 죽은 날 나머지 사람이 영영 못 받는다.
     */
    private static final String BATCH_NAME = "trip-after-notify";

    private final TripAfterNotificationCreator creator;
    private final PushDispatcher pushDispatcher;
    private final BatchRunRepository batchRunRepository;

    /**
     * 매일 저녁, 어제 여행이 끝났는데 아직 답하지 않은 사람에게 알림을 만들고 푸시로 보낸다.
     *
     * <p><b>돌았다는 사실을 남긴다.</b> 알림이 안 왔다는 제보를 받고도 "배치가 안 돈 것" 과 "돌았는데 대상이
     * 0건이었던 것" 을 가르지 못한 적이 있다(#309). 로그에는 남지만 재배포로 컨테이너가 바뀌면 사라져,
     * 며칠 지난 일은 답할 방법이 없었다. 실패해도 남긴다 — 안 남기면 터진 날이 안 돈 날과 구분되지 않는다.
     */
    @Scheduled(cron = DAILY_AT_EVENING, zone = SERVICE_ZONE_ID)
    public void notifyTripsEndedYesterday() {
        try {
            notifyTripsEndedYesterday(LocalDate.now(SERVICE_ZONE));
        } finally {
            batchRunRepository.markStarted(BATCH_NAME, LocalDateTime.now(SERVICE_ZONE));
        }
    }

    /**
     * 기준일 전날 끝난 여행에 알림을 만들고 보낸다.
     *
     * <p><b>새로 만들어진 것만 보낸다.</b> 이미 있던 알림까지 보내면 재실행 때마다 같은 푸시가 다시 간다.
     *
     * <p>발송이 실패해도 알림 자체는 남는다 — 앱을 열면 목록에서 보인다. 푸시는 "앱을 안 열고 있을 때도
     * 닿게" 하는 보강이지 알림의 유일한 통로가 아니다.
     *
     * @return 이 실행으로 새로 만들어진 알림 수
     */
    public int notifyTripsEndedYesterday(LocalDate today) {
        List<PushTarget> created = creator.createForTripsEndedYesterday(today);
        pushDispatcher.dispatch(created);
        return created.size();
    }
}
