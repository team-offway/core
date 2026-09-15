package com.offway.core.liveactivity.service;

import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.liveactivity.domain.LiveActivityException;
import com.offway.core.liveactivity.domain.LiveActivityToken;
import com.offway.core.liveactivity.repository.LiveActivityTokenRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 잠금화면 카드의 갱신 주소를 받아 둔다(#575).
 *
 * <p>여기서 푸시를 보내지 않는다 — 받을 주소를 보관하는 데까지다. 실제 갱신은
 * {@link LiveActivityRefresher} 가 자정에 한다.
 *
 * <p><b>로그에 토큰을 남기지 않는다.</b> 이 값을 아는 쪽은 그 사람의 잠금화면에 내용을 그릴 수 있어
 * 비밀값에 준한다(로깅 규약). 코스 id 만 남긴다.
 *
 * <p>외부 호출이 없어 트랜잭션이 짧다 — 전부 DB 만 만진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveActivityService {

    /** 등록·갱신 시각 기준 시간대. 서비스가 한국 여행을 다루므로 사용자 로캘과 무관하게 KST 다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final LiveActivityTokenRepository liveActivityTokenRepository;
    private final CourseRepository courseRepository;

    /**
     * 토큰을 등록하거나 이미 있으면 갈아 끼운다.
     *
     * <p><b>주인을 먼저 확인한다.</b> 이 등록의 대가로 서버가 그 코스의 여행지·날짜를 매일 잠금화면에
     * 그려 준다 — 확인을 빠뜨리면 남의 코스 id 를 적는 것만으로 그 사람의 여행 일정을 받아 볼 수 있다.
     * 소유는 요청 본문이 아니라 access 토큰이 정한다(#280).
     *
     * <p><b>있는지 먼저 보지 않는다.</b> 조회 후 분기하면 같은 카드가 두 번 동시에 등록할 때 둘 다
     * "없다" 를 읽고 하나가 유니크 제약에 걸린다. 판정은 제약을 쥔 DB 가 한 문장 안에서 한다.
     */
    @Transactional
    public void register(UUID userId, Long courseId, String token) {
        LiveActivityToken registration =
                LiveActivityToken.register(userId, courseId, token, LocalDateTime.now(SERVICE_ZONE));
        requireOwnCourse(userId, registration.getCourseId());
        liveActivityTokenRepository.register(registration);
        log.info("잠금화면 등록 courseId={}", registration.getCourseId());
    }

    /**
     * 이 코스의 등록을 지운다 — 앱이 카드를 내렸을 때.
     *
     * <p><b>지울 것이 없어도 성공이다.</b> 원한 상태("이 카드에 갱신이 가지 않는다")가 이미 이뤄져
     * 있고, 앱이 재시도했을 때 404 를 띄울 이유가 없다.
     *
     * <p>주인을 확인하지 않는다 — 지우는 범위가 <b>이 사람의</b> 행으로 이미 좁혀져 있어, 남의 코스
     * id 를 적어도 지워지는 것이 없다.
     */
    @Transactional
    public void unregister(UUID userId, Long courseId) {
        int deleted = liveActivityTokenRepository.deleteByUserAndCourse(
                userId, LiveActivityToken.requireCourseId(courseId));
        log.info("잠금화면 해제 courseId={} deleted={}", courseId, deleted);
    }

    private void requireOwnCourse(UUID userId, Long courseId) {
        if (courseRepository.findByIdAndUserId(courseId, userId).isEmpty()) {
            throw LiveActivityException.courseNotFound();
        }
    }
}
