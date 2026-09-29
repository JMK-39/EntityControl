package dev.xyat.entitycontrol.modifier.client.gui.panel;

import dev.xyat.entitycontrol.modifier.client.gui.BuffEditScreen;
import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;

import java.util.Locale;
import java.util.Objects;

public final class BuffPanel extends AbstractModifierScrollPanel<MobEffect> {
    private static final int REMOVE_BUTTON_WIDTH = 18;

    private boolean isBuffModified(String buffId) {
        return selectedEntityId != null
                && parent.getLocalData().containsKey(selectedEntityId)
                && parent.getLocalData().get(selectedEntityId).buffs.containsKey(buffId);
    }

    private String getReadableName(MobEffect effect, ResourceLocation id) {
        String translationKey = effect.getDescriptionId();
        String translated = KineticI18n.translatable(translationKey).getString();
        if (translated.equals(translationKey) && id != null) {
            StringBuilder result = new StringBuilder();
            for (String part : id.getPath().replace('.', '_').split("_")) {
                if (part.isEmpty()) continue;
                if (result.length() > 0) result.append(' ');
                result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            return result.toString();
        }
        return translated;
    }

    @Override
    protected void updateSearch(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        displayList = KineticRegistries.mobEffects().values().stream()
                .filter(effect -> {
                    ResourceLocation id = KineticRegistries.mobEffects().id(effect);
                    if (id == null) return false;
                    String readableName = getReadableName(effect, id).toLowerCase(Locale.ROOT);
                    return q.isEmpty()
                            || id.toString().toLowerCase(Locale.ROOT).contains(q)
                            || readableName.contains(q);
                })
                .sorted((left, right) -> {
                    String leftId = Objects.requireNonNull(KineticRegistries.mobEffects().id(left)).toString();
                    String rightId = Objects.requireNonNull(KineticRegistries.mobEffects().id(right)).toString();
                    boolean leftModified = isBuffModified(leftId);
                    boolean rightModified = isBuffModified(rightId);
                    if (leftModified != rightModified) return leftModified ? -1 : 1;
                    return leftId.compareTo(rightId);
                })
                .toList();
        refreshRows();
    }

    @Override
    protected Component getSearchHint() {
        return KineticI18n.translatable("gui.entitycontrol.modifier.modifier.search_buff");
    }

    @Override
    protected void renderRow(KineticGraphics graphics, MobEffect effect, int index, int rowX, int rowY,
                             int rowWidth, int rowHeight, int mouseX, int mouseY,
                             boolean hovered, boolean selected) {
        ResourceLocation id = KineticRegistries.mobEffects().id(effect);
        String effectId = id == null ? "" : id.toString();
        String namespace = id == null ? "minecraft" : id.getNamespace();

        graphics.effectIcon(effect, rowX + 4, rowY + 3, 14);
        int textX = rowX + 22;
        Component namespaceText = KineticI18n.translatable(
                "minecraft".equals(namespace)
                        ? "gui.entitycontrol.modifier.modifier.namespace.minecraft"
                        : "gui.entitycontrol.modifier.modifier.namespace.mod",
                namespace
        );
        graphics.text(namespaceText, textX, rowY + 6, KineticTheme.current().text());

        int nameX = textX + KineticText.width(namespaceText) + 4;
        int rightReserve = isBuffModified(effectId) ? 150 : 28;
        Component nameText = KineticI18n.translatable(
                "gui.entitycontrol.modifier.modifier.name",
                KineticText.ellipsize(getReadableName(effect, id), Math.max(20, rowWidth - (nameX - rowX) - rightReserve))
        );
        graphics.text(nameText, nameX, rowY + 6, KineticTheme.current().text());

        boolean hasBuff = isBuffModified(effectId);
        if (hasBuff) {
            EntityModifierConfig.PotionBuff data = parent.getLocalData().get(selectedEntityId).buffs.get(effectId);
            Component info = KineticI18n.translatable(
                    "gui.entitycontrol.modifier.modifier.buff.info",
                    data.minLevel,
                    data.maxLevel,
                    (int) (data.chance * 100)
            );
            int removeX = rowX + rowWidth - REMOVE_BUTTON_WIDTH - 4;
            int infoRight = removeX - 5;
            graphics.text(info, infoRight - KineticText.width(info), rowY + 6, KineticTheme.current().text());

            boolean removeHovered = mouseX >= removeX && mouseX < removeX + REMOVE_BUTTON_WIDTH
                    && mouseY >= rowY + 2 && mouseY < rowY + rowHeight - 2;
            KineticTheme.button(
                    graphics,
                    removeX,
                    rowY + 2,
                    REMOVE_BUTTON_WIDTH,
                    rowHeight - 4,
                    KineticI18n.translatable("gui.entitycontrol.modifier.modifier.remove_mark"),
                    removeHovered,
                    true,
                    true
            );
        } else {
            Component add = KineticI18n.translatable("gui.entitycontrol.modifier.modifier.add_mark");
            graphics.text(add, rowX + rowWidth - 9 - KineticText.width(add), rowY + 6, KineticTheme.current().text());
        }
    }

    @Override
    protected boolean onRowClicked(MobEffect effect, int index, MouseInput input) {
        if (!input.isLeft()) return false;
        ResourceLocation id = KineticRegistries.mobEffects().id(effect);
        if (id == null || selectedEntityId == null) return false;
        String effectId = id.toString();
        boolean hasBuff = isBuffModified(effectId);

        if (hasBuff && rowList != null) {
            int rowY = rowList.rowTop(index);
            int removeX = rowList.controlX() + rowList.rowsWidth() - REMOVE_BUTTON_WIDTH - 4;
            if (input.inside(removeX, rowY + 2, REMOVE_BUTTON_WIDTH, rowList.rowHeight() - 4)) {
                EntityModifierConfig.EntityEditData data = parent.getLocalData().get(selectedEntityId);
                if (data != null) data.buffs.remove(effectId);
                updateSearch(searchBox == null ? "" : searchBox.textValue());
                return true;
            }
        }

        EntityModifierConfig.PotionBuff buffData = hasBuff
                ? parent.getLocalData().get(selectedEntityId).buffs.get(effectId)
                : new EntityModifierConfig.PotionBuff();
        parent.openChild(new BuffEditScreen(parent, selectedEntityId, effectId, buffData));
        return true;
    }
}
