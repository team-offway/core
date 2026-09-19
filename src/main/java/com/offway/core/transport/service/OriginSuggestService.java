package com.offway.core.transport.service;

import com.offway.core.transport.domain.OriginCode;
import com.offway.core.transport.domain.OriginHub;
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

    private final OriginHubCatalog catalog;
    private final OriginPlaceSearchClient placeSearchClient;

    /**
     * 검색어에 걸리는 출발지 후보 — 허브 먼저, 남은 자리를 주소로.
     *
     * <p>검색어가 짧으면 빈 목록이다(정상 결과 — 화면이 아직 아무것도 안 그린다).
     */
    public List<OriginSuggestion> suggest(String query) {
        List<OriginHub> hubs = catalog.search(query);
        List<OriginSuggestion> suggestions = new ArrayList<>(hubs.stream().map(OriginSuggestion::from).toList());

        int room = OriginHubCatalog.MAX_SUGGESTIONS - suggestions.size();
        if (room <= 0 || query == null || query.isBlank()) {
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
}
