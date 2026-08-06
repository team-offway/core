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
 * 여객선 항구 마스터 한 행(#97) — TAGO 항구 코드·이름과 좌표(TMAP 지오코딩).
 *
 * <p><b>왜 여객선이 필요한가.</b> 인구감소지역 89곳 중 울릉군은 버스로 갈 수 없다. 육지에서 130㎞ 떨어진 섬이라
 * 배가 유일한 수단이다. 옹진·신안도 군청은 육지·연륙도에 있어 버스로 닿는 것처럼 보이지만, 관할 섬은 배로만 간다.
 *
 * <p><b>여객선터미널 목록이 아니라 항구 목록을 쓴다.</b> TAGO 는 둘 다 주는데 터미널 쪽(27곳)은 이름과 주소가
 * 어긋난 행이 절반 가까이다 — 인천 터미널에 전남 여수 주소가, 평택 터미널에 전남 목포 주소가 붙어 있고,
 * 2010년에 없어진 `마산시`·`진해시` 가 그대로 남아 있다. 항구 목록(500곳)은 이름이 정확하고 울릉_도동·저동·사동까지
 * 담고 있다.
 *
 * <p>좌표는 결측 가능하다 — 항구 목록도 좌표를 주지 않아 이름으로 지오코딩하는데, `주요섬` 처럼 실제 항구가 아닌
 * 항목이 섞여 있어 전부 찾아지지는 않는다. 못 찾은 곳은 최근접 탐색에서만 빠진다.
 */
@Entity
@Table(name = "ferry_port")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FerryPort {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** TAGO 항구 코드(예: SEA43113 울릉_도동). 운항정보 조회의 출발지 ID. */
    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    private Double lat;

    private Double lng;

    private FerryPort(String code, String name, Double lat, Double lng) {
        // 코드·이름은 누가 만들든 반드시 있어야 하는 불변식(좌표만 결측 허용) — DB flush 까지 미루지 않는다.
        this.code = Objects.requireNonNull(code, "항구 코드는 null 일 수 없습니다.");
        this.name = Objects.requireNonNull(name, "항구 이름은 null 일 수 없습니다.");
        this.lat = lat;
        this.lng = lng;
    }

    /** 코드·이름·좌표로 만든다(시드 로딩·테스트용). 좌표는 결측 가능. */
    public static FerryPort of(String code, String name, Double lat, Double lng) {
        return new FerryPort(code, name, lat, lng);
    }

    /** 최근접 탐색에 쓸 수 있는가 — 좌표가 있어야 한다. */
    public boolean hasCoordinate() {
        return lat != null && lng != null;
    }
}
