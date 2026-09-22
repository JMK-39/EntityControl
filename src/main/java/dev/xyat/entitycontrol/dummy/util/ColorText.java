package dev.xyat.entitycontrol.dummy.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Reads player-facing text and its formatting exclusively from language files. */
public final class ColorText {
    private ColorText() {
    }

    public static MutableComponent translatable(String key, Object... args) {
        return Component.translatable(key, args);
    }
}
