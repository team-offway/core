package com.offway.core.trip.infrastructure.pet;

import com.offway.core.trip.infrastructure.pet.dto.PetTourDetail;
import com.offway.core.trip.infrastructure.pet.dto.PetTourResult;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@link PetTourClient} 외부 경계 stub — 통합 테스트에서 반려동반 호출을 격리한다.
 *
 * <p>default 는 throw 라 명시 세팅을 빠뜨리면 즉시 깨진다.
 *
 * <p><b>목록과 상세를 따로 받는다.</b> 상세는 장소마다 한 콜이고 <b>일부만 실패하는 회차</b>가 실제
 * 설계의 일부라(칩은 뜨고 상세만 빈다), 그 분기를 테스트가 만들 수 있어야 한다.
 */
public class StubPetTourClient implements PetTourClient {

    private Supplier<PetTourResult> listBehavior = () -> {
        throw new IllegalStateException(
                "StubPetTourClient 미설정 — 테스트가 respondList(...) 로 동작을 지정해야 합니다.");
    };

    private Function<String, Optional<PetTourDetail>> detailBehavior = contentId -> {
        throw new IllegalStateException(
                "StubPetTourClient 미설정 — 테스트가 respondDetail(...) 로 동작을 지정해야 합니다.");
    };

    public void respondList(Supplier<PetTourResult> behavior) {
        this.listBehavior = behavior;
    }

    public void respondDetail(Function<String, Optional<PetTourDetail>> behavior) {
        this.detailBehavior = behavior;
    }

    @Override
    public PetTourResult findAll(Duration maxWait) {
        return listBehavior.get();
    }

    @Override
    public Optional<PetTourDetail> findDetail(String contentId, Duration maxWait) {
        return detailBehavior.apply(contentId);
    }
}
