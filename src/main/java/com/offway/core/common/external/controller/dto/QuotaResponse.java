package com.offway.core.common.external.controller.dto;

import com.offway.core.common.external.ExternalApi;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Map;

/**
 * 외부 API 오늘자 한도 현황(#123).
 *
 * @param date 기준 날짜(KST). 자정을 넘기면 새 날짜로 리셋된다
 * @param apis API 별 한도·사용량·잔여
 */
public record QuotaResponse(
        @Schema(example = "2026-08-11") LocalDate date,
        java.util.List<Item> apis) {

    /**
     * @param api enum 이름 — 클라이언트가 분기에 쓸 안정된 키
     * @param label 사람이 읽는 이름
     * @param limit 하루 한도
     * @param usedToday 오늘 쓴 수
     * @param remaining 남은 수(한도를 넘겼으면 0)
     */
    public record Item(
            @Schema(example = "TOUR_API") String api,
            @Schema(example = "국문관광정보") String label,
            @Schema(example = "1000") int limit,
            @Schema(example = "542") long usedToday,
            @Schema(example = "458") int remaining) {
    }

    /** 한 번도 안 부른 API 도 0 으로 함께 낸다 — 빠져 있으면 "안 센 것" 과 구분이 안 된다. */
    public static QuotaResponse of(LocalDate date, Map<ExternalApi, Long> usage) {
        return new QuotaResponse(date, java.util.Arrays.stream(ExternalApi.values())
                .map(api -> {
                    long used = usage.getOrDefault(api, 0L);
                    return new Item(api.name(), api.label(), api.dailyLimit(), used, api.remainingAfter(used));
                })
                .toList());
    }
}
