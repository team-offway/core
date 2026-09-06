package com.offway.core.trip.service;

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
public class TransitHubPhotoRefreshService {

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

    /** 이 기간이 지난 지점을 다시 묻는다 — 갤러리는 월 단위로 늘어나는 자료다. */
    private static final Duration REFETCH_AFTER = Duration.ofDays(30);

    private final RegionQuery regionQuery;
    private final TrainStationResolver trainStationResolver;
    private final BusTerminalResolver busTerminalResolver;
    private final FerryPortResolver ferryPortResolver;
    private final GalleryPhotoClient galleryPhotoClient;
    private final TransitHubPhotoRepository transitHubPhotoRepository;

    @Scheduled(cron = WEEKLY_AT_DAWN, zone = SERVICE_ZONE_ID)
    public void refresh() {
        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        Set<String> hubNames = destinationHubNames();
        if (hubNames.isEmpty()) {
            log.info("교통 거점 사진 — 대상 지점이 없습니다(시드를 확인하세요)");
            return;
        }
        Map<String, TransitHubPhoto> known = transitHubPhotoRepository.findByHubNames(hubNames).stream()
                .collect(Collectors.toMap(TransitHubPhoto::getHubName, Function.identity()));

        LocalDateTime staleBefore = now.minus(REFETCH_AFTER);
        List<String> targets = hubNames.stream()
                .filter(name -> {
                    TransitHubPhoto existing = known.get(name);
                    return existing == null || existing.getFetchedAt().isBefore(staleBefore);
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
            Optional<GalleryPhotoItem> photo = search(hubName);
            record(known.get(hubName), hubName, photo.orElse(null), now);
            if (photo.isPresent()) {
                found++;
            } else {
                missing++;
            }
        }
        // 0건이어도 남긴다 — 배치가 돌았는지, 왜 0건인지 답할 수 있어야 한다(#310).
        log.info("교통 거점 사진 갱신 — 대상 {}곳 중 {}곳 조회: 확보 {} · 없음 {}",
                hubNames.size(), targets.size(), found, missing);
    }

    /**
     * 89곳이 쓰는 도착 지점의 이름 — <b>대표 선정과 같은 규칙</b>으로 푼다.
     *
     * <p>이름으로 모으는 이유는 같은 지점이 고속·시외 목록에 다른 코드로 올라 있기 때문이다. 코드로 잡으면
     * 같은 사진을 두 번 받는다.
     */
    private Set<String> destinationHubNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Region region : regionQuery.all()) {
            double lat = region.getLat();
            double lng = region.getLng();
            trainStationResolver.nearest(lat, lng).map(Station::name).ifPresent(names::add);
            for (BusTerminalKind kind : BusTerminalKind.values()) {
                busTerminalResolver.nearest(lat, lng, kind).map(Terminal::name).ifPresent(names::add);
            }
            ferryPortResolver.nearest(lat, lng).map(Port::name).ifPresent(names::add);
        }
        names.removeIf(name -> name == null || name.isBlank());
        return names;
    }

    private Optional<GalleryPhotoItem> search(String hubName) {
        return galleryPhotoClient.searchByKeyword(hubName, ROWS_PER_HUB).stream()
                .filter(GalleryPhotoItem::isComplete)
                .findFirst();
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
}
