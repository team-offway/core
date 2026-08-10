package com.offway.core.leave.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 달치 공휴일의 <b>영속 형태</b>(#193 3단계).
 *
 * <p><b>왜 달 단위인가.</b> 공휴일이 아예 없는 달은 정상이고 흔하다(4·6·11월). 날짜만 행으로 쌓으면
 * "공휴일이 없는 달" 과 "아직 안 받아온 달" 이 똑같이 <b>행 없음</b>이 되어 구분되지 않는다. 뒤쪽을 앞쪽으로
 * 오인하면 공휴일이 평일로 세어져 소모 연차가 과다 계산된다 — 조용히 틀리는 쪽이라 예외보다 나쁘다.
 * 달을 행으로 두면 <b>행의 존재가 곧 "받아왔다"</b> 이고, 내용이 비면 "그 달은 공휴일이 없다" 다.
 */
@Entity
@Table(name = "holiday_month")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredHolidayMonth {

    /** 기준 연월 표기 — {@code hub_attraction.base_ym} 과 같은 형식. */
    private static final DateTimeFormatter BASE_YM_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    /** 날짜 목록의 저장 구분자. */
    private static final String HOLIDAY_DELIMITER = ",";

    /** {@code holidays} 컬럼 길이 — 마이그레이션과 같은 값이어야 한다. */
    private static final int MAX_HOLIDAYS_LENGTH = 400;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_ym", nullable = false, length = 6)
    private String baseYm;

    /** {@code yyyy-MM-dd} 를 쉼표로 이은 값 — 날짜 오름차순. 공휴일이 없는 달은 빈 문자열이다. */
    @Column(nullable = false, length = MAX_HOLIDAYS_LENGTH)
    private String holidays;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private StoredHolidayMonth(YearMonth month, Set<LocalDate> dates, LocalDateTime updatedAt) {
        Objects.requireNonNull(month, "기준 연월이 필요합니다");
        Objects.requireNonNull(dates, "공휴일 집합이 필요합니다");
        Objects.requireNonNull(updatedAt, "갱신 시각이 필요합니다");
        // 그 달에 속하지 않는 날짜가 섞이면 읽는 쪽이 달로 색인하는 순간 사라진다. 누가 만들든 여기서 막는다.
        dates.forEach(date -> {
            if (!YearMonth.from(date).equals(month)) {
                throw new IllegalArgumentException("기준 연월 밖의 날짜입니다: month=" + month + " date=" + date);
            }
        });
        String joined = dates.stream().sorted().map(LocalDate::toString).collect(Collectors.joining(HOLIDAY_DELIMITER));
        if (joined.length() > MAX_HOLIDAYS_LENGTH) {
            throw new IllegalArgumentException("한 달 공휴일이 저장 한도를 넘습니다: " + joined.length());
        }
        this.baseYm = baseYmOf(month);
        this.holidays = joined;
        this.updatedAt = updatedAt;
    }

    /** 입력에서 저장 표현이 계산되므로 빌더가 아니라 팩토리다(조립이면 빌더, 계산이면 팩토리). */
    public static StoredHolidayMonth of(YearMonth month, Set<LocalDate> dates, LocalDateTime updatedAt) {
        return new StoredHolidayMonth(month, dates, updatedAt);
    }

    /** 조회 키 변환 — 저장 표기를 아는 곳을 이 클래스 하나로 묶는다. */
    public static String baseYmOf(YearMonth month) {
        return month.format(BASE_YM_FORMAT);
    }

    public YearMonth month() {
        return YearMonth.parse(baseYm, BASE_YM_FORMAT);
    }

    /** 그 달의 공휴일. 공휴일이 없는 달이면 빈 집합이다(조회 실패와 구분된다). */
    public Set<LocalDate> holidays() {
        if (holidays.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(holidays.split(HOLIDAY_DELIMITER))
                .map(LocalDate::parse)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 그 날짜에 갱신됐는가 — 같은 날 재배포가 외부를 다시 부르지 않게 하는 판단에 쓴다. */
    public boolean refreshedOn(LocalDate date) {
        return updatedAt.toLocalDate().equals(date);
    }
}
