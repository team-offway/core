package com.offway.core.trip.service;

import com.offway.core.trip.domain.TransitHubPhoto;
import com.offway.core.trip.repository.TransitHubPhotoRepository;
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

    /** 지점명 → 사진 주소. 아직 안 받았거나 갤러리에 없는 지점은 결과에서 빠진다. */
    @Transactional(readOnly = true)
    public Map<String, String> photoUrls(Set<String> hubNames) {
        if (hubNames.isEmpty()) {
            return Map.of();
        }
        return transitHubPhotoRepository.findByHubNames(hubNames).stream()
                .filter(photo -> photo.usableImageUrl().isPresent())
                .collect(Collectors.toMap(
                        TransitHubPhoto::getHubName,
                        photo -> photo.usableImageUrl().orElseThrow(),
                        (first, second) -> first));
    }
}
