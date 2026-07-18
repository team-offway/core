package com.offway.core.region;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RegionSeedTest {

    @Autowired
    private RegionRepository regionRepository;

    @Test
    void 인구감소지역_89개가_시딩된다() {
        assertEquals(89, regionRepository.count());
    }
}
