package dev.xyat.entitycontrol.modifier.client.gui.panel;

import net.minecraft.ChatFormatting;
import dev.xyat.entitycontrol.modifier.client.gui.BuffEditScreen;
import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;
import java.util.Objects;

public class BuffPanel extends AbstractModifierScrollPanel<MobEffect> {

    private boolean isBuffModified(String buffId) {
        return selectedEntityId != null && parent.getLocalData().containsKey(selectedEntityId) &&
                parent.getLocalData().get(selectedEntityId).buffs.containsKey(buffId);
    }

    private String getReadableName(MobEffect effect, ResourceLocation rl) {
        String transName = Component.translatable(effect.getDescriptionId()).getString();
        if (transName.equals(effect.getDescriptionId()) && rl != null) {
            String[] parts = rl.getPath().replace("_", " ").split("\\.");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                if (p.isEmpty()) continue;
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
            }
            return sb.toString().trim();
        }
        return transName;
    }

    @Override
    protected void updateSearch(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        displayList = ForgeRegistries.MOB_EFFECTS.getValues().stream()
                .filter(a -> {
                    ResourceLocation rl = ForgeRegistries.MOB_EFFECTS.getKey(a);
                    if (rl == null) return false;
                    String readableName = getReadableName(a, rl).toLowerCase(Locale.ROOT);
                    return q.isEmpty() || rl.toString().contains(q) || readableName.contains(q);
                })
                .sorted((a, b) -> {
                    String idA = Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getKey(a)).toString();
                    String idB = Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getKey(b)).toString();
                    boolean modA = isBuffModified(idA);
                    boolean modB = isBuffModified(idB);
                    if (modA != modB) return modA ? -1 : 1;
                    return idA.compareTo(idB);
                }).toList();
        refreshScroll();
    }

    @Override
    protected Component getSearchHint() { return Component.translatable("gui.entitycontrol.modifier.modifier.search_buff"); }

    @Override
    protected void renderRow(GuiGraphics g, MobEffect effect, int rowY, int mx, int my) {
        ResourceLocation rl = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        String effectId = rl != null ? rl.toString() : "";

        if (mx >= x && mx < x + w - 10 && my >= rowY && my < rowY + 20) {
            g.fill(x, rowY, x + w - 10, rowY + 20, 0x33FFFFFF);
        }

        String namespace = rl != null ? rl.getNamespace() : "minecraft";
        Component namespaceText = Component.literal("[" + namespace + "]")
                .withStyle(namespace.equals("minecraft") ? ChatFormatting.GREEN : ChatFormatting.BLUE);
        g.drawString(parent.getFont(), namespaceText, x + 5, rowY + 6, 0xFFFFFF);

        String name = getReadableName(effect, rl);
        Component nameText = Component.translatable("gui.entitycontrol.modifier.modifier.name", Component.literal(name).withStyle(ChatFormatting.GOLD));
        int nameX = x + 5 + parent.getFont().width("[" + namespace + "] ");
        g.drawString(parent.getFont(), nameText, nameX, rowY + 6, 0xFFFFFF);

        boolean hasBuff = isBuffModified(effectId);
        if (hasBuff) {
            EntityModifierConfig.PotionBuff bData = parent.getLocalData().get(selectedEntityId).buffs.get(effectId);
            Component info = Component.translatable(
                    "gui.entitycontrol.modifier.modifier.buff.info",
                    Component.literal(String.valueOf(bData.minLevel)).withStyle(ChatFormatting.YELLOW),
                    Component.literal(String.valueOf(bData.maxLevel)).withStyle(ChatFormatting.YELLOW),
                    Component.literal(String.valueOf((int) (bData.chance * 100))).withStyle(ChatFormatting.GREEN)
            ).withStyle(ChatFormatting.GRAY);
            g.drawString(parent.getFont(), info, x + w - 40 - parent.getFont().width(info), rowY + 6, 0xFFFFFF);

            g.fill(x + w - 30, rowY + 2, x + w - 14, rowY + 18, 0xFFAA0000);
            g.drawCenteredString(
                    parent.getFont(),
                    Component.translatable("gui.entitycontrol.modifier.modifier.remove_mark"),
                    x + w - 22,
                    rowY + 6,
                    0xFFFFFF
            );
        } else {
            g.drawString(
                    parent.getFont(),
                    Component.translatable("gui.entitycontrol.modifier.modifier.add_mark"),
                    x + w - 25,
                    rowY + 6,
                    0xFFFFFF
            );
        }
    }

    @Override
    protected boolean onRowClicked(MobEffect effect, double mx, double my) {
        String effectId = Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getKey(effect)).toString();
        boolean hasBuff = isBuffModified(effectId);

        if (hasBuff && mx >= x + w - 30 && mx <= x + w - 14) {
            parent.getLocalData().get(selectedEntityId).buffs.remove(effectId);
            updateSearch(searchBox.getValue());
            return true;
        }

        EntityModifierConfig.PotionBuff buffData = hasBuff ? parent.getLocalData().get(selectedEntityId).buffs.get(effectId) : new EntityModifierConfig.PotionBuff();
        Minecraft.getInstance().setScreen(new BuffEditScreen(parent, selectedEntityId, effectId, buffData));
        return true;
    }
}
