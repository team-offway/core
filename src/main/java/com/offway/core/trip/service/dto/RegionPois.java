package com.offway.core.trip.service.dto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 한 지역의 코스 후보 POI 를 세 풀로 분류한 결과(course-logic ①: 볼거리풀·맛집풀·숙박풀). itinerary 가 이 풀에서 필요 수만큼
 * 골라 슬롯에 배치한다.
 *
 * <p>풀이 넉넉한지 스스로 판단하고, 부족하면 다른 소스(인허가 데이터)와의 병합을 스스로 표현한다(rich domain).
 * 서비스는 조율만 한다.
 *
 * @param sights 볼거리(관광지·문화·축제·레포츠)
 * @param foods 맛집(음식점)
 * @param stays 숙박
 */
public record RegionPois(List<PoiCandidate> sights, List<PoiCandidate> foods, List<PoiCandidate> stays) {

    /**
     * 각 풀이 최소한 갖춰야 할 후보 수 — 가장 긴 코스(2박3일 빡빡)가 요구하는 양이다.
     *
     * <p>볼거리 6개/일 × 3일, 끼니 2회/일 × 3일, 2박. 이보다 적으면 슬롯이 조용히 빈 채로 코스가 나간다.
     */
    private static final int MIN_SIGHTS = 18;
    private static final int MIN_FOODS = 6;
    private static final int MIN_STAYS = 2;

    /**
     * 같은 장소로 볼 좌표 격자(도 단위, 약 100m).
     *
     * <p>TourAPI 와 인허가 데이터는 같은 건물에도 좌표를 조금 다르게 적는다. 정확히 비교하면 중복이 그대로
     * 남고, 너무 크게 뭉개면 이웃한 다른 가게가 한 곳으로 접힌다.
     */
    private static final double GRID_DEGREES = 0.001;

    public RegionPois {
        sights = List.copyOf(sights);
        foods = List.copyOf(foods);
        stays = List.copyOf(stays);
    }

    public static RegionPois empty() {
        return new RegionPois(List.of(), List.of(), List.of());
    }

    /** 어느 풀이라도 가장 긴 코스를 못 채우는가. */
    public boolean needsSupplement() {
        return needsMoreSights() || needsMoreFoods() || needsMoreStays();
    }

    /** 볼거리가 모자란가 — 모자란 풀만 보충 후보를 조회하기 위한 판정이다. */
    public boolean needsMoreSights() {
        return sights.size() < MIN_SIGHTS;
    }

    /** 맛집이 모자란가. */
    public boolean needsMoreFoods() {
        return foods.size() < MIN_FOODS;
    }

    /** 숙소가 모자란가. */
    public boolean needsMoreStays() {
        return stays.size() < MIN_STAYS;
    }

    /**
     * 부족한 풀만 다른 소스의 후보로 채운다(#144).
     *
     * <p><b>기존 후보를 앞에 둔다</b> — 1순위 소스(TourAPI)에는 사진·소개가 붙어 있어 화면 품질이 높다. 보충 후보는
     * 뒤에 붙어 모자란 만큼만 쓰인다. 넉넉한 풀은 손대지 않으므로, 후보가 충분한 지역은 지금 결과 그대로다.
     *
     * <p>같은 장소가 두 소스에 다 있으면 코스에 두 번 뜨므로 상호로 걸러낸다.
     */
    public RegionPois supplementedWith(
            List<PoiCandidate> moreSights, List<PoiCandidate> moreFoods, List<PoiCandidate> moreStays) {
        return new RegionPois(
                merge(sights, moreSights, MIN_SIGHTS),
                merge(foods, moreFoods, MIN_FOODS),
                merge(stays, moreStays, MIN_STAYS));
    }

    private static List<PoiCandidate> merge(List<PoiCandidate> base, List<PoiCandidate> extra, int minimum) {
        if (base.size() >= minimum || extra.isEmpty()) {
            return base;
        }
        Set<String> seen = new HashSet<>();
        for (PoiCandidate candidate : base) {
            seen.add(identity(candidate));
        }
        List<PoiCandidate> merged = new ArrayList<>(base);
        for (PoiCandidate candidate : extra) {
            if (seen.add(identity(candidate))) {
                merged.add(candidate);
            }
        }
        return merged;
    }

    /**
     * 같은 장소인지 가르는 열쇠 — 상호에 <b>위치를 함께</b> 본다.
     *
     * <p>상호만 보면 "○○식당" 본점과 2호점이 한 곳으로 접혀 풀이 덜 채워진다. 반대로 좌표만 보면 소스마다
     * 소수점 정밀도가 달라 같은 곳을 다른 곳으로 센다. 좌표를 약 100m 격자로 뭉개 둘을 함께 쓴다.
     */
    private static String identity(PoiCandidate candidate) {
        return normalize(candidate.title())
                + "@" + Math.round(candidate.lat() / GRID_DEGREES)
                + "," + Math.round(candidate.lng() / GRID_DEGREES);
    }

    /** 소스마다 띄어쓰기·대소문자가 달라 그대로 비교하면 같은 곳을 다른 곳으로 본다. */
    private static String normalize(String title) {
        return title == null ? "" : title.replaceAll("\\s+", "").toLowerCase();
    }
}
