package com.offway.core.transport.domain;

import com.offway.core.common.geo.Coordinate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기차역 마스터 한 행 — TAGO 역코드·역명과 좌표(TMAP 지오코딩). 역 상세 API 가 없고 전국이 수백 개라 마스터를 우리가 소유(시드)한다.
 * 좌표는 폐역 등 일부 결측 가능 — 최근접 계산에서 제외한다.
 */
@Entity
@Table(name = "train_station")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** TAGO nodeid(예: NATH13421). 열차 조회의 출발/도착역 코드. */
    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    private Double lat;

    private Double lng;

    /**
     * 속한 시·도(#590) — 출발지 자동완성의 지역 검색이 보는 값.
     *
     * <p><b>이름으로는 안 된다.</b> 서울의 주요 역 대부분이 이름에 "서울" 을 담지 않는다 — 용산·청량리·
     * 영등포·왕십리·수서·광운대. 그래서 "서울" 을 쳤을 때 이름 매칭만으로는 서울역 하나만 뜬다.
     *
     * <p>좌표가 없으면 판정할 근거가 없어 결측이다. 그런 역은 출발지로 고를 수 없다.
     */
    @Column(length = 20)
    private String sido;

    /**
     * 간선 열차가 서는 역인가(#590) — 출발지로 고를 수 있는지를 가른다.
     *
     * <p><b>왜 필요한가.</b> 이 마스터는 TAGO 노드 목록 전체라 통근 전용 역이 섞여 있다. 서울만 봐도
     * 노량진·신도림·서빙고가 그렇다. 자동완성은 사용자가 <b>직접 고르는</b> 화면이라, 그런 역을 목록에
     * 두면 고른 사람이 열차 없는 코스를 받는다 — 우리가 고를 수 있게 해 놓고 degrade 시킨 것이다.
     * GPS 로 최근접을 자동 선택하던 때와 성질이 다르다.
     *
     * <p><b>목록으로는 못 갈라 실측했다.</b> 역마다 먼 허브로 편성을 물어 1,772콜을 썼다. 자세한 근거는
     * 마이그레이션 주석에 있다 — 특히 <b>1차 측정이 목포·전주·원주·정선역을 미운행으로 잘못 판정했고</b>
     * 2차 확인으로 살렸다는 것.
     *
     * <p><b>기본값은 {@code true} 다.</b> 새로 들어오는 역은 재기 전까지 쓸 수 있어야 한다 —
     * {@link BusTerminal#isTerminal()} 과 같은 판단이다.
     */
    @Column(nullable = false)
    private boolean intercity = true;

    private TrainStation(String code, String name, Double lat, Double lng, String sido) {
        // 역코드·역명은 누가 만들든 반드시 있어야 하는 불변식(좌표만 결측 허용) — DB flush 까지 미루지 않고 생성 시점에 막는다.
        this.code = Objects.requireNonNull(code, "역코드는 null 일 수 없습니다.");
        this.name = Objects.requireNonNull(name, "역명은 null 일 수 없습니다.");
        this.lat = lat;
        this.lng = lng;
        this.sido = sido;
    }

    /** 코드·역명·좌표로 만든다(시드 로딩·테스트용). 좌표는 결측 가능. */
    public static TrainStation of(String code, String name, Double lat, Double lng) {
        return new TrainStation(code, name, lat, lng, null);
    }

    /**
     * 시도까지 함께 만든다(#590).
     *
     * <p>운영에서는 마이그레이션이 시도를 채우므로 이 팩토리가 필요한 곳은 테스트다 — 시도가 없는 역은
     * 지역 검색에 걸리지 않아 제안 시나리오를 세울 수 없다.
     */
    public static TrainStation of(String code, String name, Double lat, Double lng, String sido) {
        return new TrainStation(code, name, lat, lng, sido);
    }

    /**
     * 간선 정차 여부까지 정해 만든다(#590) — 통근 전용 역 시나리오를 세우는 테스트용.
     *
     * <p>운영에서는 마이그레이션이 채우므로 코드가 이 값을 정하는 자리는 없다.
     */
    public static TrainStation of(
            String code, String name, Double lat, Double lng, String sido, boolean intercity) {
        TrainStation station = new TrainStation(code, name, lat, lng, sido);
        station.intercity = intercity;
        return station;
    }

    /** 좌표가 있어 최근접 계산에 쓸 수 있는가. */
    public boolean hasCoordinate() {
        return lat != null && lng != null;
    }

    /**
     * 출발지 제안 한 줄로 옮긴다 — 올릴 수 없으면 비어 있다(#590).
     *
     * <p>세 조건을 모두 넘어야 한다.
     *
     * <ul>
     *   <li><b>좌표</b> — 없으면 동선에 못 올린다. 좌표가 틀린 것으로 확인된 역은 비워 뒀다(신림·대야·
     *       상동·진성) — 틀린 좌표를 남기면 최근접 탐색이 엉뚱한 곳을 답한다
     *   <li><b>시도</b> — 좌표가 있으면 판정돼 있다
     *   <li><b>간선 정차</b> — 통근 전용 역을 고르면 열차 없는 코스가 나온다({@link #intercity})
     * </ul>
     */
    public Optional<OriginHub> toOriginHub() {
        if (!hasCoordinate() || !intercity) {
            return Optional.empty();
        }
        return OriginSido.ofStored(sido)
                .map(found -> OriginHub.of(
                        OriginHubType.TRAIN_STATION, code, name, found, new Coordinate(lat, lng)));
    }
}
