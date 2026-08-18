package com.offway.core.itinerary.event;

import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.itinerary.repository.TripOutcomeRepository;
import com.offway.core.user.event.UserWithdrawn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 탈퇴하면 그 사람의 코스·여행 후기를 지운다. {@code itinerary} 가 자기 테이블을 스스로 치운다.
 *
 * <p><b>공유 링크({@code course_share})는 지우지 않는다.</b> 코스가 사라지면 그 토큰은 자연히
 * 410(게시자가 삭제한 코스입니다)으로 답한다. 함께 지우면 404 가 되는데, 그건 "링크를 잘못 옮겨 적었다"
 * 와 구분되지 않아 링크를 받은 사람이 무슨 일이 일어났는지 알 수 없다. 남는 행에는 토큰·코스 id·발급
 * 시각뿐이라 탈퇴자의 개인정보가 남지도 않는다.
 *
 * <p><b>소유자 없는 코스는 여기서 못 지운다.</b> "담지 않고 공유만" 으로 만들어진 코스(#261)는
 * {@code guest_id} 가 비어 있어 어떤 소유자 조회로도 걸리지 않는다. 애초에 어떤 식별자와도 묶여 있지
 * 않아 탈퇴자에게 되짚을 수 없는 데이터이고, 정리는 발급 시각 기준 별도 작업의 몫이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoursePurgeOnUserWithdrawn {

    private final CourseRepository courseRepository;
    private final TripOutcomeRepository tripOutcomeRepository;

    @EventListener
    public void on(UserWithdrawn event) {
        event.guestIdIfPresent().ifPresentOrElse(this::purge, () -> logSkipped(event));
    }

    private void purge(String guestId) {
        int courses = courseRepository.deleteByGuestId(guestId);
        int outcomes = tripOutcomeRepository.deleteByGuestId(guestId);
        log.info("탈퇴 정리 — 코스 {}건 · 여행 후기 {}건 삭제", courses, outcomes);
    }

    /**
     * 지우지 못하고 넘어간 것을 반드시 남긴다. 이 경로가 조용하면 "탈퇴했는데 코스가 남아 있다" 를 아무도
     * 모른다 — 개인정보 삭제에서 조용한 실패는 그대로 사고다.
     */
    private void logSkipped(UserWithdrawn event) {
        log.warn("탈퇴 정리 건너뜀 — 게스트 식별자가 없어 코스·여행 후기에 닿지 못했다 userId={}", event.userId());
    }
}
