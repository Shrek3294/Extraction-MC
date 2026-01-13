package com.raidextraction.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class MiniMessageText {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MiniMessageText() {
    }

    public static Component parse(String value) {
        if (value == null || value.isBlank()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(value);
    }
}

