package com.offway.core.common.external.controller.dto;

import com.offway.core.common.external.Caller;
import com.offway.core.common.external.DataFlow;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 외부 API 연동 현황(#398) — 백오피스가 한 화면에 그리는 값 전부.
 *
 * @param from 조회 시작일(포함)
 * @param to 조회 종료일(포함)
 * @param days 실제 조회한 일수. 요청값이 상한에 잘렸을 수 있어 되돌려준다
 * @param apis 연동별 현황. 한 번도 안 부른 API 도 <b>0 으로 나온다</b> — 안 쓰는 연동이 보여야 한다
 * @param daily 날짜별 총합. 월배치가 튀는 날이 여기서 보인다
 * @param batches 배치별 마지막 실행 시각
 */
public record ExternalApiStatusResponse(
        @Schema(example = "2026-08-20") LocalDate from,
        @Schema(example = "2026-09-02") LocalDate to,
        @Schema(example = "14") int days,
        List<Api> apis,
        List<Day> daily,
        List<Batch> batches) {

    /**
     * 연동 하나.
     *
     * @param batchTotal 기간 중 <b>배치</b>가 태운 수
     * @param requestTotal 기간 중 <b>사용자 요청</b>이 태운 수. 심사가 보는 것은 이쪽이다
     */
    public record Api(
            @Schema(example = "TOUR_API") String name,
            @Schema(example = "국문관광정보") String label,
            @Schema(example = "1000") int dailyLimit,
            @Schema(example = "412") long todayUsed,
            @Schema(example = "588") int todayRemaining,
            @Schema(example = "41") int todayUsedRate,
            @Schema(example = "5230") long periodTotal,
            @Schema(example = "4100") long batchTotal,
            @Schema(example = "1130") long requestTotal,
            List<CallerShare> callers,
            List<Flow> flows) {
    }

    /** 누가 태웠나. 많이 쓴 순으로 온다. */
    public record CallerShare(
            @Schema(example = "장소운영시간배치") String caller,
            @Schema(example = "2400") long count,
            @Schema(description = "사용자 요청이면 true, 배치면 false", example = "false") boolean fromRequest) {
    }

    /** 어느 화면이 이 API 를 어떻게 쓰나({@link DataFlow}). */
    public record Flow(
            @Schema(example = "코스 생성") String screen,
            @Schema(example = "POST /api/v1/courses/generate") String path,
            @Schema(example = "실호출") String mode,
            @Schema(example = "요청마다 외부를 부른다. 캐시가 없다") String modeDetail,
            @Schema(example = "슬롯 후보 조회. 관광타입 4종이라 코스 하나에 4콜") String note) {
    }

    /**
     * 하루치.
     *
     * @param counts API 이름 → 호출 수. <b>안 부른 API 는 키가 없다</b> — 0 을 채우면 "안 불렀다" 와
     *     "기록 자체가 없다" 가 구분되지 않는다
     */
    public record Day(
            @Schema(example = "2026-09-01") LocalDate date,
            @Schema(example = "700") long total,
            Map<String, Long> counts) {
    }

    public record Batch(
            @Schema(example = "장소운영시간배치") String name,
            @Schema(example = "2026-09-02T04:30:00") LocalDateTime lastRunAt) {
    }

    public static ExternalApiStatusResponse from(ExternalApiSnapshot snapshot) {
        return new ExternalApiStatusResponse(
                snapshot.from(),
                snapshot.to(),
                (int) (snapshot.to().toEpochDay() - snapshot.from().toEpochDay() + 1),
                apis(snapshot),
                days(snapshot),
                snapshot.batches().stream()
                        .map(run -> new Batch(run.getName(), run.getLastRunAt()))
                        .toList());
    }

    /** 한도가 큰 순이 아니라 <b>오늘 많이 쓴 순</b>이다 — 위험한 것이 위에 와야 한다. */
    private static List<Api> apis(ExternalApiSnapshot snapshot) {
        return Arrays.stream(ExternalApi.values())
                .map(api -> toApi(api, snapshot))
                .sorted(Comparator.comparingLong(Api::todayUsed).reversed()
                        .thenComparing(Api::label))
                .toList();
    }

    private static Api toApi(ExternalApi api, ExternalApiSnapshot snapshot) {
        long todayUsed = snapshot.countOn(snapshot.to(), api);
        Map<String, Long> byCaller = snapshot.callers().getOrDefault(api, Map.of());

        long requestTotal = byCaller.entrySet().stream()
                .filter(entry -> Caller.of(entry.getKey()).fromRequest())
                .mapToLong(Map.Entry::getValue)
                .sum();
        long callerTotal = byCaller.values().stream().mapToLong(Long::longValue).sum();

        return new Api(
                api.name(),
                api.label(),
                api.dailyLimit(),
                todayUsed,
                api.remainingAfter(todayUsed),
                usedRate(todayUsed, api.dailyLimit()),
                snapshot.total(api),
                // 주체 기록이 총량보다 적을 수 있다(주체를 안 심은 경로). 뺄셈으로 내면 음수가 나오므로
                // 배치는 "주체가 있는 것 중 요청이 아닌 것" 으로만 센다.
                callerTotal - requestTotal,
                requestTotal,
                byCaller.entrySet().stream()
                        .map(entry -> new CallerShare(
                                entry.getKey(), entry.getValue(), Caller.of(entry.getKey()).fromRequest()))
                        .toList(),
                DataFlow.using(api).stream()
                        .map(flow -> new Flow(
                                flow.screen().label(),
                                flow.screen().path(),
                                flow.mode().label(),
                                flow.mode().detail(),
                                flow.note()))
                        .toList());
    }

    private static int usedRate(long used, int limit) {
        return limit <= 0 ? 0 : (int) Math.min(100, used * 100 / limit);
    }

    /** 기록이 없는 날도 <b>0 으로 채운다</b> — 빠진 날이 그래프에서 앞으로 당겨지면 추이가 거짓이 된다. */
    private static List<Day> days(ExternalApiSnapshot snapshot) {
        List<Day> days = new ArrayList<>();
        for (LocalDate date = snapshot.from(); !date.isAfter(snapshot.to()); date = date.plusDays(1)) {
            Map<ExternalApi, Long> counts = snapshot.daily().getOrDefault(date, Map.of());
            days.add(new Day(
                    date,
                    counts.values().stream().mapToLong(Long::longValue).sum(),
                    counts.entrySet().stream().collect(
                            java.util.stream.Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue))));
        }
        return days;
    }
}
