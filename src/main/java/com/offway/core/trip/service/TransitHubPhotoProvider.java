package com.offway.core.trip.service;

import com.offway.core.transport.domain.TransitMode;
import com.offway.core.trip.domain.TransitHubPhoto;
import com.offway.core.trip.repository.TransitHubPhotoRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 코스 응답이 쓰는 교통 거점 사진(#450) — <b>DB 만 읽는다</b>.
 *
 * <p>받아 두는 것은 {@link TransitHubPhotoRefreshService} 가 배치로 한다. 여기서 외부를 부르면 코스 응답이
 * 갤러리 응답시간을 뒤집어쓴다.
 */
@Service
@RequiredArgsConstructor
public class TransitHubPhotoProvider {

    private final TransitHubPhotoRepository transitHubPhotoRepository;

    /**
     * 지점명 → 사진 주소. 아직 안 받았거나 갤러리에 없는 지점은 결과에서 빠진다.
     *
     * <p><b>받은 이름과 종류를 뗀 이름을 함께 찾는다</b>(#535). 사진은 마스터 이름(`강릉`)으로 받아 두는데
     * 코스 슬롯 제목은 종류가 붙은 이름(`강릉역`)이다(#529) — 받은 이름으로만 찾으면 있는 사진을 못 찾는다.
     *
     * <p>둘 다 찾는 이유는 {@code 동서울터미널}처럼 <b>원본에 이미 종류가 든</b> 이름이 있어서다. 그건
     * 떼면 오히려 어긋나므로, 받은 이름을 먼저 보고 없을 때만 뗀 이름을 쓴다.
     *
     * @return 키는 <b>물어본 이름 그대로</b>다 — 부르는 쪽이 슬롯 제목으로 다시 찾을 수 있어야 한다
     */
    @Transactional(readOnly = true)
    public Map<String, String> photoUrls(Set<String> hubNames) {
        if (hubNames.isEmpty()) {
            return Map.of();
        }
        Set<String> lookup = new LinkedHashSet<>(hubNames);
        hubNames.forEach(name -> lookup.add(TransitMode.rawPlaceName(name)));

        Map<String, String> byStoredName = transitHubPhotoRepository.findByHubNames(lookup).stream()
                .filter(photo -> photo.usableImageUrl().isPresent())
                .collect(Collectors.toMap(
                        TransitHubPhoto::getHubName,
                        photo -> photo.usableImageUrl().orElseThrow(),
                        (first, second) -> first));

        Map<String, String> byRequested = new LinkedHashMap<>();
        for (String name : hubNames) {
            String url = byStoredName.get(name);
            if (url == null) {
                url = byStoredName.get(TransitMode.rawPlaceName(name));
            }
            if (url != null) {
                byRequested.put(name, url);
            }
        }
        return Map.copyOf(byRequested);
    }
}
