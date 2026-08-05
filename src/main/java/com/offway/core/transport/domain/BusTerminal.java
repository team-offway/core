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
 * 고속버스 터미널 마스터 한 행(#107) — TAGO 터미널 코드·이름과 좌표(TMAP 지오코딩).
 *
 * <p>터미널 상세 API 가 없고 전국이 452곳뿐이라 마스터를 우리가 소유(시드)한다. 기차역({@link TrainStation})과 같은
 * 판단이다.
 *
 * <p><b>좌표는 결측 가능하다.</b> 터미널 목록 API 는 코드·이름만 주고 좌표를 주지 않아 이름으로 지오코딩하는데,
 * 목록에 {@code 2구간} 처럼 실제 터미널이 아닌 항목도 섞여 있어 전부 찾아지지는 않는다. 못 찾은 곳은 최근접 탐색에서
 * 빠질 뿐 시드에는 남긴다 — 코드가 실재하므로 구간 조회에는 여전히 쓸 수 있다.
 */
@Entity
@Table(name = "bus_terminal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusTerminal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** TAGO 터미널 코드(예: NAEK010). 구간 조회의 출발/도착 터미널 코드. */
    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    private Double lat;

    private Double lng;

    private BusTerminal(String code, String name, Double lat, Double lng) {
        // 코드·이름은 누가 만들든 반드시 있어야 하는 불변식(좌표만 결측 허용) — DB flush 까지 미루지 않는다.
        this.code = Objects.requireNonNull(code, "터미널 코드는 null 일 수 없습니다.");
        this.name = Objects.requireNonNull(name, "터미널 이름은 null 일 수 없습니다.");
        this.lat = lat;
        this.lng = lng;
    }

    /** 코드·이름·좌표로 만든다(시드 로딩·테스트용). 좌표는 결측 가능. */
    public static BusTerminal of(String code, String name, Double lat, Double lng) {
        return new BusTerminal(code, name, lat, lng);
    }

    /** 최근접 탐색에 쓸 수 있는가 — 좌표가 있어야 한다. */
    public boolean hasCoordinate() {
        return lat != null && lng != null;
    }
}
