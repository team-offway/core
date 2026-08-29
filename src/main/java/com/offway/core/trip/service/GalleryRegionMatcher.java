package com.offway.core.trip.service;

import com.offway.core.region.domain.Region;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 갤러리 사진의 <b>촬영 위치 원문</b>을 우리 89곳에 붙인다(#196).
 *
 * <p><b>원문은 자유 텍스트다.</b> 실측(2026-08-09, 6,118건)에서 이런 값들이 나왔다.
 *
 * <pre>
 *   전남광주통합특별시  711건   ← 개편 후 표기. 지금은 이쪽이 우리 정본이다(#347)
 *   강원도 581 / 강원특별자치도 198   ← 개편 전후가 섞여 있다
 *   전라북도 402 / 전북특별자치도 98
 *   서울 231 / 서울시 18 / 서울특별시 277
 *   인청광역시 · 산광역시 · 전북특별자치도도   ← 오타
 *   신승반점 · FNC · 전주식당   ← 시도 자리에 상호명
 * </pre>
 *
 * <p><b>시도를 함께 보지 않으면 조용히 틀린다.</b> 우리 89곳 중 서구(부산·대구)와 고성군(강원·경남)이
 * 겹치고, 갤러리에는 우리 목록 밖의 동구·남구 사진도 많다. 시군구명만으로 세면 대구 남구가 104건으로
 * 부풀었다(정규화 후 6건). 지명 매칭의 이 함정은 방문자 집계에서 이미 겪었다(#65).
 */
final class GalleryRegionMatcher {

    /**
     * 시도 표기 별칭 → 우리 시드가 쓰는 정본.
     *
     * <p>실측에 나온 표기만 담는다. 여기 없는 표기는 매칭에서 빠질 뿐 틀린 지역에 붙지 않는다.
     */
    private static final Map<String, String> SIDO_ALIASES = Map.ofEntries(
            Map.entry("강원도", "강원특별자치도"),
            Map.entry("강원", "강원특별자치도"),
            Map.entry("전라북도", "전북특별자치도"),
            Map.entry("전북", "전북특별자치도"),
            Map.entry("전북특별자치도도", "전북특별자치도"),
            // 개편 전 표기. 이제 정본이 통합 이름이라 방향이 뒤집혔다(#347).
            // 갤러리에는 개편 전후가 섞여 오므로 둘 다 받는다.
            Map.entry("전라남도", "전남광주통합특별시"),
            Map.entry("전남", "전남광주통합특별시"),
            Map.entry("광주광역시", "전남광주통합특별시"),
            Map.entry("광주", "전남광주통합특별시"),
            Map.entry("경북", "경상북도"),
            Map.entry("경남", "경상남도"),
            Map.entry("충북", "충청북도"),
            Map.entry("충남", "충청남도"),
            Map.entry("경기", "경기도"),
            Map.entry("부산", "부산광역시"),
            Map.entry("부산시", "부산광역시"),
            Map.entry("산광역시", "부산광역시"),
            Map.entry("대구", "대구광역시"),
            Map.entry("대구시", "대구광역시"),
            Map.entry("인천", "인천광역시"),
            Map.entry("인천시", "인천광역시"),
            Map.entry("인청광역시", "인천광역시"));

    /** 매칭에 필요한 것만 추린 지역 — 엔티티를 그대로 받지 않아 단위 테스트가 가능하다. */
    record RegionKey(Long id, String sido, String sigungu) {
    }

    /** (시도, 시군구) → regionId. */
    private final Map<String, Long> bySidoSigungu = new HashMap<>();
    /** 시군구명 → 그 이름을 쓰는 우리 지역 수 — 시도를 못 읽었을 때 안전한지 판단한다. */
    private final Map<String, Integer> nameCounts = new HashMap<>();
    /** 시군구명 → regionId(그 이름이 유일할 때만 유효). */
    private final Map<String, Long> byNameOnly = new HashMap<>();

    /**
     * 훑는 순서를 고정한 시군구명 — <b>긴 이름부터</b>.
     *
     * <p>해시 순서로 돌면 원문에 시군구명이 둘 이상 들어 있을 때 어느 것이 먼저 걸릴지 실행마다 달라질 수
     * 있다. 길이 내림차순이면 결과가 일정하고, 덤으로 <b>최장 일치</b>가 된다.
     */
    private final List<String> namesByLengthDesc;

    /** 우리 시드에 있는 시도 정본 — 생성자에서 한 번 만든다(입력이 안 바뀌는 값). */
    private final Set<String> knownSidos = new HashSet<>();

    GalleryRegionMatcher(List<RegionKey> regions) {
        for (RegionKey region : regions) {
            bySidoSigungu.put(key(region.sido(), region.sigungu()), region.id());
            nameCounts.merge(region.sigungu(), 1, Integer::sum);
            byNameOnly.put(region.sigungu(), region.id());
            knownSidos.add(region.sido());
        }
        namesByLengthDesc = byNameOnly.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /** 지역 엔티티에서 매처를 만든다. */
    static GalleryRegionMatcher from(List<Region> regions) {
        return new GalleryRegionMatcher(regions.stream()
                .map(region -> new RegionKey(region.getId(), region.getSido(), region.getSigungu()))
                .toList());
    }

    /**
     * 촬영 위치 원문이 가리키는 우리 지역.
     *
     * @return 지역 id. 못 가리면 empty — <b>지어내지 않는다</b>
     */
    Optional<Long> match(String photographyLocation) {
        if (photographyLocation == null || photographyLocation.isBlank()) {
            return Optional.empty();
        }
        String location = photographyLocation.strip();
        String sido = normalizedSido(location);

        // 시도를 읽었으면 (시도, 시군구) 정확 매칭. 동명 시군구는 여기서 갈린다.
        if (sido != null) {
            for (String name : namesByLengthDesc) {
                if (location.contains(name)) {
                    Long id = bySidoSigungu.get(key(sido, name));
                    if (id != null) {
                        return Optional.of(id);
                    }
                }
            }
        }
        return matchByNameAlone(location);
    }

    /**
     * 시도를 못 읽었을 때의 폴백 — <b>이름이 우리 안에서 유일하고 광역시 자치구가 아닐 때만</b> 인정한다.
     *
     * <p>"동구"·"남구" 같은 자치구는 전국에 여럿이라 시도 없이는 어느 곳인지 알 수 없다. 반면 "완도군" 처럼
     * 전국에서 유일한 이름은 시도가 없어도 안전하다.
     */
    private Optional<Long> matchByNameAlone(String location) {
        for (String name : namesByLengthDesc) {
            if (name.endsWith("구") || nameCounts.getOrDefault(name, 0) != 1) {
                continue;
            }
            if (location.contains(name)) {
                return Optional.of(byNameOnly.get(name));
            }
        }
        return Optional.empty();
    }

    /** 첫 토큰을 시도로 읽는다. 별칭이면 정본으로 바꾸고, 모르는 표기면 null. */
    private String normalizedSido(String location) {
        String first = location.split("\\s+")[0].replace(",", "");
        if (first.isBlank()) {
            return null;
        }
        String canonical = SIDO_ALIASES.getOrDefault(first, first);
        return knownSidos.contains(canonical) ? canonical : null;
    }

    private static String key(String sido, String sigungu) {
        return sido + " " + sigungu;
    }
}
