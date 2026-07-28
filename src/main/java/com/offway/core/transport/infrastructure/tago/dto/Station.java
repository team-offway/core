package com.offway.core.transport.infrastructure.tago.dto;

/**
 * 기차역 한 곳 — TAGO 역 코드와 역명. 지역(시군구명)·출발지를 역으로 해석할 때 쓴다.
 *
 * @param id 역 코드(TAGO nodeid, 예: {@code NAT010000})
 * @param name 역명(예: 서울·정선)
 */
public record Station(String id, String name) {}
