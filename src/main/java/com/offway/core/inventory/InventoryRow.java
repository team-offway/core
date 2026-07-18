package com.offway.core.inventory;

/** 인벤토리 표 한 줄: 어떤 데이터를, 어디서, 지금 가져올 수 있는지 + 무엇을 주는지. */
public record InventoryRow(
        String name, String provider, String provides, String statusLabel, String tone, String note) {
}
