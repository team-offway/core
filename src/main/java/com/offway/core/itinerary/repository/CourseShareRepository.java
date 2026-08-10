package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.CourseShare;
import java.util.Optional;

/** 코스 공유 링크 영속 port(#143). 구현은 {@link CourseShareRepositoryImpl}. */
public interface CourseShareRepository {

    CourseShare save(CourseShare courseShare);

    /** 링크를 연 사람이 쓰는 경로 — 토큰만으로 찾는다(소유자 정보 없음). */
    Optional<CourseShare> findByShareToken(String shareToken);

    /** 코스 하나에 링크 하나 — 이미 발급했으면 그것을 다시 준다. */
    Optional<CourseShare> findByCourseId(Long courseId);
}
