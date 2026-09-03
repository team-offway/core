package com.offway.core.common.external;

/**
 * 이 배치가 지금 이 연동을 불러도 되나(#403).
 *
 * <p>스위치(꺼 뒀나)와 상한(오늘 몫을 다 썼나)을 <b>한 물음으로 합친다.</b> 배치마다 둘을 따로
 * 확인하게 하면 여섯 곳 중 한 곳은 한쪽을 빠뜨린다.
 *
 * <p>{@link ExternalApiCachePolicy} 와 같은 이유로 좁게 둔다 — 단위 테스트가 저장소·스케줄러를 끌고
 * 오지 않게, 그리고 배치가 설정 저장 경로를 실수로 부르지 않게.
 *
 * <p>구현은 {@link ExternalApiSettings} 다.
 */
@FunctionalInterface
public interface ExternalApiBatchPolicy {

    boolean batchMayCall(String batchName, ExternalApi api);

    /** 늘 돈다 — <b>이 기능이 붙기 전의 동작</b>. 단위 테스트용이다. */
    ExternalApiBatchPolicy ALWAYS_RUN = (batchName, api) -> true;
}
