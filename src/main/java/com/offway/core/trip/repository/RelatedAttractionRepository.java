package com.offway.core.trip.repository;

import com.offway.core.trip.domain.RelatedAttraction;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 연관 관광지 저장소 port(#186). 도메인·서비스는 이 인터페이스에만 의존한다. */
public interface RelatedAttractionRepository {

    /**
     * 그 중심 관광지와 함께 가는 곳 — <b>순위 낮은 것부터</b>.
     *
     * @param categoryLarge 관광지·음식·숙박. 어느 슬롯을 채우느냐에 따라 갈린다
     */
    List<RelatedAttraction> findByHub(long regionId, String hubCode, String categoryLarge, int limit);

    /** 그 지역에 쌓인 연관 관광지 전부 — 커버리지를 재거나 코스가 한 번에 훑을 때. */
    List<RelatedAttraction> findByRegion(long regionId);

    /**
     * 그 지역이 이미 이 달(또는 그 이후) 자료를 갖고 있는가.
     *
     * <p>지역별로 판정한다 — 89곳을 한 덩어리로 물으면 발행 시점이 지역마다 달라 그 조건이 사실상
     * 참이 되지 않고, 부팅마다 89회를 쏜다(#185 가 같은 실수를 했다).
     */
    Optional<YearMonth> latestBaseMonth(long regionId);

    /** 그 지역·그 달 것을 통째로 갈아 끼운다. 원본이 월 단위로 다시 발행되므로 부분 갱신이 없다. */
    int replaceRegion(long regionId, YearMonth baseMonth, Collection<RelatedAttraction> rows);

    long count();
}
