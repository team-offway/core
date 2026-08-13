package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.CourseShare;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 코스 공유 링크 영속 port(#143). 구현은 {@link CourseShareRepositoryImpl}. */
public interface CourseShareRepository {

    CourseShare save(CourseShare courseShare);

    /** 링크를 연 사람이 쓰는 경로 — 토큰만으로 찾는다(소유자 정보 없음). */
    Optional<CourseShare> findByShareToken(String shareToken);

    /** 코스 하나에 링크 하나 — 이미 발급했으면 그것을 다시 준다. */
    Optional<CourseShare> findByCourseId(Long courseId);

    /**
     * 여러 코스의 링크를 <b>한 번에</b>(#259) — 목록 응답이 쓴다.
     *
     * <p>코스마다 {@link #findByCourseId} 를 부르면 한 페이지에 쿼리가 코스 수만큼 늘어난다(N+1).
     * 목록은 페이지당 최대 100건이라 그대로 두면 요청 하나가 쿼리 백 번이 된다.
     *
     * @return 링크가 있는 코스의 것만. 발급된 적 없는 코스는 결과에 없다
     */
    List<CourseShare> findByCourseIdIn(Collection<Long> courseIds);
}
