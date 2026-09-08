package com.offway.core.inventory.service;

import com.offway.core.inventory.infrastructure.probe.ExternalApiProbe;
import com.offway.core.inventory.infrastructure.probe.ProbeResult;
import com.offway.core.inventory.service.dto.InventoryRow;
import com.offway.core.inventory.service.dto.InventorySnapshot;
import com.offway.core.region.service.RegionQuery;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 지금 가져올 수 있는 데이터 소스 현황을 표 형태로 취합한다(ops 헬스체크). */
@Slf4j
@Service
public class InventoryService {

    private static final List<String> ORDER = List.of(
            "특일정보(공휴일)",
            "국문관광정보(TourAPI)",
            "관광빅데이터(방문자·집중률)",
            "코레일 열차운행정보",
            "TAGO 대중교통(버스)",
            "TMAP 경로");

    private final List<ExternalApiProbe> probes;
    private final RegionQuery regionQuery;

    public InventoryService(List<ExternalApiProbe> probes, RegionQuery regionQuery) {
        this.probes = probes;
        this.regionQuery = regionQuery;
    }

    public InventorySnapshot snapshot() {
        long regionCount = regionQuery.count();

        List<InventoryRow> rows = new ArrayList<>();
        rows.add(new InventoryRow(
                "인구감소지역", "행정안전부", "89개 시군구 마스터 (추천 기준)",
                "보유", "ok", regionCount + "개 DB 시딩 완료"));

        probes.stream()
                .map(this::runSafely)
                .map(this::toRow)
                .sorted((a, b) -> Integer.compare(orderOf(a.name()), orderOf(b.name())))
                .forEach(rows::add);

        return new InventorySnapshot(regionCount, rows);
    }

    private ProbeResult runSafely(ExternalApiProbe probe) {
        try {
            ProbeResult r = probe.probe();
            log.debug("[inventory] {} · {} (status={})", r.name(), r.status(), r.httpStatus());
            return r;
        } catch (Exception e) {
            log.warn("[inventory] 프로브 실패: {}", e.toString());
            return ProbeResult.fail(probe.getClass().getSimpleName(), "-", 0, e.toString(), "");
        }
    }

    private InventoryRow toRow(ProbeResult r) {
        String label;
        String tone;
        switch (r.status()) {
            case OK -> {
                label = "조회 성공";
                tone = "ok";
            }
            case UNVERIFIED -> {
                label = "승인됨 · 연동 예정";
                tone = "info";
            }
            case SKIPPED_NO_KEY -> {
                label = "키 없음";
                tone = "muted";
            }
            case FAIL -> {
                if (r.httpStatus() == 403) {
                    label = "승인 반영 대기";
                    tone = "warn";
                } else {
                    label = "실패";
                    tone = "bad";
                }
            }
            default -> {
                label = r.status().name();
                tone = "muted";
            }
        }
        return new InventoryRow(r.name(), r.provider(), provides(r.name()), label, tone, r.detail());
    }

    private int orderOf(String name) {
        int i = ORDER.indexOf(name);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    private String provides(String name) {
        return switch (name) {
            case "특일정보(공휴일)" -> "공휴일·대체공휴일 (가용시간·샌드위치 계산)";
            case "국문관광정보(TourAPI)" -> "관광지·숙박·음식점·운영시간·좌표";
            case "관광빅데이터(방문자·집중률)" -> "지역별 방문자수·집중률·향후 예측";
            case "코레일 열차운행정보" -> "KTX 등 여객열차 운행계획·운행정보";
            case "TAGO 대중교통(버스)" -> "버스 도착·노선·위치, 지하철 정보";
            case "TMAP 경로" -> "자동차 경로·소요시간·경유지 최적화";
            default -> "";
        };
    }
}
