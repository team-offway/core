package com.offway.core.inventory.infrastructure.probe;

/** 외부 API 하나가 "지금 조회 가능한지"를 스스로 판단해 결과를 돌려준다. */
public interface ExternalApiProbe {
    ProbeResult probe();
}
