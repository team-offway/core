package com.offway.core.transport.infrastructure.kakao;

import com.offway.core.transport.infrastructure.kakao.dto.FoundPlace;
import java.util.List;

/**
 * 출발지로 고를 주소·장소를 찾는다 — 우리 허브 목록에 없는 곳을 위한 port(#590).
 *
 * <p><b>왜 필요한가.</b> 역·터미널 목록만으로는 "분당"·"해운대" 처럼 사용자가 자기 동네를 부르는 말을
 * 받을 수 없다. 그 지점의 좌표를 알면 최근접 허브를 찾는 것은 이미 되어 있다
 * ({@code BusTerminalResolver#nearest}).
 *
 * <p><b>구현은 실패를 던지지 않는다.</b> 주소 검색이 안 되면 허브 목록만으로 답하면 되고, 그건 화면이
 * 비는 것보다 낫다 — 키가 없는 로컬에서도 자동완성이 돌아야 한다는 뜻이기도 하다.
 */
public interface OriginPlaceSearchClient {

    /**
     * 검색어에 걸리는 주소·장소. 실패하거나 키가 없으면 빈 목록.
     *
     * @param query 사용자가 친 말
     * @param limit 받을 최대 건수
     */
    List<FoundPlace> search(String query, int limit);
}
