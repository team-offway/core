package com.offway.core.liveactivity.infrastructure.apns;

import java.time.LocalDate;

/**
 * 잠금화면 카드 하나에 보낼 내용(#575) — <b>갱신이거나 종료거나</b>.
 *
 * <p>둘뿐이고 앞으로도 둘이라 {@code sealed} 로 닫는다. {@code event} 문자열과 nullable 필드 묶음으로
 * 두면 "종료인데 남은 날이 들어 있는" 조합이 컴파일된다.
 *
 * <h2>문구가 아니라 재료를 보낸다</h2>
 *
 * <p>{@code "정선군 여행 D-2"} 같은 완성된 문구를 서버가 만들지 않는다. 그러면 같은 한국어 조립 규칙이
 * 서버와 앱 두 곳에 생겨, 카피가 바뀔 때마다 양쪽을 고치고 배포 시점까지 맞춰야 한다. 그 사이에는 앱을
 * 열었을 때와 자정 갱신이 서로 다른 말을 한다.
 *
 * <p>여기 있는 것은 숫자와 이름뿐이고, 조립은 위젯이 한다. 필드 이름은 앱의
 * {@code TripActivityAttributes.ContentState} 와 <b>1:1</b> 이어야 한다 — 하나라도 어긋나면 iOS 가
 * 디코딩에 실패하고, <b>오류를 주지 않은 채 화면이 안 바뀐다</b>.
 */
public sealed interface LiveActivityPush {

    /**
     * 잠금화면 값을 바꾼다.
     *
     * <p><b>{@code daysLeft} 와 {@code dayNth} 는 둘 중 하나만 값이 있다.</b> 출발 전이면 남은 날,
     * 떠난 뒤면 며칠째다. 둘 다 채우면 앱이 어느 쪽을 보여야 할지 정할 수 없다.
     *
     * @param regionName 여행지 이름 (예: {@code 정선군})
     * @param daysLeft 남은 날. 여행 중이면 {@code null}
     * @param dayNth 여행 며칠째. 출발 전이면 {@code null}
     * @param startDate 출발일 — 앱이 기간 표기({@code 2박 3일})와 날짜 범위를 여기서 만든다
     * @param endDate 종료일
     */
    record Update(String regionName, Integer daysLeft, Integer dayNth, LocalDate startDate, LocalDate endDate)
            implements LiveActivityPush {
    }

    /**
     * 잠금화면에 카드를 <b>새로 만든다</b>(#583) — push-to-start.
     *
     * <p><b>앱이 안 켜져 있어도 된다.</b> iOS 가 카드를 만들고 앱을 백그라운드로 깨워 그 카드의
     * 갱신 토큰을 준다. 앱이 그 토큰을 {@code POST /api/v1/live-activities} 로 올리면 그 뒤 갱신·종료는
     * 지금 경로(#577)를 그대로 탄다.
     *
     * <p>{@link Update} 와 {@code state} 를 공유한다 — <b>같은 다섯 칸이어야 한다.</b> 띄울 때와
     * 갱신할 때 모양이 다르면 앱의 {@code ContentState} 디코딩이 한쪽에서만 성공하고, 그 실패는
     * 오류 없이 화면이 안 바뀌는 것으로만 드러난다.
     *
     * @param courseId 어느 코스인가. <b>문자열로 나간다</b> — 앱의 {@code TripActivityAttributes} 가
     *     {@code let courseId: String} 이라, 숫자로 보내면 디코딩에 실패해 카드가 조용히 안 뜬다
     * @param state 처음 그릴 값. 갱신과 같은 계약이다
     */
    record Start(long courseId, Update state) implements LiveActivityPush {
    }

    /**
     * 카드를 내린다 — 여행이 끝났거나 코스가 사라졌다.
     *
     * <p>안 보내면 iOS 가 스스로 걷어낼 때까지 <b>최대 12시간</b> 끝난 여행이 잠금화면에 남는다.
     */
    record End() implements LiveActivityPush {
    }

    /** 종료는 값이 없어 매번 새로 만들 이유가 없다. */
    LiveActivityPush END = new End();
}
