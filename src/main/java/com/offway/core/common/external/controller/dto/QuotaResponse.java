package com.offway.core.common.external.controller.dto;

import com.offway.core.common.external.ExternalApi;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 외부 API 오늘자 한도 현황(#123).
 *
 * @param date 기준 날짜(KST). 자정을 넘기면 새 날짜로 리셋된다
 * @param apis API 별 한도·사용량·잔여
 */
public record QuotaResponse(
        @Schema(example = "2026-08-11") LocalDate date,
        List<Item> apis) {

    /**
     * @param api enum 이름 — 클라이언트가 분기에 쓸 안정된 키
     * @param label 사람이 읽는 이름
     * @param limit 하루 한도
     * @param usedToday 오늘 쓴 수
     * @param remaining 남은 수(한도를 넘겼으면 0)
     * @param callers 누가 얼마나 썼나 — 많이 쓴 순(#285). 아직 안 부른 API 는 빈 목록
     */
    public record Item(
            @Schema(example = "TOUR_API") String api,
            @Schema(example = "국문관광정보") String label,
            @Schema(example = "1000") int limit,
            @Schema(example = "542") long usedToday,
            @Schema(example = "458") int remaining,
            List<CallerShare> callers) {
    }

    /**
     * 주체 하나의 몫(#285).
     *
     * <p>총량만으로는 "어디서 새나" 에 답할 수 없어 갈라 낸다. 배치 이름이거나 요청 엔드포인트다.
     *
     * @param caller 주체 이름
     * @param count 그 주체가 오늘 부른 수
     */
    public record CallerShare(
            @Schema(example = "중심관광지배치") String caller,
            @Schema(example = "89") long count) {
    }

    /** 한 번도 안 부른 API 도 0 으로 함께 낸다 — 빠져 있으면 "안 센 것" 과 구분이 안 된다. */
    public static QuotaResponse of(LocalDate date, Map<ExternalApi, Long> usage,
            Map<ExternalApi, Map<String, Long>> callerUsage) {
        return new QuotaResponse(date, Arrays.stream(ExternalApi.values())
                .map(api -> {
                    long used = usage.getOrDefault(api, 0L);
                    return new Item(api.name(), api.label(), api.dailyLimit(), used, api.remainingAfter(used),
                            sharesOf(callerUsage.get(api)));
                })
                .toList());
    }

    private static List<CallerShare> sharesOf(Map<String, Long> counts) {
        if (counts == null) {
            return List.of();
        }
        return counts.entrySet().stream()
                .map(entry -> new CallerShare(entry.getKey(), entry.getValue()))
                .sorted((left, right) -> {
                    int byCount = Long.compare(right.count(), left.count());
                    return byCount != 0 ? byCount : left.caller().compareTo(right.caller());
                })
                .toList();
    }
}
