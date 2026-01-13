package com.raidextraction.item;

public enum WeaponType {
    DAGGER,
    SWORD,
    SABER,
    RAPIER,
    CLEAVER,
    GREATSWORD,
    GLAIVE,
    SCYTHE;

    public static WeaponType parse(String value, WeaponType fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return WeaponType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}

