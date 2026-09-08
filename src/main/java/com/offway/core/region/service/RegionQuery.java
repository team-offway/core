package com.offway.core.region.service;

import com.offway.core.region.domain.Region;
import com.offway.core.region.domain.RegionTagType;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.region.repository.RegionTagRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 다른 도메인이 지역을 묻는 <b>유일한 통로</b>(#410).
 *
 * <h2>왜 만들었나</h2>
 *
 * <p>도메인 간 참조 규약은 "소유 도메인의 service/port 를 통해 얻고 저쪽 repository 를 직접 부르지
 * 않는다" 인데, 실제로는 <b>6개 도메인 13곳</b>이 {@code RegionRepository}·{@code RegionTagRepository}
 * 를 직접 주입하고 있었다.
 *
 * <p>그래서 <b>지역 조회 방식을 바꾸려면 13곳을 동시에 열어야 했다.</b> 지역 마스터에 캐시를 얹거나
 * {@code findByIds} 의 N+1 을 막으려 할 때, 그 결정이 region 도메인 안에서 끝나지 않는다.
 *
 * <h2>경계 — {@link RegionMaster} 와 무엇이 다른가</h2>
 *
 * <p>{@code RegionMaster} 는 <b>"누가 누구에게 가까운가"</b> 라는 지리적 사실만 소유한다(그쪽 javadoc
 * 이 그렇게 못박고 있다). 부팅 시 데운 스냅숏이라 조회 의미도 다르다 — 여기는 <b>지금 DB 에 있는
 * 것</b>을 답한다.
 *
 * <p>둘을 합치지 않은 이유다. 배치가 지역을 훑을 때 데운 값이 아니라 지금 값을 봐야 하고, 반대로
 * 추천이 이웃을 물을 때마다 DB 를 칠 이유가 없다.
 *
 * <h2>여기 없는 것</h2>
 *
 * <p><b>지금 쓰이는 조회만 연다.</b> 저장소가 가진 것을 그대로 옮기면 이 통로가 곧 저장소의 별명이
 * 되고, 그러면 애초에 만든 이유가 사라진다.
 */
@Component
@RequiredArgsConstructor
public class RegionQuery {

    private final RegionRepository regionRepository;
    private final RegionTagRepository regionTagRepository;

    /**
     * 지역 하나. 없으면 빈 값이다.
     *
     * <p>네 곳이 {@code findByIds(List.of(id))} 뒤에 {@code findFirst()} 를 각자 붙이고 있었다 —
     * 같은 뜻을 네 벌로 쓰면 한 곳만 {@code orElse(null)} 로 새는 식으로 갈린다.
     */
    public Optional<Region> byId(long regionId) {
        return regionRepository.findByIds(List.of(regionId)).stream().findFirst();
    }

    /** 여러 지역. 순서는 저장소가 준 순서 그대로다. */
    public List<Region> byIds(List<Long> regionIds) {
        return regionRepository.findByIds(regionIds);
    }

    /** 인구감소지역 전부. 배치가 89곳을 훑을 때 쓴다. */
    public List<Region> all() {
        return regionRepository.findAll();
    }

    /** 지역 수 — 시드가 들어왔는지 보는 자리(인벤토리 점검). */
    public long count() {
        return regionRepository.count();
    }

    /** 이 태그가 붙은 지역 id. 정책의 대상 지역이 여기서 나온다. */
    public List<Long> idsWithTag(RegionTagType tag) {
        return regionTagRepository.findRegionIdsByTag(tag);
    }

    /** 한 지역의 태그 — 그 지역에 어떤 혜택이 붙는지의 근거다. */
    public List<RegionTagType> tagsOf(long regionId) {
        return regionTagRepository.findTagsByRegionId(regionId);
    }

    /** 여러 지역의 태그를 한 번에. 지역마다 물으면 목록 길이만큼 쿼리가 는다. */
    public Map<Long, Set<RegionTagType>> tagsOf(List<Long> regionIds) {
        return regionTagRepository.findTagsByRegionIds(regionIds);
    }
}
