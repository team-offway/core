package com.offway.core.trip.service;

import com.offway.core.common.batch.domain.ManualBatch;
import com.offway.core.region.domain.Region;
import com.offway.core.region.service.RegionQuery;
import com.offway.core.transport.domain.BusTerminalKind;
import com.offway.core.transport.domain.Port;
import com.offway.core.transport.domain.Station;
import com.offway.core.transport.domain.Terminal;
import com.offway.core.transport.service.BusTerminalResolver;
import com.offway.core.transport.service.FerryPortResolver;
import com.offway.core.transport.service.TrainStationResolver;
import com.offway.core.trip.domain.TransitHubPhoto;
import com.offway.core.trip.infrastructure.gallery.GalleryPhotoClient;
import com.offway.core.trip.infrastructure.gallery.dto.GalleryPhotoItem;
import com.offway.core.trip.repository.TransitHubPhotoRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 역·터미널·항구 칸의 사진을 미리 받아 둔다(#450) — 관광사진갤러리.
 *
 * <p><b>왜 미리 받나.</b> 코스는 <b>도착 지역부터</b> 보여주므로 사진이 필요한 지점은 인구감소지역 89곳이
 * 쓰는 것뿐이고, 그 집합은 시드가 정한 유한 집합이다(버스만 110종, 열차역·항구까지 200종 남짓). 요청
 * 경로에서 외부를 부르지 않는다는 규칙 그대로다.
 *
 * <p><b>출발 쪽은 안 받는다.</b> 도착·출발 칸이 둘 다 도착 지점이고(내린 곳에서 다시 탄다), 출발 터미널은
 * 슬롯이 아니라 교통 카드의 {@code fromPlace} 다. 출발지는 사용자마다 달라 유한하지도 않다.
 *
 * <p><b>cron 이다.</b> {@code fixedDelay} 는 프로세스가 살아 있는 동안의 간격이라 재배포하면 주기가
 * 처음부터 다시 센다 — "주 1회" 라고 적어 두고 배포마다 도는 일이 실제로 있었다(#226·#231).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransitHubPhotoRefreshService implements ManualBatch {

    /** 관리자 화면이 마지막 실행 시각을 붙이는 키(#537). batch_run.name 과 같은 값이어야 한다. */
    static final String BATCH_NAME = "transit-hub-photo-refresh";

    /** 매주 화요일 04:40 — 정각·다른 배치와 겹치지 않게 어긋냈다. */
    private static final String WEEKLY_AT_DAWN = "0 40 4 * * TUE";

    private static final String SERVICE_ZONE_ID = "Asia/Seoul";

    private static final ZoneId SERVICE_ZONE = ZoneId.of(SERVICE_ZONE_ID);

    /**
     * 회당 조회 상한 — 관광사진갤러리 한도(1,000/일)를 지킨다.
     *
     * <p>대상이 200종 남짓이라 한 회차에 전량이 돈다. 상한은 시드가 늘어 대상이 커졌을 때를 위한 것이다.
     */
    private static final int MAX_LOOKUPS_PER_RUN = 300;

    /** 한 지점에 몇 장까지 받아 볼지 — 첫 장만 쓰지만 불완전한 것을 걸러낼 여지를 둔다. */
    private static final int ROWS_PER_HUB = 5;

    private static final Pattern PARENTHESES = Pattern.compile("\\((.*?)\\)");

    /** 시설·방위 접미어 — `고양종합`·`대전복합`·`대구북부`·`장성사거리` 를 지역명으로 되돌린다. */
    private static final Pattern FACILITY_SUFFIX =
            Pattern.compile("(종합|복합|공용|사거리|동부|서부|남부|북부|중앙)+$");

    /** 사진을 <b>찾은</b> 지점을 다시 묻기까지 — 갤러리는 월 단위로 늘어나는 자료다. */
    private static final Duration REFETCH_AFTER = Duration.ofDays(30);

    /**
     * 사진을 <b>못 찾은</b> 지점을 다시 묻기까지 — 훨씬 짧게.
     *
     * <p>{@code searchByKeyword} 는 조회 실패와 실제 미검색을 <b>둘 다 빈 결과</b>로 준다. 찾은 것과 같은
     * 30일을 걸면 일시적 장애 한 번이 그 지점을 한 달 내내 사진 없이 만든다 — 빈 응답을 성공 TTL 로
     * 누르지 않는다는 규칙 그대로다(CLAUDE.md §조용한 실패를 만들지 않는다).
     *
     * <p>주간 배치라 다음 회차가 곧 다시 묻는다.
     */
    private static final Duration REFETCH_MISSING_AFTER = Duration.ofDays(3);

    private final RegionQuery regionQuery;
    private final TrainStationResolver trainStationResolver;
    private final BusTerminalResolver busTerminalResolver;
    private final FerryPortResolver ferryPortResolver;
    private final GalleryPhotoClient galleryPhotoClient;
    private final TransitHubPhotoRepository transitHubPhotoRepository;

    @Scheduled(cron = WEEKLY_AT_DAWN, zone = SERVICE_ZONE_ID)
    public void refresh() {
        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        Map<String, String> regionByHub = destinationHubNames();
        Set<String> hubNames = regionByHub.keySet();
        if (hubNames.isEmpty()) {
            log.info("교통 거점 사진 — 대상 지점이 없습니다(시드를 확인하세요)");
            return;
        }
        Map<String, TransitHubPhoto> known = transitHubPhotoRepository.findByHubNames(hubNames).stream()
                .collect(Collectors.toMap(TransitHubPhoto::getHubName, Function.identity()));

        LocalDateTime foundStaleBefore = now.minus(REFETCH_AFTER);
        LocalDateTime missingStaleBefore = now.minus(REFETCH_MISSING_AFTER);
        List<String> targets = hubNames.stream()
                .filter(name -> {
                    TransitHubPhoto existing = known.get(name);
                    if (existing == null) {
                        return true;
                    }
                    // 못 찾은 것은 더 자주 다시 묻는다 — 빈 결과가 장애인지 실제 미검색인지 알 수 없다.
                    LocalDateTime staleBefore =
                            existing.usableImageUrl().isPresent() ? foundStaleBefore : missingStaleBefore;
                    return existing.getFetchedAt().isBefore(staleBefore);
                })
                .limit(MAX_LOOKUPS_PER_RUN)
                .toList();
        if (targets.isEmpty()) {
            log.info("교통 거점 사진 — {}곳 모두 최근에 받아 조회하지 않습니다", hubNames.size());
            return;
        }

        int found = 0;
        int missing = 0;
        for (String hubName : targets) {
            Optional<GalleryPhotoItem> photo = search(hubName, regionByHub.get(hubName));
            record(known.get(hubName), hubName, photo.orElse(null), now);
            if (photo.isPresent()) {
                found++;
            } else {
                missing++;
            }
        }
        // 0건이어도 남긴다 — 배치가 돌았는지, 왜 0건인지 답할 수 있어야 한다(#310).
        // 못 찾은 수를 warn 으로 가른다 — 갤러리가 죽어도 이 배치는 "정상 종료" 로 보이기 때문이다.
        log.info("교통 거점 사진 갱신 — 대상 {}곳 중 {}곳 조회: 확보 {} · 없음 {}",
                hubNames.size(), targets.size(), found, missing);
        if (found == 0 && missing > 0) {
            log.warn("교통 거점 사진 — 조회한 {}곳이 전부 빈 결과입니다. 갤러리 장애일 수 있어 {}일 뒤 다시 묻습니다",
                    missing, REFETCH_MISSING_AFTER.toDays());
        }
    }

    /**
     * 89곳이 쓰는 도착 지점의 이름 — <b>대표 선정과 같은 규칙</b>으로 푼다.
     *
     * <p>이름으로 모으는 이유는 같은 지점이 고속·시외 목록에 다른 코드로 올라 있기 때문이다. 코드로 잡으면
     * 같은 사진을 두 번 받는다.
     */
    private Map<String, String> destinationHubNames() {
        Map<String, String> byHub = new LinkedHashMap<>();
        for (Region region : regionQuery.all()) {
            double lat = region.getLat();
            double lng = region.getLng();
            List<String> hubs = new ArrayList<>();
            trainStationResolver.nearest(lat, lng).map(Station::name).ifPresent(hubs::add);
            for (BusTerminalKind kind : BusTerminalKind.values()) {
                busTerminalResolver.nearest(lat, lng, kind).map(Terminal::name).ifPresent(hubs::add);
            }
            ferryPortResolver.nearest(lat, lng).map(Port::name).ifPresent(hubs::add);
            for (String hub : hubs) {
                if (hub != null && !hub.isBlank()) {
                    // 같은 지점을 여러 지역이 쓰면 먼저 만난 지역으로 둔다 — 폴백 사진의 지역일 뿐이다.
                    byHub.putIfAbsent(hub, region.shortName());
                }
            }
        }
        return byHub;
    }

    /**
     * 지점 사진 — <b>구체적인 검색어부터</b> 훑고 첫 결과를 쓴다.
     *
     * <p>지점명을 그대로 넣으면 68%만 나온다(158종 중 108종, 실측). 못 찾는 것들은 대부분 이름 자체가
     * 검색어로 안 맞는 경우다 — `광주(유·스퀘어)` 의 괄호, `부산_영도` 의 밑줄, `고양종합`·`대전복합` 의
     * 시설 접미어.
     *
     * <p><b>마지막은 지역명이다.</b> `점촌`·`탄현` 처럼 이름을 아무리 다듬어도 안 나오는 지점이 있는데,
     * 그 지점을 쓰는 지역으로 물으면 나온다(문경 812건 · 파주 877건). 89곳 전부 지역명으로는 사진이
     * 있으므로 여기서 멈춘다. 터미널 카드에 그 지역 사진이 붙는 것은 빈 칸보다 낫다.
     */
    private Optional<GalleryPhotoItem> search(String hubName, String regionName) {
        for (String keyword : keywords(hubName, regionName)) {
            Optional<GalleryPhotoItem> found = galleryPhotoClient.searchByKeyword(keyword, ROWS_PER_HUB).stream()
                    .filter(GalleryPhotoItem::isComplete)
                    .findFirst();
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /** 구체적인 것부터. 중복은 접고, 지역명을 맨 뒤에 둔다. */
    private static List<String> keywords(String hubName, String regionName) {
        List<String> keywords = new ArrayList<>();
        add(keywords, hubName);
        // `부산서부(사상)` 의 '사상' 처럼 괄호 안이 더 구체적인 경우가 있다 — 괄호를 떼기 전에 본다.
        Matcher inner = PARENTHESES.matcher(hubName);
        if (inner.find()) {
            add(keywords, inner.group(1));
        }
        String withoutParentheses = PARENTHESES.matcher(hubName).replaceAll("");
        add(keywords, withoutParentheses);
        // `부산_영도` — 시드가 지역을 밑줄로 붙여 둔 항구들이다.
        add(keywords, withoutParentheses.replace('_', ' ').replace('-', ' '));
        // `고양종합`·`대전복합`·`대구북부`·`장성사거리` — 시설·방위 접미어를 뗀다.
        add(keywords, FACILITY_SUFFIX.matcher(withoutParentheses).replaceAll(""));
        add(keywords, regionName);
        return keywords;
    }

    private static void add(List<String> keywords, String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (!trimmed.isBlank() && !keywords.contains(trimmed)) {
            keywords.add(trimmed);
        }
    }

    /**
     * 결과를 적는다 — <b>못 찾은 것도</b>.
     *
     * <p>행이 없는 것은 "아직 안 물어봄" 이고 {@code imageUrl} 이 비어 있는 것은 "물어봤는데 없다" 다.
     * 뭉치면 매 회차가 같은 지점을 다시 묻는다.
     */
    private void record(TransitHubPhoto existing, String hubName, GalleryPhotoItem photo, LocalDateTime now) {
        if (existing != null) {
            existing.refresh(
                    photo == null ? null : photo.imageUrl(),
                    photo == null ? null : photo.photographer(),
                    photo == null ? null : photo.title(),
                    now);
            transitHubPhotoRepository.save(existing);
            return;
        }
        transitHubPhotoRepository.save(photo == null
                ? TransitHubPhoto.missing(hubName, now)
                : TransitHubPhoto.found(hubName, photo.imageUrl(), photo.photographer(), photo.title(), now));
    }

    @Override
    public String batchName() {
        return BATCH_NAME;
    }

    /** 손으로 돌린다(#537) — 배치 자신의 가드는 그대로 탄다. */
    @Override
    public void runNow() {
        refresh();
    }
}
