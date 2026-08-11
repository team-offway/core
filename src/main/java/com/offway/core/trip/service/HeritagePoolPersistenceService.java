package com.offway.core.trip.service;

import com.offway.core.trip.domain.HeritagePlace;
import com.offway.core.trip.repository.HeritagePlaceRepository;
import com.offway.core.trip.repository.HeritagePoolSourceRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 국가유산 풀 적재의 트랜잭션 경계(#160).
 *
 * <p>경계를 로더가 아니라 여기에 두는 이유는 장소 풀과 같다 — 로더가 예외를 잡으므로, 경계가 로더 안에 있으면
 * 잡는 순간 그때까지 넣은 것이 커밋된다. 실제로 그렇게 89곳 중 42곳만 실린 채 배포된 적이 있다(#144).
 */
@Service
@RequiredArgsConstructor
public class HeritagePoolPersistenceService {

    private final HeritagePlaceRepository heritagePlaceRepository;
    private final HeritagePoolSourceRepository heritagePoolSourceRepository;

    @Transactional
    public int replaceAll(List<HeritagePlace> places, String checksum) {
        heritagePlaceRepository.deleteAll();
        int inserted = heritagePlaceRepository.saveAll(places);
        if (inserted != places.size()) {
            throw new IllegalStateException(
                    "국가유산 풀이 일부만 적재됐습니다. 기대=" + places.size() + " 실제=" + inserted);
        }
        heritagePoolSourceRepository.record(checksum, inserted);
        return inserted;
    }

    @Transactional(readOnly = true)
    public Optional<String> loadedChecksum() {
        return heritagePoolSourceRepository.findChecksum();
    }

    @Transactional(readOnly = true)
    public long count() {
        return heritagePlaceRepository.count();
    }
}
