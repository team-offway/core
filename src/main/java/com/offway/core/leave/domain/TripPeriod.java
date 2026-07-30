package com.offway.core.leave.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 확정된 여행 날짜 구간. 기간스타일 해석({@link PeriodStyle})과 날짜 직접선택이 <b>같은 타입으로 합류하는 지점</b>이라,
 * 그 뒤 계산({@link AvailableTime})은 어느 경로로 왔는지 몰라도 된다(결정 #38 — "명목값 vs 실제값" 분기를 없앤다).
 *
 * <p>여기서 던지는 예외는 <b>불변식</b>이다(500). 순서 역전·상한 초과는 요청 DTO 경계가 계약 검증(400)으로 이미 걸러야
 * 하고, 스타일 해석은 애초에 유효한 구간만 만든다. 그래도 검증을 두는 이유는 누가 만들든 스스로 유효함을 보장하는 최후의
 * 보루이기 때문이다.
 *
 * @param startDate 여행 시작일
 * @param endDate 여행 종료일 (시작일과 같거나 이후)
 */
public record TripPeriod(LocalDate startDate, LocalDate endDate) {

    public TripPeriod {
        Objects.requireNonNull(startDate, "startDate 는 null 일 수 없습니다.");
        Objects.requireNonNull(endDate, "endDate 는 null 일 수 없습니다.");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "종료일이 시작일보다 앞섭니다: start=%s end=%s".formatted(startDate, endDate));
        }
        // 상한 검사는 int 캐스팅 전에 long 으로 한다 — AvailableTime 과 같은 이유(랩어라운드 회피).
        long span = endDate.toEpochDay() - startDate.toEpochDay() + 1;
        if (span > AvailableTime.MAX_TRIP_DAYS) {
            throw new IllegalArgumentException(
                    "여행일수가 상한(%d)을 넘습니다: %d일".formatted(AvailableTime.MAX_TRIP_DAYS, span));
        }
    }

    /** 시작일부터 하루씩 세는 여행 일수. 당일치기는 1이다. */
    public int days() {
        return (int) (endDate.toEpochDay() - startDate.toEpochDay() + 1);
    }
}
