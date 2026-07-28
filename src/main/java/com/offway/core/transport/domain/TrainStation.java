package com.offway.core.transport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
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

    private TrainStation(String code, String name, Double lat, Double lng) {
        // 역코드·역명은 누가 만들든 반드시 있어야 하는 불변식(좌표만 결측 허용) — DB flush 까지 미루지 않고 생성 시점에 막는다.
        this.code = Objects.requireNonNull(code, "역코드는 null 일 수 없습니다.");
        this.name = Objects.requireNonNull(name, "역명은 null 일 수 없습니다.");
        this.lat = lat;
        this.lng = lng;
    }

    /** 코드·역명·좌표로 만든다(시드 로딩·테스트용). 좌표는 결측 가능. */
    public static TrainStation of(String code, String name, Double lat, Double lng) {
        return new TrainStation(code, name, lat, lng);
    }

    /** 좌표가 있어 최근접 계산에 쓸 수 있는가. */
    public boolean hasCoordinate() {
        return lat != null && lng != null;
    }
}
