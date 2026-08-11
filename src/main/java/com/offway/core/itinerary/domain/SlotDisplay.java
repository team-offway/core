package com.offway.core.itinerary.domain;

/**
 * 슬롯의 표시 정보 — 화면 카드에 그대로 나가는 값들(#157).
 *
 * <p><b>왜 묶는가.</b> 전부 {@code String} 이라 인자로 줄세우면 순서를 바꿔 넣어도 컴파일이 통과한다.
 * 주소 자리에 캐치프레이즈가 들어가도 아무도 못 잡는다. 넷째(전화번호)를 더하면서 그 위험이 실제로 커졌다.
 *
 * <p>여기 담기는 것은 <b>동선·불변식과 무관한</b> 값들이다. 좌표·이동시간처럼 코스가 성립하려면 반드시
 * 맞아야 하는 값은 {@link Slot} 이 직접 들고 검증한다. 표시 정보는 없으면 그 줄이 안 그려질 뿐이라
 * 전부 null 을 허용한다.
 */
public record SlotDisplay(String imageUrl, String address, String catchphrase, String tel) {

    private static final SlotDisplay NONE = new SlotDisplay(null, null, null, null);

    /** 표시 정보가 없는 슬롯(휴식·이동 등). */
    public static SlotDisplay none() {
        return NONE;
    }
}
