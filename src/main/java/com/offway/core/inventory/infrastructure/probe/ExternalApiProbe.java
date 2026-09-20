package com.offway.core.inventory.infrastructure.probe;

import com.offway.core.common.external.ExternalApi;
import java.util.Optional;

/** 외부 API 하나가 "지금 조회 가능한지"를 스스로 판단해 결과를 돌려준다. */
public interface ExternalApiProbe {

    ProbeResult probe();

    /**
     * 이 프로브가 태우는 한도(#594) — 앱이 안 쓰는 API 면 비어 있다.
     *
     * <h2>왜 프로브가 한도를 알아야 하나</h2>
     *
     * <p>프로브도 진짜 호출이라 <b>한도를 진짜로 깎는다.</b> 그런데 집계는 각 {@code *ClientImpl} 이
     * {@code ExternalApiCallRecorder} 를 직접 불러서 하고, 프로브는 {@code externalWebClient} 를 바로
     * 쓰므로 그 경로를 안 지났다 — <b>하루 288 콜이 아무 데도 안 남았다.</b>
     *
     * <p>미상으로 세어지는 것보다 나쁘다. 미상은 "누군지 모르는 채 세어진 것" 이고 이쪽은 "세어지지도
     * 않은 것" 이라, 앱은 한도가 남았다고 판단해 배치를 발사한다(CLAUDE.md §외부 API 한도).
     *
     * <h2>왜 Optional 인가</h2>
     *
     * <p>코레일 열차운행정보는 우리가 앱에서 안 부른다 — 프로브만 친다. {@link ExternalApi} 에 항목이
     * 없으므로 셀 자리도 없다. 없는 항목을 억지로 만들면 "우리가 쓰는 API" 목록이 사실과 어긋난다.
     */
    Optional<ExternalApi> quota();

    /**
     * 이 프로브가 재는 시스템의 라벨(#479) — 상태 판정과 로그가 같은 이름을 쓰게 한다.
     *
     * <p>값을 여기서 새로 정하지 않고 {@code ExternalSystems} 에서 도출한다. 두 곳이 각자 정하면
     * 어드민 표와 알림이 같은 API 를 다른 이름으로 부르게 된다.
     */
    String system();
}
