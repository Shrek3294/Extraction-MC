package com.raidextraction.item;

public enum ModCategory {
    CORE,
    ENCHANT,
    GRIP,
    EDGE,
    GUARD,
    UTILITY,
    ELEMENT,
    RISK;

    public static ModCategory parse(String value, ModCategory fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return ModCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
