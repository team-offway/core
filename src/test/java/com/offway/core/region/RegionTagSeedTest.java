package com.offway.core.region;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.offway.core.region.domain.RegionTagType;
import com.offway.core.region.repository.RegionTagRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RegionTagSeedTest {

    @Autowired
    private RegionTagRepository regionTagRepository;

    @Test
    void 인구감소지역_태그가_89개_지역에_시딩된다() {
        assertEquals(89, regionTagRepository.countByTag(RegionTagType.POPULATION_DECLINE));
    }

    /**
     * 디지털관광주민증은 <b>89곳이 아니라 52곳</b>이다(#498).
     *
     * <p>참여하지 않는 37곳에서는 발급받아도 쓸 데가 없다. 이 수가 89로 돌아가면 그 37곳에 거짓
     * 뱃지가 나가고, 화면에는 "혜택 있음" 으로만 보여 알아채기 어렵다.
     *
     * <p>명단은 해마다 바뀌므로 <b>이 숫자도 바뀔 수 있다.</b> 다만 바뀔 때는 시드와 함께 의식적으로
     * 고쳐야 한다 — 조용히 어긋나는 것을 막는 것이 이 단언의 목적이다.
     */
    @Test
    void 디지털관광주민증_태그가_참여_52곳에만_시딩된다() {
        assertEquals(52, regionTagRepository.countByTag(RegionTagType.DIGITAL_TOURIST_CARD));
    }

    @Test
    void 인구감소지역_태그로_89개_지역ID를_역조회한다() {
        assertEquals(89, regionTagRepository.findRegionIdsByTag(RegionTagType.POPULATION_DECLINE).size());
    }
}
