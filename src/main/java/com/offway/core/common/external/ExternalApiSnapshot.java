package com.offway.core.common.external;

import com.offway.core.common.batch.domain.BatchRun;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 외부 API 연동 현황 한 장(#398) — <b>서비스 내부용</b>이라 응답 모양을 모른다.
 *
 * <p>가공하지 않은 조회 결과를 그대로 든다. 화면이 읽을 모양으로 접는 것은 응답 DTO 의 일이다
 * (프로젝트 규약 — 매핑은 DTO 자신에).
 *
 * @param from 조회 시작일(포함)
 * @param to 조회 종료일(포함) — 보통 오늘
 * @param daily 날짜 → (API → 호출 수). 한 번도 안 부른 조합은 <b>키가 없다</b>
 * @param callers API → (주체 → 기간 합계). 배치와 사용자 요청을 가르는 축이다
 * @param batches 기록이 있는 배치 전부
 * @param settings 손댄 연동의 설정. 없는 키는 기본값이다
 * @param disabledBatches 지금 꺼 둔 배치 이름
 */
public record ExternalApiSnapshot(
        LocalDate from,
        LocalDate to,
        Map<LocalDate, Map<ExternalApi, Long>> daily,
        Map<ExternalApi, Map<String, Long>> callers,
        List<BatchRun> batches,
        Map<ExternalApi, ExternalApiSetting> settings,
        Set<String> disabledBatches) {

    /** 손대지 않은 연동은 기본값이다 — 이 기능이 붙기 전과 같은 동작. */
    public ExternalApiSetting settingOf(ExternalApi api) {
        return settings.getOrDefault(api, ExternalApiSetting.defaultFor(api));
    }

    /** 이 배치가 지금 돌게 돼 있나. */
    public boolean batchEnabled(String name) {
        return !disabledBatches.contains(name);
    }

    /** 그날 그 API 를 얼마나 썼나. 기록이 없으면 0 — 화면이 빈 칸을 다루지 않게 여기서 접는다. */
    public long countOn(LocalDate date, ExternalApi api) {
        return daily.getOrDefault(date, Map.of()).getOrDefault(api, 0L);
    }

    /** 기간 합계. */
    public long total(ExternalApi api) {
        return daily.values().stream()
                .mapToLong(byApi -> byApi.getOrDefault(api, 0L))
                .sum();
    }
}
