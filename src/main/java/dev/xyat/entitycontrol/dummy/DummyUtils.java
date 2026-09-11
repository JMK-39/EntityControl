package dev.xyat.entitycontrol.dummy;

import dev.xyat.entitycontrol.dummy.config.DummyConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.List;

public class DummyUtils {
    /**
     * 格式化数字：
     * - 超过 Integer.MAX_VALUE 显示 ∞
     * - 超过 10亿 显示 G (精确到3位)
     * - 超过 100万 显示 M (精确到3位)
     * - 其余显示原数值 (精确到3位)
     */
    public static String formatNum(float val) {
        if (val >= Integer.MAX_VALUE) return "∞";
        if (val >= 1_000_000_000) return String.format("%.3fG", val / 1_000_000_000.0f);
        if (val >= 1_000_000) return String.format("%.3fM", val / 1_000_000.0f);
        return String.format("%.3f", val);
    }

    /**
     * 装备黑名单检查逻辑
     * 支持：
     * - @modid (匹配模组)
     * - #tag:path (匹配标签)
     * - modid:item_id (匹配具体物品)
     */
    public static boolean isBlacklisted(ItemStack stack) {
        if (stack.isEmpty()) return false;

        List<? extends String> list = DummyConfig.equipmentBlacklist.get();
        if (list == null || list.isEmpty()) return false;

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return false;

        String idStr = id.toString();
        for (String entry : list) {
            if (entry == null || entry.isEmpty()) continue;

            if (entry.startsWith("@")) {
                // 匹配模组ID，例如 @irons_spellbooks
                if (id.getNamespace().equals(entry.substring(1))) return true;
            } else if (entry.startsWith("#")) {
                // 匹配标签，例如 #minecraft:arrows
                try {
                    ResourceLocation tagLoc = new ResourceLocation(entry.substring(1));
                    TagKey<Item> tag = TagKey.create(ForgeRegistries.ITEMS.getRegistryKey(), tagLoc);
                    if (stack.is(tag)) return true;
                } catch (Exception ignored) {}
            } else {
                // 匹配具体ID，例如 minecraft:diamond_sword
                if (idStr.equals(entry)) return true;
            }
        }
        return false;
    }
}
