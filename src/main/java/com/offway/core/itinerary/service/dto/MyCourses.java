package com.offway.core.itinerary.service.dto;

import com.offway.core.common.response.PageResponse;
import com.offway.core.itinerary.domain.Course;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;

/**
 * "내 코스" 목록 조회 결과 — 코스들과, 화면이 함께 보여줘야 하는 부가 상태.
 *
 * <p>차감 여부·D-day 는 코스의 상태가 아니라 <b>조회 시점의 상태</b>다. 연차 내역과 오늘 날짜에 의존하므로 도메인
 * 객체에 심지 않고 여기서 함께 내린다({@code GeneratedCourse} 가 혜택·날씨를 다루는 방식과 같다).
 *
 * @param courses 조회된 코스 (범위에 따라 정렬됨)
 * @param deductedCourseIds 연차를 차감한 코스 ID 들
 * @param regionNames 코스가 속한 지역명(코스 ID 가 아니라 지역 ID 로 색인) — 목록 카드가 숫자 대신 이름을 쓴다(#171)
 * @param shareTokens 코스 ID → 공유 토큰(#259). 링크가 없는 코스는 키가 없다
 * @param today D-day 계산 기준일
 * @param page 0부터 시작하는 페이지 번호
 * @param size 페이지 크기
 * @param totalElements 범위에 맞는 전체 건수
 * @param totalPages 전체 페이지 수
 */
public record MyCourses(
        List<Course> courses,
        Set<Long> deductedCourseIds,
        Map<Long, String> regionNames,
        Map<Long, String> shareTokens,
        LocalDate today,
        int page,
        int size,
        long totalElements,
        int totalPages) implements PageResponse.Paged {

    public MyCourses {
        courses = List.copyOf(courses);
        deductedCourseIds = Set.copyOf(deductedCourseIds);
        regionNames = Map.copyOf(regionNames);
        shareTokens = Map.copyOf(shareTokens);
    }

    /** 한 페이지 — 목록 화면이 쓰는 길(#105). */
    public static MyCourses from(
            Page<Course> page,
            Set<Long> deductedCourseIds,
            Map<Long, String> regionNames,
            Map<Long, String> shareTokens,
            LocalDate today) {
        return new MyCourses(
                page.getContent(),
                deductedCourseIds,
                regionNames,
                shareTokens,
                today,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    /**
     * 페이지 없이 전부 — 서버 안에서 <b>더 걸러 쓰는</b> 호출자용(다녀온 여행 확인 등).
     *
     * <p>화면에 그대로 내리는 길이 아니다. 응답으로 나가는 목록은 {@link #from} 을 쓴다.
     *
     * <p>공유 토큰은 비운다 — 응답으로 나가지 않는 길이라 읽어봐야 쿼리만 하나 더 는다.
     */
    public static MyCourses all(
            List<Course> courses, Set<Long> deductedCourseIds, Map<Long, String> regionNames, LocalDate today) {
        return new MyCourses(
                courses, deductedCourseIds, regionNames, Map.of(), today, 0, courses.size(), courses.size(),
                courses.isEmpty() ? 0 : 1);
    }

    /** 코스가 속한 지역명. 모르면 null — 화면이 지역 칸을 비운다. */
    public String regionName(Course course) {
        return regionNames.get(course.getRegionId());
    }

    /**
     * 코스의 공유 토큰. 아직 발급된 적 없으면 null — 상세를 한 번 열면 그때 발급된다(#259).
     *
     * <p>목록에서는 발급하지 않는다. 페이지당 최대 100건이라 조회 한 번이 그만큼의 INSERT 가 된다.
     */
    public String shareToken(Course course) {
        return shareTokens.get(course.getId());
    }

    public boolean isDeducted(Course course) {
        return deductedCourseIds.contains(course.getId());
    }

    /**
     * 오늘 기준 남은 날 — 오늘이면 0, 미래면 양수, 지난 여행이면 음수.
     *
     * <p>여행 날짜가 없는 코스는 {@code null}. 0 으로 내리면 화면이 "오늘 출발" 로 읽는다.
     */
    public Integer dDay(Course course) {
        if (course.getTravelDate() == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(today, course.getTravelDate());
    }
}
