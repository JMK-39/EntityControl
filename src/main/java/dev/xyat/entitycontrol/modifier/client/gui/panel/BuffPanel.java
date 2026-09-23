package dev.xyat.entitycontrol.modifier.client.gui.panel;

import net.minecraft.ChatFormatting;
import dev.xyat.entitycontrol.modifier.client.gui.BuffEditScreen;
import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import dev.xyat.kineticcore.api.registry.KineticRegistries;

import java.util.Locale;
import java.util.Objects;
import java.util.HashMap;
import java.util.Map;

public class BuffPanel extends AbstractModifierScrollPanel<MobEffect> {
    private final Map<String, StateButton> removeButtons = new HashMap<>();

    @Override
    protected int rowStride() { return ROW_HEIGHT + 2; }

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
        displayList = KineticRegistries.mobEffects().values().stream()
                .filter(a -> {
                    ResourceLocation rl = KineticRegistries.mobEffects().id(a);
                    if (rl == null) return false;
                    String readableName = getReadableName(a, rl).toLowerCase(Locale.ROOT);
                    return q.isEmpty() || rl.toString().contains(q) || readableName.contains(q);
                })
                .sorted((a, b) -> {
                    String idA = Objects.requireNonNull(KineticRegistries.mobEffects().id(a)).toString();
                    String idB = Objects.requireNonNull(KineticRegistries.mobEffects().id(b)).toString();
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
        ResourceLocation rl = KineticRegistries.mobEffects().id(effect);
        String effectId = rl != null ? rl.toString() : "";

        int listTop = y + listTopOffset();
        boolean hovered = mx >= x + 4 && mx < x + w - 12
                && my >= listTop && my < listTop + getListHeight()
                && my >= rowY && my < rowY + ROW_HEIGHT;
        GuiTheme.stateSurface(g, x + 4, rowY, w - 16, ROW_HEIGHT,
                GuiTheme.Surface.PANEL_ALT, false, hovered, false);

        String namespace = rl != null ? rl.getNamespace() : "minecraft";
        Component namespaceText = Component.literal("[" + namespace + "]")
                .withStyle(namespace.equals("minecraft") ? ChatFormatting.GREEN : ChatFormatting.BLUE);
        g.drawString(parent.getFont(), namespaceText, x + 8, rowY + 6, 0xFFFFFF);

        String name = getReadableName(effect, rl);
        Component nameText = Component.translatable("gui.entitycontrol.modifier.modifier.name", Component.literal(name).withStyle(ChatFormatting.GOLD));
        int nameX = x + 8 + parent.getFont().width("[" + namespace + "] ");
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

            StateButton removeButton = removeButtons.computeIfAbsent(effectId, id -> {
                StateButton button = KineticWidgets.createCompactButton(
                        0,
                        0,
                        16,
                        Component.translatable("gui.entitycontrol.modifier.modifier.remove_mark"),
                        null,
                        () -> {
                            if (selectedEntityId != null && parent.getLocalData().containsKey(selectedEntityId)) {
                                parent.getLocalData().get(selectedEntityId).buffs.remove(id);
                                updateSearch(searchBox.getValue());
                            }
                        }
                );
                button.setError(true);
                return button;
            });
            removeButton.setX(x + w - 30);
            removeButton.setY(rowY + 2);
            removeButton.setWidth(16);
            KineticWidgets.renderControl(removeButton, g, mx, my, 0.0F);
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
    protected boolean onRowClicked(MobEffect effect, double mx, double my, int button) {
        String effectId = Objects.requireNonNull(KineticRegistries.mobEffects().id(effect)).toString();
        boolean hasBuff = isBuffModified(effectId);

        StateButton removeButton = removeButtons.get(effectId);
        if (hasBuff && removeButton != null && removeButton.mouseClicked(mx, my, button)) return true;

        EntityModifierConfig.PotionBuff buffData = hasBuff ? parent.getLocalData().get(selectedEntityId).buffs.get(effectId) : new EntityModifierConfig.PotionBuff();
        KineticClientRuntime.openScreen(new BuffEditScreen(parent, selectedEntityId, effectId, buffData));
        return true;
    }
}
