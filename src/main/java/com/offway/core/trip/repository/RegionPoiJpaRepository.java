package com.offway.core.trip.repository;

import com.offway.core.trip.domain.RegionPoi;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
interface RegionPoiJpaRepository extends JpaRepository<RegionPoi, Long> {

    /**
     * 이 지역의 <b>사진 있는</b> 장소 — "매력 포인트 장소" 가 그대로 쓴다.
     *
     * <p>사진 없는 것을 걸러 오는 이유는 화면이다. 섞이면 가로 목록 중간에 회색 판이 낀다. 앱에서 거르면
     * "10개를 달라" 고 했는데 3개만 그려지는 일이 생기므로 <b>세는 쪽과 거르는 쪽이 같아야 한다.</b>
     */
    @Query("""
            SELECT p FROM RegionPoi p
            WHERE p.regionId = :regionId AND p.imageUrl IS NOT NULL AND p.imageUrl <> ''
            ORDER BY p.id ASC
            """)
    List<RegionPoi> findShowable(@Param("regionId") long regionId, Limit limit);

    /**
     * 여러 지역에서 칩마다 앞의 몇 건씩 — 홈 장소 카드(#305).
     *
     * <p><b>네이티브 쿼리다.</b> 칩별 상위 N 은 윈도우 함수({@code ROW_NUMBER}) 없이는 표현할 수 없고
     * JPQL 은 그것을 모른다. 지역마다 따로 부르면 지역 수만큼 왕복이 생긴다.
     *
     * <p><b>컬럼을 명시한다.</b> {@code rp.*} 로 두면 바깥 SELECT 에 순번 컬럼이 섞여 엔티티 매핑이
     * 무엇을 읽을지 애매해진다. 컬럼이 늘면 여기도 함께 고쳐야 하는 대가는 있지만, 조용히 어긋나는
     * 것보다 컴파일·실행에서 바로 드러나는 편이 낫다.
     */
    @Query(value = """
            SELECT id, region_id, content_id, content_type_id, category, title, image_url,
                   address, lat, lng, tel, base_ym, fetched_at
            FROM (
                SELECT rp.id, rp.region_id, rp.content_id, rp.content_type_id, rp.category, rp.title,
                       rp.image_url, rp.address, rp.lat, rp.lng, rp.tel, rp.base_ym, rp.fetched_at,
                       ROW_NUMBER() OVER (PARTITION BY rp.region_id, rp.category ORDER BY rp.id) AS rank_in_chip
                FROM region_poi rp
                WHERE rp.region_id IN (:regionIds)
                  AND rp.image_url IS NOT NULL AND rp.image_url <> ''
            ) ranked
            WHERE rank_in_chip <= :perCategory
            ORDER BY region_id, category, id
            """, nativeQuery = true)
    List<RegionPoi> findForCards(@Param("regionIds") List<Long> regionIds,
            @Param("perCategory") int perCategory);

    /** 그 달치가 이미 적재된 지역인지 — 있으면 외부를 아예 안 부른다. */
    @Query("SELECT COUNT(p) > 0 FROM RegionPoi p WHERE p.regionId = :regionId AND p.baseYm = :baseYm")
    boolean existsFresh(@Param("regionId") long regionId, @Param("baseYm") String baseYm);

    /**
     * 이 지역의 장소를 통째로 지운다 — 갱신은 <b>지우고 다시 넣는다</b>.
     *
     * <p>합치지 않는 이유는 <b>사라진 장소</b>다. TourAPI 에서 빠진 곳을 남겨 두면 화면에는 계속 뜨는데
     * 눌러 들어가면 상세가 없다. 지역 단위 교체라 그 자리에서 정리된다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RegionPoi p WHERE p.regionId = :regionId")
    int deleteByRegionId(@Param("regionId") long regionId);
}
