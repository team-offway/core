package com.offway.core.trip.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * 국가유산의 대분류(#160) — 국가유산청이 주는 {@code gcodeName} 이다. 이 상수가 <b>갈 수 있는 곳인지</b>를 안다.
 *
 * <p><b>왜 종목이 아니라 대분류인가.</b> 처음에는 종목(국보·보물·사적)으로 가르려 했는데 그걸로는 안 갈린다.
 * 우리 89곳의 국보·보물 표본 12건이 전부 {@code 동궐도}·{@code 자수 초충도 병풍} 같은 소장 유물이었다. 주소가
 * 소장 기관으로 찍혀 있어 그대로 코스에 넣으면 그림 한 점이 목적지가 된다. 반대로 종목으로 국보·보물을 통째로
 * 빼면 부석사 무량수전·사찰 대웅전·석탑처럼 진짜 목적지가 함께 날아간다. 대분류는 정확히 이 둘을 가른다.
 *
 * <p>모르는 값은 {@link #from} 이 비어 있음으로 돌려준다. 제공기관이 분류를 늘리면 그 건은 코스에 안 쓰이고,
 * 적재 로그에 건수가 남는다 — 조용히 섞여 들어가는 쪽이 나쁘다.
 */
public enum HeritageGroup {

    /** 건축물·성곽·능묘·사찰 등 부동산. 그 자리에 가는 것이 곧 관람이다. */
    HISTORIC_STRUCTURE("유적건조물", true),

    /** 노거수·동굴·경관·서식지. 천연기념물 상당수가 여기 속하고 전부 야외다. */
    NATURAL("자연유산", true),

    /** 근대 건축물이 주다 — 구 역사(驛舍)·근대 가옥처럼 지금도 서 있는 것들. */
    REGISTERED("등록문화유산", true),

    /** 회화·공예·조각 등 동산. 소장 기관 주소로 찍혀 목적지가 될 수 없다. */
    ARTIFACT("유물", false),

    /** 전적·문서·서간. 유물과 같은 이유로 목적지가 아니다. */
    RECORD("기록유산", false),

    /** 판소리·탈춤·옹기장 같은 기능·예능. 주소가 보유자 소재지라 장소 자체가 아니다. */
    INTANGIBLE("무형유산", false);

    private final String label;
    private final boolean visitable;

    HeritageGroup(String label, boolean visitable) {
        this.label = label;
        this.visitable = visitable;
    }

    /** 국가유산청이 쓰는 대분류 이름. */
    public String label() {
        return label;
    }

    /** 코스 스팟으로 쓸 수 있는가 — 그 자리에 가는 것이 관람이 되는 분류인가. */
    public boolean isVisitable() {
        return visitable;
    }

    /** 대분류 이름으로 찾는다. 모르는 값이면 비어 있음. */
    public static Optional<HeritageGroup> from(String label) {
        if (label == null || label.isBlank()) {
            return Optional.empty();
        }
        String trimmed = label.trim();
        return Arrays.stream(values()).filter(group -> group.label.equals(trimmed)).findFirst();
    }
}
