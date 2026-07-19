package com.offway.core.inventory.service.dto;

import java.util.List;

/** 인벤토리 페이지 데이터: 지역 수 + 데이터 소스 표. */
public record InventorySnapshot(long regionCount, List<InventoryRow> rows) {
}
