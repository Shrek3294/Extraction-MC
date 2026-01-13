package com.raidextraction.item;

public record SpellDefinition(
        String id,
        long cooldownMs,
        int manaCost
) {
}

