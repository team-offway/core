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

    @Test
    void 인구감소지역_태그로_89개_지역ID를_역조회한다() {
        assertEquals(89, regionTagRepository.findRegionIdsByTag(RegionTagType.POPULATION_DECLINE).size());
    }
}
