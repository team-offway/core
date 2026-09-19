package com.offway.core.transport.domain;

import com.offway.core.common.geo.Coordinate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /** 고속·시외 중 어느 쪽인가 — 구간을 어느 API 로 물을지 정한다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BusTerminalKind kind;

    /**
     * 터미널인가, 지나가며 서는 정류소인가(#446).
     *
     * <p>TAGO 목록에는 잠실역·광나루역·DDP 같은 <b>경유 정류소가 섞여 있다.</b> 좌표 최근접으로만 고르면
     * 그런 곳이 출발 지점으로 뽑히는데, 정류소는 특정 노선만 서므로 "거기서 타세요" 가 틀린 안내가 될 수 있다.
     *
     * <p><b>근거를 못 찾은 행은 {@code true} 다.</b> 정류소로 잘못 낮추면 멀쩡한 터미널이 뒤로 밀리는데,
     * 반대는 지금까지의 동작과 같을 뿐이다.
     */
    @Column(nullable = false)
    private boolean isTerminal = true;

    private Double lat;

    private Double lng;

    /**
     * 속한 시·도(#590) — 출발지 자동완성의 지역 검색이 보는 값.
     *
     * <p><b>왜 이름으로 못 하나.</b> "서울" 을 쳤을 때 서울의 터미널이 떠야 하는데, 이름에 그 말이 든
     * 것만으로는 안 된다. 좌표 사각형으로 자르는 것도 안 된다 — 서울 범위로 잡으면 부천·일산 같은
     * 경기 것이 섞인다.
     *
     * <p><b>결측 가능하다.</b> 좌표가 없으면 판정할 근거가 없다. 그런 터미널은 애초에 출발지로 고를 수
     * 없으므로(동선에 못 올린다) 부재가 정상이다.
     *
     * <p>표기는 통합 이전 기준이다 — 자세한 이유는 {@link OriginSido} 에 적었다.
     */
    @Column(length = 20)
    private String sido;

    private BusTerminal(String code, String name, BusTerminalKind kind, Double lat, Double lng, String sido) {
        // 코드·이름·종류는 누가 만들든 반드시 있어야 하는 불변식(좌표만 결측 허용) — DB flush 까지 미루지 않는다.
        this.code = Objects.requireNonNull(code, "터미널 코드는 null 일 수 없습니다.");
        this.name = Objects.requireNonNull(name, "터미널 이름은 null 일 수 없습니다.");
        this.kind = Objects.requireNonNull(kind, "터미널 종류는 null 일 수 없습니다.");
        // 좌표는 결측을 허용하되 **반쪽은 허용하지 않는다.** 한쪽만 있으면 hasCoordinate() 가 조용히
        // 걸러내 시드·임포트 오류가 드러나지 않는다.
        if ((lat == null) != (lng == null)) {
            throw new IllegalArgumentException("위도와 경도는 함께 있거나 함께 없어야 합니다.");
        }
        this.lat = lat;
        this.lng = lng;
        this.sido = sido;
    }

    /** 코드·이름·종류·좌표로 만든다(시드 로딩·테스트용). 좌표는 결측 가능. */
    public static BusTerminal of(String code, String name, BusTerminalKind kind, Double lat, Double lng) {
        return new BusTerminal(code, name, kind, lat, lng, null);
    }

    /**
     * 시도까지 함께 만든다(#590).
     *
     * <p>운영에서는 시도를 마이그레이션이 채우므로 이 팩토리가 필요한 곳은 테스트다 — 출발지 제안은
     * 시도가 있어야 성립하는데, 위 팩토리로 만든 터미널은 지역 검색에 걸리지 않는다.
     */
    public static BusTerminal of(
            String code, String name, BusTerminalKind kind, Double lat, Double lng, String sido) {
        return new BusTerminal(code, name, kind, lat, lng, sido);
    }

    /** 최근접 탐색에 쓸 수 있는가 — 좌표가 있어야 한다. */
    public boolean hasCoordinate() {
        return lat != null && lng != null;
    }

    /**
     * 출발지 제안 한 줄로 옮긴다 — 올릴 수 없으면 비어 있다(#590).
     *
     * <p>세 조건을 모두 넘어야 한다.
     *
     * <ul>
     *   <li><b>좌표</b> — 없으면 동선에 못 올린다. 고를 수 있게 하면 그 코스가 통째로 degrade 된다
     *   <li><b>시도</b> — 좌표가 있으면 판정돼 있다. 없다는 것은 시드가 어긋났다는 신호다
     *   <li><b>터미널</b> — 경유 정류소는 특정 노선만 서므로 "거기서 타세요" 가 틀린 안내가 된다
     * </ul>
     */
    public Optional<OriginHub> toOriginHub() {
        if (!hasCoordinate() || !isTerminal) {
            return Optional.empty();
        }
        return toAnyOriginHub();
    }

    /**
     * 좌표만 있으면 출발지로 옮긴다 — <b>이미 고른 출발지를 되살릴 때</b> 쓴다(#590).
     *
     * <p>{@link #toOriginHub()} 와 다른 점은 <b>경유 정류소 여부를 보지 않는다</b>는 것이다. 필터는
     * "무엇을 추천하나" 의 규칙이라, 앱이 저장해 둔 코드를 되살리는 자리에 그대로 적용하면 우리가
     * 목록을 좁힐 때마다 남의 저장값이 깨진다 — 자세한 사정은 {@code OriginHubCatalog#findByCode}
     * 에 적었다.
     *
     * <p>좌표는 여전히 요구한다 — 없으면 동선에 올릴 값이 아예 없다.
     */
    public Optional<OriginHub> toAnyOriginHub() {
        if (!hasCoordinate()) {
            return Optional.empty();
        }
        return OriginSido.ofStored(sido)
                .map(found -> OriginHub.of(
                        OriginHubType.BUS_TERMINAL, code, name, found, new Coordinate(lat, lng)));
    }
}
