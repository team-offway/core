package com.offway.core.notification.domain;

/**
 * 알림 종류 — <b>앱이 아이콘·문구를 맞추는 키</b>(#263).
 *
 * <p><b>알림함 응답에는 문구를 싣지 않는다.</b> 프론트가 "어떤 값들이 오는지 정해 주시면 앱에서 아이콘·
 * 문구를 맞추겠다" 고 했고, 실제로 문구는 화면 폭·서체·강조에 묶여 있어 앱이 쥐는 편이 낫다. 응답에는 이
 * 상수 이름만 실린다 — {@code OpeningStatus} 와 같은 방식이다.
 *
 * <h2>배너 문구는 여기 있다 — 알림함과 수명이 다르기 때문이다(#355)</h2>
 *
 * <p>예전에는 <b>푸시에도</b> 문구를 안 실었다. "서버가 문구를 실으면 이미 발송된 알림이 영영 옛 문구로
 * 남는다" 는 이유였는데, 그 걱정은 <b>알림함에서만 성립한다.</b>
 *
 * <table>
 *   <tr><th></th><th>수명</th><th>문구가 굳는가</th></tr>
 *   <tr><td>알림함</td><td>30일 보관, 언제든 다시 조회</td><td><b>굳는다</b> — 그래서 type 만 내린다</td></tr>
 *   <tr><td>푸시 배너</td><td>도착 순간 한 번, 지나가면 끝</td><td>안 굳는다 — 다음 발송은 새 문구로 나간다</td></tr>
 * </table>
 *
 * <p>문구를 안 실은 대가는 <b>알림이 아예 안 뜨는 것</b>이었다. {@code data} 만 실은 메시지는 iOS 가 silent
 * push 로 취급해 백그라운드·종료 상태에서 아무것도 그리지 않는다. 그런데 이 알림들은 전날 20시·다음 날
 * 20시 배치라 <b>정확히 그 상태에서 필요하다.</b>
 *
 * <p>앱이 켜져 있을 때는 지금도 앱이 그린다 — FCM 은 포그라운드에서 {@code notification} 이 있어도 시스템
 * 배너를 띄우지 않고 앱에 넘긴다. 바뀌는 것은 <b>꺼져 있을 때뿐</b>이다.
 *
 * <p><b>보낼 사람이 없는 종류를 미리 나열하지 않는다.</b> 적어 두면 앱이 그 값을 기다리는 분기를 만들고,
 * 영영 오지 않는 분기가 남는다. 값이 느는 것은 클라이언트에 안전한 변경(추가)이므로 보낼 것이 생길 때 더한다.
 *
 * <p>추가 기준: <b>서버가 그 사실을 이미 알고 있고, 그것을 알릴 주체가 이 레포에 있는가.</b>
 */
public enum NotificationType {

    /**
     * 내일 여행을 떠난다 — 저장한 코스의 여행 시작일이 내일이다.
     *
     * <p>서버가 여행 날짜를 들고 있어 판단에 외부가 필요 없고, 알림을 받는 시점(전날)이 사용자가 할 일
     * (짐 싸기)과 맞는다.
     */
    TRIP_TOMORROW("내일은 여행을 떠나는 날이에요. 짐은 다 챙기셨나요?") {
        @Override
        public String bannerTitle(String destination) {
            return GENERIC_TITLE;
        }
    },

    /**
     * 여행이 끝났다 — 연차를 기록해 달라(#302).
     *
     * <p><b>모달만으로는 놓친다.</b> "다녀오셨나요?"(#116)는 홈·내 연차에 들어가는 그 순간에만 묻는다.
     * 딴 데를 누르면 그걸로 끝이고, 알림함에 흔적이 없어 <b>물어본 적이 있었는지조차 알 수 없다.</b>
     * 그 사이 연차는 안 깎인 채 남아 잔액이 실제와 벌어진다.
     *
     * <p>추가 기준을 둘 다 만족한다 — 서버가 여행 날짜를 들고 있고({@code Course}), 같은 판단을 하는 코드가
     * 이미 있다({@code TripOutcomeService.pending()}).
     */
    TRIP_AFTER("연차를 사용했다면 기록해주세요") {
        @Override
        public String bannerTitle(String destination) {
            if (destination == null || destination.isBlank()) {
                return DESTINATIONLESS_TRIP_AFTER_TITLE;
            }
            return destination + " " + DESTINATIONLESS_TRIP_AFTER_TITLE;
        }
    };

    /**
     * 여행지를 못 붙일 때 쓰는 제목 — 여행지가 붙을 때도 <b>같은 문장이 뒤에 온다.</b>
     *
     * <p>두 벌로 적어 두면 한쪽만 고쳐도 컴파일이 통과해, 여행지가 없는 사용자만 옛 문구를 받는다.
     */
    private static final String DESTINATIONLESS_TRIP_AFTER_TITLE = "여행 다녀오셨나요?";

    /**
     * 여행지를 말하지 않는 종류가 쓰는 제목.
     *
     * <p>제목 칸이 말하는 것은 "무슨 알림인지" 가 아니라 "어디서 온 알림인지" 다 — 무엇에 관한 알림인지는
     * 본문이 말한다. 그 기준이 아직 맞는 종류는 이 값을 쓴다.
     */
    private static final String GENERIC_TITLE = "알림";

    private final String bannerBody;

    NotificationType(String bannerBody) {
        this.bannerBody = bannerBody;
    }

    /**
     * 잠금화면·알림센터에 뜨는 제목 — <b>종류마다 다르고, 여행지를 받을 수 있다.</b>
     *
     * <p>예전에는 종류와 무관하게 "알림" 하나였다. 배너와 알림함이 다르게 말하면 사용자가 두 개의 알림으로
     * 읽는다는 이유였고, 그 판단 자체는 지금도 맞다. 바뀐 것은 <b>제목에 쓸 말이 생겼다</b>는 점이다 —
     * 여행이 끝난 뒤 묻는 알림은 어느 여행인지가 곧 그 알림의 정체라, 제목에 여행지를 두면 잠금화면에서
     * 본문을 펼치지 않고도 무엇을 기록하라는 것인지 읽힌다.
     *
     * @param destination 여행지 이름(짧은 형태, 예: "정선"). 코스가 지워졌거나 지역을 못 찾으면 {@code null}
     *                    이고, 그때는 여행지 없는 제목으로 내려간다 — 제목이 빈칸으로 시작하면 안 된다.
     */
    public abstract String bannerTitle(String destination);

    /**
     * 배너 본문. <b>개행을 넣지 않는다</b> — 앱은 두 줄로 그리지만 배너는 폭이 좁아 어차피 한 줄로 줄어들고,
     * 개행이 그 자리에서 공백으로 보인다.
     */
    public String bannerBody() {
        return bannerBody;
    }
}
