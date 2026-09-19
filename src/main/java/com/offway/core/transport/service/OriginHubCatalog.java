package com.offway.core.transport.service;

import com.offway.core.transport.domain.BusTerminal;
import com.offway.core.transport.domain.OriginCode;
import com.offway.core.transport.domain.OriginHub;
import com.offway.core.transport.domain.SearchableName;
import com.offway.core.transport.domain.TrainStation;
import com.offway.core.transport.repository.BusTerminalRepository;
import com.offway.core.transport.repository.TrainStationRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 출발지로 고를 수 있는 역·터미널 목록 — 검색과 코드 해석을 함께 소유한다(#590).
 *
 * <p><b>왜 인메모리인가.</b> 역 343곳 + 터미널 789곳으로 끝이고 느리게 변한다. 자동완성은 글자마다
 * 부르는 화면이라 요청당 SQL 을 치면 그 부담이 타이핑 수만큼 곱해진다 — 같은 판단으로
 * {@link BusTerminalResolver}·{@link TrainStationResolver} 도 전체를 들고 최근접을 계산한다.
 *
 * <p><b>키 공간에 상한이 있다.</b> 시드가 정한 유한 집합이라 캐시가 자라지 않는다(캐시 키 상한을 먼저
 * 정하라는 규칙 그대로).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OriginHubCatalog {

    /**
     * 한 번에 내리는 제안 수 상한.
     *
     * <p>"서울" 한 글자로 36곳이 걸리고 "시" 처럼 짧은 말은 수백 곳이 걸린다. 자동완성 목록은 사용자가
     * 훑어보는 자리라 전부 내리면 화면이 넘치고 응답도 그만큼 커진다.
     */
    public static final int MAX_SUGGESTIONS = 20;

    /**
     * 검색을 시작하는 최소 글자 수.
     *
     * <p>한 글자면 "서" 하나에 서울·서산·서천·서광주가 전부 걸려 목록이 뜻을 잃는다. 한국어 지명은 두
     * 글자가 사실상의 최소 단위다.
     */
    public static final int MIN_QUERY_LENGTH = 2;

    private final BusTerminalRepository terminalRepository;
    private final TrainStationRepository stationRepository;

    /** 시드에서 만든 제안 목록 — 중복을 접은 결과. */
    private volatile List<OriginHub> cache;

    /**
     * 이 검색어로 찾을 만한가 — 허브와 주소 검색이 <b>같은 기준</b>을 쓴다.
     *
     * <p>여기서 갈라지면 짧은 검색어가 허브는 못 찾으면서 외부는 부르는 상태가 된다.
     */
    public static boolean isSearchable(String query) {
        if (query == null) {
            return false;
        }
        SearchableName normalized = SearchableName.of(query.trim());
        return !normalized.isBlank() && normalized.value().length() >= MIN_QUERY_LENGTH;
    }

    /**
     * 검색어에 걸리는 허브를 순위대로.
     *
     * <p>정렬 순서와 그 이유:
     *
     * <ol>
     *   <li><b>이름이 검색어로 시작하는 것 먼저.</b> "서울" 은 서울의 허브 전부에 걸리는데, 첫 줄에서
     *       기대하는 것은 서울역이다. 이름 안쪽에 든 동서울터미널이나 원본 이름으로 걸린 고속버스터미널은
     *       그 뒤다
     *   <li><b>별칭이 붙은 허브를 위로.</b> 부르는 이름이 따로 있는 곳이 그 지역의 대표 허브다
     *   <li><b>짧은 이름을 먼저.</b> 검색어가 이름의 더 많은 부분을 덮는다는 뜻이다
     *   <li><b>표시 이름 사전순.</b> 남은 순서를 우연(DB 순서)에 맡기지 않는다
     * </ol>
     *
     * <p>올릴 수 없는 허브는 애초에 목록에 없다 — 좌표 없음·경유 정류소·간선 열차 미정차. 그 판정은
     * 엔티티의 {@code toOriginHub} 가 소유한다.
     *
     * <p>검색어가 짧으면 <b>빈 목록</b>이다. 그건 정상 결과다 — 화면은 아직 아무것도 안 그린다.
     */
    public List<OriginHub> search(String query) {
        if (!isSearchable(query)) {
            return List.of();
        }
        SearchableName normalized = SearchableName.of(query.trim());
        return hubs().stream()
                .flatMap(hub -> hub.match(normalized).stream().map(match -> Map.entry(hub, match)))
                .sorted(Comparator
                        .<Map.Entry<OriginHub, OriginHub.Match>, OriginHub.Match>comparing(Map.Entry::getValue)
                        .thenComparing(entry -> !entry.getKey().curated())
                        // **짧은 이름을 먼저.** 검색어가 이름의 더 많은 부분을 덮는다는 뜻이라, 그쪽이
                        // 그 지역의 대표 표기다. 사전순만으로 정하면 "서울" 의 첫 줄이 서울남부터미널이
                        // 된다 — 가나다순으로 '남' 이 '역' 보다 앞이기 때문이고, 그건 판단이 아니라 우연이다.
                        .thenComparing(entry -> entry.getKey().displayName().length())
                        .thenComparing(entry -> entry.getKey().displayName()))
                .limit(MAX_SUGGESTIONS)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 앱이 되돌려 보낸 코드가 가리키는 허브.
     *
     * <h2>제안보다 넉넉하게 받는다</h2>
     *
     * <b>필터는 "무엇을 추천하나" 의 규칙이고, 받아들이는 것은 더 넉넉해야 한다.</b> 그래서 여기서는
     * 접힌 제안 목록이 아니라 <b>마스터 전체</b>에서 찾고, 좌표만 요구한다.
     *
     * <p>이유가 둘이다.
     *
     * <ul>
     *   <li><b>대표 코드가 바뀔 수 있다.</b> 같은 지점에 코드가 여럿일 때 하나만 내리는데(동서울 5개 →
     *       {@code NAEK030}), 그 선택은 별칭·이름 길이·코드 순으로 정해진다. 나중에 별칭을 하나 더하면
     *       대표가 옮겨가고, 앱이 저장해 둔 옛 코드가 못 풀린다 — <b>우리 잘못인데 사용자가 오류를 본다</b>
     *   <li><b>필터가 나중에 는다.</b> 이번에 기차역 119곳을 목록에서 뺐다(통근 전용). 그 전에 고른
     *       출발지를 앱이 저장해 뒀다면 그것도 못 풀린다
     * </ul>
     *
     * <p>통근 전용 역 코드가 살아 있으면 그 좌표로 코스를 짜고, 열차가 없으면 버스·자차로 degrade
     * 한다 — GPS 를 쓰던 때와 같은 동작이다. 목록에서 빼는 것은 <b>새로 고르는 사람</b>에게 더 나은
     * 후보를 주려는 것이고, 이미 고른 사람의 요청을 깨뜨릴 이유는 아니다.
     *
     * <p><b>그래도 비어 있을 수 있다</b> — 마스터에 없는 코드(오타·폐역)거나 좌표가 틀린 것으로 확인돼
     * 비운 역(신림·대야·상동·진성)이다. 그때는 부재를 그대로 알려 부르는 쪽이 거절하게 한다. 조용히
     * 기본 출발지로 바꾸면 사용자는 엉뚱한 곳에서 출발하는 코스를 받는다.
     */
    public Optional<OriginHub> findByCode(OriginCode code) {
        if (code == null || code.isCoordinate()) {
            return Optional.empty();
        }
        return code.hubType().flatMap(type -> switch (type) {
            case TRAIN_STATION -> stationRepository.findAll().stream()
                    .filter(station -> station.getCode().equals(code.hubCode()))
                    .findFirst()
                    .flatMap(TrainStation::toAnyOriginHub);
            case BUS_TERMINAL -> terminalRepository.findAll().stream()
                    .filter(terminal -> terminal.getCode().equals(code.hubCode()))
                    .findFirst()
                    .flatMap(BusTerminal::toAnyOriginHub);
        });
    }

    /** 캐시 무효화 — 시드 갱신·통합 테스트 격리용. */
    public void evictCache() {
        cache = null;
    }

    private List<OriginHub> hubs() {
        List<OriginHub> local = cache;
        if (local == null) {
            local = build();
            cache = local;
            log.info("출발지 허브 적재 — {}곳(중복 접은 뒤)", local.size());
        }
        return local;
    }

    /**
     * 시드에서 제안 목록을 만든다 — 올릴 수 없는 것을 빼고, 같은 지점을 하나로 접는다.
     *
     * <p>접는 이유는 TAGO 가 같은 터미널에 노선별 코드를 따로 주기 때문이다 — 동서울이 5건, 서울 고속
     * 버스터미널 경부선 쪽이 이름 둘로 온다. 그대로 내리면 같은 곳이 목록에 다섯 줄 뜬다.
     */
    private List<OriginHub> build() {
        Map<String, OriginHub> byPlace = new LinkedHashMap<>();
        stationRepository.findAll().stream()
                .flatMap(station -> station.toOriginHub().stream())
                .forEach(hub -> byPlace.merge(hub.dedupeKey(), hub, OriginHub::preferOver));
        terminalRepository.findAll().stream()
                .flatMap(terminal -> terminal.toOriginHub().stream())
                .forEach(hub -> byPlace.merge(hub.dedupeKey(), hub, OriginHub::preferOver));
        return List.copyOf(byPlace.values());
    }
}
