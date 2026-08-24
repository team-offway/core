package com.offway.core.leave.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

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
        @Schema(example = "[\"2026-01-01\", \"2026-08-15\"]") List<LocalDate> dates) {

    public static HolidayResponse of(int year, List<LocalDate> dates) {
        return new HolidayResponse(year, dates);
    }
}
