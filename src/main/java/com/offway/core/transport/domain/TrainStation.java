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

    /** 좌표가 있어 최근접 계산에 쓸 수 있는가. */
    public boolean hasCoordinate() {
        return lat != null && lng != null;
    }

    /**
     * 출발지 제안 한 줄로 옮긴다 — 올릴 수 없으면 비어 있다(#590).
     *
     * <p>버스와 달리 <b>거르는 조건이 좌표와 시도뿐이다.</b> 통근 전용 역(옥수·서빙고·신림)을 가려낼
     * 근거가 데이터에 없다 — 이 마스터는 TAGO 노드 목록 전체이고, 코드 접두는 노선(경부선·중앙선)이라
     * 간선과 통근을 구별하지 않는다. 실측하려면 역마다 TAGO 를 물어야 해서 한도를 태운다.
     *
     * <p><b>그래서 거르지 않고 정렬로 내린다.</b> 지금보다 나빠지지 않는다 — GPS 를 쓰는 지금도 옥수동에
     * 있는 사용자는 최근접 탐색이 옥수역을 골라 같은 경로를 탄다. 자동완성이 만드는 새 문제가 아니다.
     */
    public Optional<OriginHub> toOriginHub() {
        if (!hasCoordinate()) {
            return Optional.empty();
        }
        return OriginSido.ofStored(sido)
                .map(found -> OriginHub.of(
                        OriginHubType.TRAIN_STATION, code, name, found, new Coordinate(lat, lng)));
    }
}
