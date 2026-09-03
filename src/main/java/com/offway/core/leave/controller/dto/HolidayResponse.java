package com.offway.core.leave.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import com.offway.core.common.response.Attributed;
import com.offway.core.common.response.DataSource;
import java.util.List;
import java.util.Set;

/**
 * 한 해의 공휴일 목록(#317).
 *
 * <p><b>연도를 함께 싣는다.</b> 날짜 배열만 내리면 앱이 "무엇을 물어서 받은 답인지" 를 요청 쪽 상태로만
 * 기억해야 한다. 캐시에 담아 두고 나중에 꺼내 쓸 때 그 짝이 어긋나면 다른 해의 공휴일로 계산하게 된다.
 *
 * <p><b>빈 배열은 "공휴일이 없는 해" 라는 뜻이다.</b> 조회에 실패했으면 이 응답이 아니라 502 가 나간다 —
 * 실패를 빈 목록으로 답하면 앱이 공휴일을 평일로 세어 연차를 과다 계산한다.
 *
 * @param year 조회한 연도
 * @param dates 그 해의 공휴일. <b>날짜 오름차순</b>이고 중복이 없다
 */
public record HolidayResponse(
        @Schema(example = "2026") int year,
        @Schema(example = "[\"2026-01-01\", \"2026-08-15\"]") List<LocalDate> dates) implements Attributed {

    /**
     * 공휴일은 한국천문연구원 특일정보에서 온다(#399).
     *
     * <p>그 해 공휴일이 하나도 안 실렸으면(조회 실패·범위 밖) 표기할 것도 없다.
     */
    @Override
    public Set<DataSource> sources() {
        return dates.isEmpty() ? Set.of() : Set.of(DataSource.KASI);
    }

    public static HolidayResponse of(int year, List<LocalDate> dates) {
        return new HolidayResponse(year, dates);
    }
}
