package com.offway.core.inventory.infrastructure.probe;

/** 외부 API 하나가 "지금 조회 가능한지"를 스스로 판단해 결과를 돌려준다. */
public interface ExternalApiProbe {

    ProbeResult probe();

    /**
     * 이 프로브가 재는 시스템의 라벨(#479) — 상태 판정과 로그가 같은 이름을 쓰게 한다.
     *
     * <p>값을 여기서 새로 정하지 않고 {@code ExternalSystems} 에서 도출한다. 두 곳이 각자 정하면
     * 어드민 표와 알림이 같은 API 를 다른 이름으로 부르게 된다.
     */
    String system();
}
