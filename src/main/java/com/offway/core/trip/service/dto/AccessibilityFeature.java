package com.offway.core.trip.service.dto;

import com.offway.core.trip.domain.AccessibilityCategory;

/**
 * 무장애 편의 한 항목 — 분류·항목명·상세문구. 값이 있는(등록된) 편의만 만들어진다.
 *
 * @param category 이용약자 분류
 * @param name 편의 항목명(예: 휠체어, 장애인 화장실)
 * @param detail 관광지가 등록한 상세 문구(예: "대여가능", "주출입구 경사로")
 */
public record AccessibilityFeature(AccessibilityCategory category, String name, String detail) {
}
