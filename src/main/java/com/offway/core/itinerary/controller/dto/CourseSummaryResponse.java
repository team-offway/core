package com.offway.core.itinerary.controller.dto;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.service.dto.MyCourses;
import com.offway.core.common.response.Attributed;
import com.offway.core.common.response.DataSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Set;
import java.util.List;

/**
 * 내 코스 목록 항목 — API 계약. 카드 리스트용 요약(상세는 {@code GET /courses/{id}}).
 *
 * @param courseId 코스 ID
 * @param regionId 지역 ID
 * @param travelDays 여행 일수
 * @param density 일정 밀도
 * @param placeCount 전체 장소 수
 */
public record CourseSummaryResponse(
        long courseId,
        long regionId,
        @Schema(description = "여행 시작일 (저장 시 넣지 않았으면 null)", example = "2026-08-14", nullable = true)
                LocalDate travelDate,
        @Schema(
                        description = "오늘 기준 남은 날. 오늘이면 0, 지난 여행이면 음수. 여행 날짜가 없으면 null",
                        example = "14",
                        nullable = true)
                Integer dDay,
        int travelDays,
        @Schema(example = "PACKED") String density,
        @Schema(description = "지역명 (모르면 null)", example = "정선군", nullable = true) String regionName,
        @Schema(
                        description = "카드 대표 이미지 — **그 지역의 대표 사진**(#313). 같은 지역 코스는 같은 사진이다. "
                                + "지역 카드(GET /regions)가 쓰는 그 사진이며, 못 고른 지역은 null",
                        example = "http://tong.visitkorea.or.kr/cms/resource/1.jpg",
                        nullable = true)
                String coverImageUrl,
        int placeCount,
        @Schema(description = "이 코스로 연차를 차감했는가", example = "true") boolean leaveDeducted,
        @Schema(
                        description = "공유 링크 토큰(#259). 공유 URL 은 /c/{shareToken}. "
                                + "아직 링크가 발급된 적 없는 코스는 null 이며, 상세를 한 번 열면 채워진다",
                        example = "a1B2c3D4e5F6g7H8i9J0kL",
                        nullable = true)
                String shareToken) implements Attributed {

    /**
     * 카드 대표 사진이 <b>그 지역의 대표 사진</b>이라 공사에서 온다(#399 · #313).
     *
     * <p>못 고른 지역은 null 이고, 그때는 이 응답에 공사 값이 없다 — 나머지(지역명 · 날짜 · 연차)는 우리 값이다.
     *
     * <p>이 화면은 {@code data} 가 <b>목록</b>이라 래퍼가 원소마다 물어 합친다. 한 장이라도 사진이 있으면
     * 표기가 붙는다.
     */
    @Override
    public Set<DataSource> sources() {
        return coverImageUrl == null ? Set.of() : Set.of(DataSource.KTO);
    }

    public static CourseSummaryResponse from(Course course, MyCourses myCourses) {
        return new CourseSummaryResponse(
                course.getId(),
                course.getRegionId(),
                course.getTravelDate(),
                myCourses.dDay(course),
                course.getTravelDays(),
                course.getDensity().name(),
                myCourses.regionName(course),
                myCourses.regionImage(course),
                course.totalSlots(),
                myCourses.isDeducted(course),
                myCourses.shareToken(course));
    }

    /** 목록 전체를 한 번에 — 차감 여부·D-day 는 코스마다 다시 묻지 않고 {@link MyCourses} 가 이미 들고 있다. */
    public static List<CourseSummaryResponse> listFrom(MyCourses myCourses) {
        return myCourses.courses().stream().map(course -> from(course, myCourses)).toList();
    }
}
