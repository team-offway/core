package com.offway.core.itinerary.service.dto;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.policy.domain.PolicyType;
import com.offway.core.transport.service.dto.TrainAccess;
import com.offway.core.weather.domain.DailyWeather;
import java.util.List;
import java.util.Map;

/**
 * 코스 생성 결과 — 조립된 코스({@link Course} 도메인)와 응답 시점에 매칭한 혜택·날씨·열차 접근. 이들은 정책·기상·교통 상태에 의존해
 * 도메인 상태로 두지 않고 여기서 함께 내린다.
 *
 * @param course 날짜별 타임라인 코스
 * @param benefits 이 코스(지역·기간)에 적용되는 혜택 뱃지
 * @param weatherByDay Day 번호(1부터) → 그날의 날씨. 예보가 없는 Day 는 <b>키가 없다</b>(#141)
 * @param trainAccess 대중교통 코스일 때 출발지→지역 열차 접근(자차·저장 코스는 null)
 * @param regionName 코스 지역의 짧은 이름(예: 정선군) — 슬롯마다 "관광명소 · 정선군" 으로 붙는다(#141)
 *     이 순간의 측정치라 다음 주 코스에 붙이면 여행일 상태로 오해된다. 그 밖에는 null
 * @param shareToken 공유 링크 토큰(#143). <b>소유자에게만</b> 준다 — 저장 응답에만 채우고, 공유 링크로 여는
 *     공개 조회에는 null 이다. 링크를 받은 사람에게 토큰을 되돌려줄 이유가 없다
 * @param firstDayChange 날짜 수정으로 <b>첫날 판단이 뒤집혔을 때만</b> 실린다(#214). 그 밖에는 null
 */
public record GeneratedCourse(
        Course course,
        List<Benefit> benefits,
        Map<Integer, DailyWeather> weatherByDay,
        TrainAccess trainAccess,
        String regionName,
        String shareToken,
        FirstDayChange firstDayChange) {

    public GeneratedCourse {
        benefits = List.copyOf(benefits);
        weatherByDay = weatherByDay == null ? Map.of() : Map.copyOf(weatherByDay);
    }

    /** 날씨·열차 접근·대기질 없이(목록 조회 등). 지역명은 슬롯 표시에 쓰이므로 저장 코스도 채운다. */
    public static GeneratedCourse of(Course course, List<Benefit> benefits, String regionName) {
        return new GeneratedCourse(course, benefits, Map.of(), null, regionName, null, null);
    }

    /** 조립이 끝난 뒤 첫날 변화만 얹는다 — 날짜 수정 경로에서만 붙는다(#214). */
    public GeneratedCourse withFirstDayChange(FirstDayChange firstDayChange) {
        return new GeneratedCourse(
                course, benefits, weatherByDay, trainAccess, regionName, shareToken, firstDayChange);
    }

    /** 조립이 끝난 뒤 공유 토큰만 얹는다 — 토큰은 영속 경계에서 오므로 조립 시점에 알 수 없다. */
    public GeneratedCourse withShareToken(String shareToken) {
        return new GeneratedCourse(
                course, benefits, weatherByDay, trainAccess, regionName, shareToken, firstDayChange);
    }

    /**
     * 여행 날짜를 옮겼을 때 첫날에 생긴 변화(#214).
     *
     * <p>열차 도착 시각은 새 날짜로 다시 조회하는데 일정 판단은 저장된 옛것이라, 둘이 어긋날 수 있다.
     *
     * <ul>
     *   <li>{@code TRIMMED} — 도착이 늦어져 <b>갈 수 없게 된 슬롯을 걷어냈다</b>. 서버가 이미 고쳤다.
     *   <li>{@code FILLABLE} — 도착이 빨라져 첫날을 쓸 수 있게 됐다. 채울 후보가 저장 코스에 없어
     *       서버가 못 고친다 — 사용자가 재생성을 고를 수 있게 알린다.
     * </ul>
     */
    public enum FirstDayChange {
        TRIMMED,
        FILLABLE
    }

    /**
     * @param policyId 정책 ID
     * @param type 정책 분류
     * @param text 뱃지 문구
     */
    public record Benefit(long policyId, PolicyType type, String text) {
    }
}
