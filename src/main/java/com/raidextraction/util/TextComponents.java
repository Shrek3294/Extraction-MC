package com.raidextraction.util;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TextComponents {
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();

    private TextComponents() {}

    public static @NotNull Component plain(@Nullable String text) {
        return text == null || text.isEmpty() ? Component.empty() : Component.text(text);
    }

    public static @NotNull Component legacy(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return text.indexOf('§') >= 0 ? SECTION.deserialize(text) : AMPERSAND.deserialize(text);
    }

    public static @NotNull List<Component> legacyLines(@NotNull List<String> lines) {
        return lines.stream().map(TextComponents::legacy).toList();
    }
}
