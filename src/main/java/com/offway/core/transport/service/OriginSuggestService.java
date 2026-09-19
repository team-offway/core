package com.offway.core.transport.service;

import com.offway.core.common.geo.Coordinate;
import com.offway.core.transport.domain.OriginCode;
import com.offway.core.transport.domain.OriginHub;
import com.offway.core.transport.domain.OriginHubType;
import com.offway.core.transport.domain.TransportException;
import com.offway.core.transport.infrastructure.kakao.OriginPlaceSearchClient;
import com.offway.core.transport.infrastructure.kakao.dto.FoundPlace;
import com.offway.core.transport.service.dto.OriginSuggestion;
import com.offway.core.transport.service.dto.ResolvedOrigin;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 출발지 자동완성과 출발지 코드 해석(#590).
 *
 * <p><b>왜 이 기능이 있는가.</b> 앱이 GPS 수집을 그만둔다. 위치정보 사전상담에서 "개인의 위치를
 * 특정하지 않더라도 수집하는 순간 신고 대상" 이라는 답을 받았고, 익명·가명 처리로는 빠져나갈 수 없다.
 * 그래서 <b>사용자가 출발지를 직접 고르게</b> 바꾼다 — 목록에서 고르는 것은 기기 위치를 파악하는 것이
 * 아니라 입력이다.
 *
 * <h2>허브를 먼저, 주소로 메운다</h2>
 *
 * 우리 역·터미널이 먼저다. 그 목록은 <b>외부 호출 없이 인메모리로 즉시</b> 답하고, 고르면 그 좌표가
 * 곧 출발 지점이라 최근접 허브를 다시 찾을 필요가 없다.
 *
 * <p>주소 검색은 <b>자리가 남을 때만</b> 부른다. "서울" 처럼 허브로 상한이 차는 검색어에서는 외부를
 * 아예 부르지 않는다 — 자동완성은 글자마다 부르는 화면이라, 안 불러도 되는 호출을 줄이는 것이 한도를
 * 지키는 가장 확실한 방법이다.
 *
 * <p><b>트랜잭션이 없다.</b> 읽기만 하고 그 읽기는 인메모리이며, 외부 호출이 섞여 있어 트랜잭션에
 * 넣으면 커넥션을 붙잡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OriginSuggestService {

    /** 출발지를 고르기 전에 쓰는 기본 허브 — 서울역의 TAGO nodeid. */
    private static final String DEFAULT_ORIGIN_HUB_CODE = "NAT010000";

    private static final String DEFAULT_ORIGIN_NAME = "서울역";

    /** 시드에서 서울역을 못 찾았을 때의 최후 폴백 — 서울역 좌표. */
    private static final Coordinate DEFAULT_ORIGIN_COORDINATE = new Coordinate(37.553261, 126.969133);

    private final OriginHubCatalog catalog;
    private final OriginPlaceSearchClient placeSearchClient;

    /**
     * 검색어에 걸리는 출발지 후보 — 허브 먼저, 남은 자리를 주소로.
     *
     * <p>검색어가 짧으면 빈 목록이다(정상 결과 — 화면이 아직 아무것도 안 그린다).
     */
    public List<OriginSuggestion> suggest(String query) {
        // **짧은 검색어는 외부까지 막는다.** 허브에 쓰는 최소 글자 수 판단이 주소 검색에도 그대로
        // 적용돼야 한다 — 한 글자로 외부를 부르면 쓸모없는 결과에 한도를 태운다. 예전에는 허브만
        // 걸러 "서" 한 글자가 카카오로 나갔다.
        if (!OriginHubCatalog.isSearchable(query)) {
            return List.of();
        }
        List<OriginHub> hubs = catalog.search(query);
        List<OriginSuggestion> suggestions = new ArrayList<>(hubs.stream().map(OriginSuggestion::from).toList());

        int room = OriginHubCatalog.MAX_SUGGESTIONS - suggestions.size();
        if (room <= 0) {
            return List.copyOf(suggestions);
        }
        for (FoundPlace place : placeSearchClient.search(query.trim(), room)) {
            suggestions.add(OriginSuggestion.from(place));
        }
        return List.copyOf(suggestions);
    }

    /**
     * 앱이 되돌려 보낸 출발지 코드를 좌표·이름으로 푼다.
     *
     * <p><b>없을 수 있다</b> — 코드가 망가졌거나, 들고 있던 허브가 시드에서 사라진 경우다. 부재를 그대로
     * 알려 부르는 쪽이 폴백을 고르게 한다. 여기서 조용히 기본값으로 바꾸면 사용자는 <b>엉뚱한 곳에서
     * 출발하는 코스</b>를 받고, 그것이 틀렸다는 사실을 아무도 모른다.
     *
     * <h2>이름을 어디서 얻나</h2>
     *
     * <b>허브 코드면 우리가 안다</b> — 우리가 내려준 목록의 표시 이름을 쓴다. 앱이 이름을 실어 보내지
     * 않아도 되고(#382 의 {@code fromPlace} 가 그래서 사라진다), 앱이 보낸 이름이 우리 목록과 어긋날
     * 일도 없다.
     *
     * <p><b>좌표 코드({@code GEO:})면 앱이 보여준 이름을 받는다.</b> 그 지점에는 우리 이름이 없다.
     * 최근접 허브의 이름을 대신 쓰는 방법을 검토했지만 버렸다 — 사용자가 "분당구청" 을 골랐는데 카드가
     * "서현역에서 출발" 이라고 적는 것은 고른 것과 다른 말이다.
     *
     * @param code 앱이 되돌려 보낸 출발지 코드
     * @param fallbackName 좌표 코드일 때 쓸 이름(앱이 목록에서 보여준 그것). 허브 코드면 무시된다
     */
    public Optional<ResolvedOrigin> resolve(OriginCode code, String fallbackName) {
        if (code == null) {
            return Optional.empty();
        }
        if (code.isCoordinate()) {
            String name = fallbackName == null ? "" : fallbackName.trim();
            return code.coordinate().map(coordinate -> new ResolvedOrigin(coordinate, name));
        }
        return catalog.findByCode(code)
                .map(hub -> new ResolvedOrigin(hub.coordinate(), hub.displayName()));
    }

    /**
     * 요청이 실은 출발지를 하나로 정한다 — 전환 기간 동안 세 형태를 다 받는다(#590).
     *
     * <p>우선순위와 그 이유:
     *
     * <ol>
     *   <li><b>{@code originCode}</b> — 사용자가 고른 값이다. 있으면 무조건 이것이 이긴다
     *   <li><b>{@code originLat}·{@code originLng}</b> — 위치 수집을 걷어내기 전의 구버전 앱이다.
     *       심사를 거쳐야 해서 한동안 남는다
     *   <li><b>기본 출발지(서울역)</b> — 아직 아무것도 고르지 않은 화면이다
     * </ol>
     *
     * <p><b>코드가 있는데 못 풀면 거절한다.</b> 조용히 다음 순위로 내려가면 사용자가 고른 곳과 다른
     * 데서 출발하는 코스가 나오고, 그것이 틀렸다는 사실이 아무 흔적도 남기지 않는다.
     *
     * <p><b>기본값은 조용한 실패가 아니다.</b> 출발지를 고르기 전에도 추천을 보여주는 화면이 있어서,
     * 그 상태는 버그가 아니라 정상 흐름이다 — 그래서 info 로 남긴다.
     */
    public ResolvedOrigin resolveOrDefault(String originCode, String originName, Double lat, Double lng) {
        if (originCode != null && !originCode.isBlank()) {
            return resolve(new OriginCode(originCode.trim()), originName)
                    .orElseThrow(TransportException::unknownOriginCode);
        }
        if (lat != null && lng != null) {
            String name = originName == null ? "" : originName.trim();
            return new ResolvedOrigin(new Coordinate(lat, lng), name);
        }
        log.info("출발지가 없어 기본값을 쓴다 — {}", DEFAULT_ORIGIN_NAME);
        return defaultOrigin();
    }

    /**
     * 출발지를 고르기 전에 쓰는 기본값 — 서울역.
     *
     * <p><b>왜 서울역인가.</b> 우리 허브 목록에 있는 실제 지점이고, 전국에서 가장 많은 사람이 출발하는
     * 곳이다. 앱도 같은 폴백을 쓰고 있었다.
     *
     * <p>목록에서 찾아 쓰고, 못 찾으면 좌표 상수로 떨어진다. 시드가 바뀌어 코드가 사라지는 경우에도
     * 추천 화면이 비지 않게 하려는 것이다.
     */
    private ResolvedOrigin defaultOrigin() {
        return catalog.findByCode(OriginCode.ofHub(OriginHubType.TRAIN_STATION, DEFAULT_ORIGIN_HUB_CODE))
                .map(hub -> new ResolvedOrigin(hub.coordinate(), hub.displayName()))
                .orElseGet(() -> new ResolvedOrigin(DEFAULT_ORIGIN_COORDINATE, DEFAULT_ORIGIN_NAME));
    }
}
