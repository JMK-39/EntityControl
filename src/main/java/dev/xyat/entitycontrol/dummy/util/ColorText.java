package dev.xyat.entitycontrol.dummy.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.HashMap;
import java.util.Map;

public final class ColorText {
    private static final Map<String, ChatFormatting[][]> ARG_STYLES = new HashMap<>();

    static {
        ARG_STYLES.put("cmd.entitycontrol.dummy.dummy.cleared", new ChatFormatting[][]{new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD}});
        ARG_STYLES.put("cmd.entitycontrol.dummy.dummy.error", new ChatFormatting[][]{new ChatFormatting[]{ChatFormatting.YELLOW}});
        ARG_STYLES.put("cmd.entitycontrol.dummy.dummy.help.entry", new ChatFormatting[][]{new ChatFormatting[]{ChatFormatting.GREEN}});
        ARG_STYLES.put("dummy.entitycontrol.summary.damage_amount", new ChatFormatting[][]{new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}});
        ARG_STYLES.put("dummy.entitycontrol.summary.kill", new ChatFormatting[][]{new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}});
        ARG_STYLES.put("dummy.entitycontrol.summary.time", new ChatFormatting[][]{new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD}, new ChatFormatting[]{ChatFormatting.YELLOW}});
        ARG_STYLES.put("gui.entitycontrol.dummy.dummy.dps", new ChatFormatting[][]{new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD}});
        ARG_STYLES.put("gui.entitycontrol.dummy.dummy.dps_with_avg", new ChatFormatting[][]{new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD}, new ChatFormatting[]{ChatFormatting.AQUA}});
        ARG_STYLES.put("gui.entitycontrol.dummy.dummy.source", new ChatFormatting[][]{new ChatFormatting[]{ChatFormatting.GOLD}});
        ARG_STYLES.put("gui.entitycontrol.dummy.dummy.stats", new ChatFormatting[][]{new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}, new ChatFormatting[]{ChatFormatting.YELLOW}});
        ARG_STYLES.put("gui.entitycontrol.dummy.dummy.type", new ChatFormatting[][]{new ChatFormatting[]{ChatFormatting.LIGHT_PURPLE}});
    }

    private ColorText() {
    }

    public static MutableComponent translatable(String key, Object... args) {
        ChatFormatting[][] styles = ARG_STYLES.get(key);
        if (styles == null || args.length == 0) {
            return Component.translatable(key, args);
        }
        Object[] styledArgs = args.clone();
        int count = Math.min(styles.length, styledArgs.length);
        for (int i = 0; i < count; i++) {
            ChatFormatting[] formats = styles[i];
            if (formats == null || formats.length == 0) continue;
            Object value = styledArgs[i];
            boolean preserveColor = value instanceof Component existing && existing.getStyle().getColor() != null;
            MutableComponent component = value instanceof Component existing
                    ? existing.copy()
                    : Component.literal(String.valueOf(value));
            if (preserveColor) {
                for (int j = 1; j < formats.length; j++) {
                    component.withStyle(formats[j]);
                }
            } else {
                component.withStyle(formats);
            }
            styledArgs[i] = component;
        }
        return Component.translatable(key, styledArgs);
    }
}
