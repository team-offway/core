package com.offway.core.trip.domain;

import java.time.LocalDate;
import java.util.Locale;

/**
 * 두 축제 행이 <b>같은 회차</b>인가(#622 후속).
 *
 * <p>{@link SamePlace} 와 같은 자리다 — "같은 것인가" 를 판정하는 규칙을 도메인이 소유한다. 다만 축제는
 * 장소와 기준이 다르다.
 *
 * <h2>왜 {@link SamePlace} 로 안 되나</h2>
 *
 * <p>둘 다 필요한 판정이 아니다.
 *
 * <ul>
 *   <li>{@link SamePlace} 는 <b>좌표</b>로 가른다. 실측한 중복 한 건은 두 좌표가 약 <b>319m</b> 떨어져
 *       같은 출처 상한(200m)을 넘었다. 상한을 늘리면 같은 이름의 다른 장소가 접힌다.
 *   <li>축제는 <b>기간</b>으로 가려야 한다. 원본이 과거 회차(2023~2025)를 함께 싣는데, 이름만 보면
 *       그것들이 한 덩이가 된다 — 서로 다른 회차를 하나로 만드는 것은 중복을 남기는 것보다 나쁘다.
 * </ul>
 *
 * <h2>같은 회차의 기준</h2>
 *
 * <p><b>이름이 같고 기간이 하루라도 겹치면</b> 같은 회차다. 지자체가 날짜를 고쳐 다시 올리면 자연키
 * (지역·이름·시작일)가 달라져 두 행이 남는데, 그 두 행은 기간이 거의 같으므로 겹친다. 연도가 다른
 * 회차는 겹치지 않는다.
 *
 * <p>지역은 보지 않는다 — 부르는 쪽이 이미 한 지역 안에서 견준다.
 */
public final class SameFestivalEdition {

    private SameFestivalEdition() {
    }

    /**
     * 같은 회차인가.
     *
     * <p><b>기간을 모르면 거짓이다.</b> 가릴 수 없는 것을 접으면 다른 회차를 하나로 만들 수 있고, 그건
     * 중복이 남는 것보다 나쁘다 — 중복은 하나가 뽑히면 끝이지만, 잘못 접으면 없는 기간이 만들어진다.
     */
    public static boolean is(
            String nameA, LocalDate startA, LocalDate endA,
            String nameB, LocalDate startB, LocalDate endB) {
        if (!sameName(nameA, nameB)) {
            return false;
        }
        if (startA == null || endA == null || startB == null || endB == null) {
            return false;
        }
        return !startA.isAfter(endB) && !startB.isAfter(endA);
    }

    /**
     * 두 기간이 <b>함께 인정하는</b> 시작일 — 늦은 쪽.
     *
     * <p>어느 쪽이 맞는지 우리는 모른다. 넓은 쪽(이른 시작)을 쓰면 <b>한쪽만 주장하는 날</b>에 사용자를
     * 보낼 수 있고, 그건 #616 이 막으려던 그 일이다 — 문 닫힌 곳에 보내는 것보다 하루를 덜 매칭하는
     * 것이 낫다.
     */
    public static LocalDate laterStart(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    /** 두 기간이 함께 인정하는 종료일 — 이른 쪽. {@link #laterStart} 와 같은 이유다. */
    public static LocalDate earlierEnd(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    /**
     * 이름 비교 — 띄어쓰기·대소문자만 지운다.
     *
     * <p>{@code Locale.ROOT} 를 명시하는 이유는 {@link SamePlace} 와 같다. 인자 없는
     * {@code toLowerCase()} 는 JVM 기본 로케일을 따르는데, 그건 서버가 어디서 뜨는지에 달렸다.
     */
    private static boolean sameName(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String left = a.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return !left.isEmpty() && left.equals(b.replaceAll("\\s+", "").toLowerCase(Locale.ROOT));
    }
}
