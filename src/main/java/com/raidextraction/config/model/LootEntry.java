package com.raidextraction.config.model;

public record LootEntry(
        String id,
        String material,
        int weight,
        int minAmount,
        int maxAmount
) {
    public boolean isValid() {
        return material != null && !material.isEmpty() && weight > 0 && maxAmount >= minAmount && minAmount > 0;
    }
}
