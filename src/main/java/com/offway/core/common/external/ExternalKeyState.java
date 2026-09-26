package com.offway.core.common.external;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 지금 어느 키로 돌고 있나(#596) — 주 키인가 보조 키인가.
 *
 * <h2>왜 상태가 필요한가</h2>
 *
 * <p>처음에는 상태 없이 짰다. 매번 주 키로 먼저 가고 실패하면 보조 키로 한 번 더 — 단순해서 좋았지만
 * <b>주 키가 마른 뒤에는 요청마다 죽은 키를 한 번씩 두드린다.</b> 호출이 두 배가 되고 지연도 두 배가
 * 되며, 주 키 사용량 집계는 100% 를 넘어 계속 올라간다. 마른 것을 이미 아는데도 매번 다시 확인하는 셈이다.
 *
 * <p>그래서 <b>한도가 말랐다는 것이 확인된 날</b>은 그 사실을 기억하고, 그날은 보조 키를 먼저 쓴다.
 *
 * <h2>"확인된" 만 기억한다</h2>
 *
 * <p>아무 실패에나 이 표시를 달면 <b>일시적인 5xx 하나가 그날 내내 보조 키를 쓰게 만든다.</b> 그러면
 * 정작 보조 키가 필요한 순간에 그쪽 한도가 이미 닳아 있다. 게이트웨이가 한도라고 <b>말한</b> 경우만
 * 기억한다(data.go.kr 은 {@code reasonCode=22}, TMAP 은 429).
 *
 * <h2>날짜로 스스로 풀린다</h2>
 *
 * <p>일일 한도는 KST 자정에 돌아온다. 표시를 지우는 장치를 따로 두지 않고 <b>어느 날짜에 말랐는지</b>를
 * 적어, 날짜가 바뀌면 저절로 주 키로 돌아간다. 지우는 일을 누가 언제 하느냐를 정하지 않아도 된다.
 *
 * <p>기억은 메모리에 둔다. 재시작하면 주 키부터 다시 시도하는데, 그건 <b>맞는 동작</b>이다 — 그 사이
 * 날짜가 바뀌었을 수도 있고, 한 번 더 두드리는 대가는 호출 하나다.
 */
@Component
public class ExternalKeyState {

    /** 일일 한도가 KST 자정에 돌아온다 — 이 기억의 "하루" 도 같은 경계를 쓴다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /** API → 주 키가 마른 것으로 확인된 날짜. 키 공간은 API 개수만큼이라 유한하다. */
    private final Map<ExternalApi, LocalDate> primaryExhaustedOn = new ConcurrentHashMap<>();

    /** 게이트웨이가 "한도" 라고 말했다 — 오늘은 보조 키를 먼저 쓴다. */
    public void markPrimaryExhausted(ExternalApi api) {
        primaryExhaustedOn.put(api, LocalDate.now(SERVICE_ZONE));
    }

    /** 오늘 이 API 는 보조 키로 도나. */
    public boolean usingFallback(ExternalApi api) {
        return LocalDate.now(SERVICE_ZONE).equals(primaryExhaustedOn.get(api));
    }

    /**
     * 알림·로그에 실을 이름 — 사람이 읽는 말로 둔다.
     *
     * <p>이 한 줄이 있어야 {@code 9000/10000 (90%)} 를 보고 무엇을 할지 정할 수 있다. 주 키로 도는
     * 중이면 곧 마른다는 뜻이고, 이미 보조 키면 그 숫자는 <b>멈춘 값</b>이다.
     */
    public String label(ExternalApi api) {
        return usingFallback(api) ? "보조 키" : "주 키";
    }
}
