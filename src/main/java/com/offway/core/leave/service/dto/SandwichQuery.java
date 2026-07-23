package com.offway.core.leave.service.dto;

import com.offway.core.leave.domain.LeaveException;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 샌드위치 연휴 조회 커맨드 — 서비스 내부용.
 *
 * <p>조회 개월 수 계약(1~12)은 compact constructor 에서 모든 생성 경로에 강제한다. {@code remainingLeave} 는 선택 — 있으면 그
 * 이하 연차로 가능한 연휴만 남긴다.
 *
 * @param fromDate 조회 시작일
 * @param months 조회 개월 수 (1~12)
 * @param remainingLeave 남은 연차 (null 이면 필터 안 함)
 */
public record SandwichQuery(LocalDate fromDate, int months, Double remainingLeave) {

    private static final int MIN_MONTHS = 1;
    private static final int MAX_MONTHS = 12;

    /** 조회 개월 수(1~12) 계약을 모든 생성 경로에서 강제한다 — {@code new} 직접 생성도 우회 못 하게 compact constructor 에 둔다. */
    public SandwichQuery {
        Objects.requireNonNull(fromDate, "fromDate 는 null 일 수 없습니다.");
        if (months < MIN_MONTHS || months > MAX_MONTHS) {
            throw LeaveException.invalidLookupMonths();
        }
    }

    /** 조회 종료일(포함). {@code fromDate} 부터 {@code months} 개월. */
    public LocalDate toDate() {
        return fromDate.plusMonths(months).minusDays(1);
    }

    /** 남은 연차 안에서 쓸 수 있는 연휴인가. 남은 연차 미지정이면 항상 참. */
    public boolean withinRemainingLeave(int leaveDays) {
        return remainingLeave == null || leaveDays <= remainingLeave;
    }
}
