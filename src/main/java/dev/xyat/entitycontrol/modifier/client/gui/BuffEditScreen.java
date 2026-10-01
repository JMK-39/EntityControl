package dev.xyat.entitycontrol.modifier.client.gui;

import dev.xyat.entitycontrol.client.gui.kit.EcPage;
import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import dev.xyat.kineticcore.api.client.gui.layout.KineticLayout;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 单个药水效果的设置：概率、等级范围、限定维度。顶栏“应用”把设置写回主界面并返回（在主界面保存后生效）。
 */
public final class BuffEditScreen extends EcPage {
    private static final int LABEL_WIDTH = 88;
    private static final int PAIR_WIDTH = 50;

    private final EntityModifierScreen parent;
    private final String entityId;
    private final String effectId;
    private final EntityModifierConfig.PotionBuff buff;
    private final List<String> dimensions = new ArrayList<>();
    private final List<String> allDimensions = new ArrayList<>();
    private double chance;
    private int minLevel;
    private int maxLevel;
    private KineticLayout.Rect helpRect;

    public BuffEditScreen(EntityModifierScreen parent, String entityId, String effectId, EntityModifierConfig.PotionBuff buff) {
        super(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.buff_edit"));
        this.parent = parent;
        this.entityId = entityId;
        this.effectId = effectId;
        this.buff = buff;
        this.chance = buff.chance;
        this.minLevel = buff.minLevel;
        this.maxLevel = buff.maxLevel;
        if (buff.dimensions != null && !buff.dimensions.isBlank()) {
            Arrays.stream(buff.dimensions.split(",")).map(String::trim).filter(value -> !value.isEmpty()).forEach(dimensions::add);
        }
        KineticClientRuntime.knownLevels().forEach(key -> allDimensions.add(key.location().toString()));
        for (String dimension : dimensions) if (!allDimensions.contains(dimension)) allDimensions.add(dimension);
        allDimensions.sort(String::compareTo);
    }

    private static MutableComponent tr(String key, Object... args) {
        return KineticI18n.translatable("gui.entitycontrol.modifier.buff_editor." + key, args);
    }

    @Override
    protected Component headerTitle() {
        return tr("title", effectId);
    }

    @Override
    protected List<HeaderAction> headerActions() {
        return List.of(HeaderAction.button("apply", tr("apply"), tr("apply.tooltip"), this::apply));
    }

    private void apply() {
        if (maxLevel < minLevel) {
            KineticOverlays.toast(KineticI18n.translatable("msg.entitycontrol.modifier.invalid_number"));
            return;
        }
        buff.chance = chance;
        buff.minLevel = minLevel;
        buff.maxLevel = maxLevel;
        buff.dimensions = String.join(",", dimensions);
        parent.getLocalData().computeIfAbsent(entityId, key -> new EntityModifierConfig.EntityEditData()).buffs.put(effectId, buff);
        KineticOverlays.toast(tr("applied"));
        navigateBack();
    }

    @Override
    protected void buildContent(KineticUi ui, KineticLayout.Rect body) {
        KineticLayout.Split columns = halves(body);
        Form form = form(section(columns.first(), tr("section")), LABEL_WIDTH);
        decimalField(ui, number(form.row(KineticI18n.translatable("gui.entitycontrol.modifier.modifier.buff.chance"), tr("chance.tooltip"))),
                chance, 0D, 1D, tr("chance.tooltip"), value -> {
                    if (value != null) chance = value;
                });
        KineticLayout.Rect levels = form.row(tr("levels"), tr("levels.tooltip"));
        intField(ui, new KineticLayout.Rect(levels.x(), levels.y(), PAIR_WIDTH, H), minLevel, 0, 255,
                KineticI18n.translatable("gui.entitycontrol.modifier.modifier.buff.min"), value -> {
                    if (value != null) minLevel = value;
                });
        intField(ui, new KineticLayout.Rect(levels.x() + PAIR_WIDTH + GAP, levels.y(), PAIR_WIDTH, H), maxLevel, 0, 255,
                KineticI18n.translatable("gui.entitycontrol.modifier.modifier.buff.max"), value -> {
                    if (value != null) maxLevel = value;
                });
        List<ToggleOption> options = new ArrayList<>();
        for (String dimension : allDimensions) {
            options.add(new ToggleOption(Component.literal(dimension), tr("dimension.tooltip"), () -> dimensions.contains(dimension),
                    value -> {
                        if (value) {
                            if (!dimensions.contains(dimension)) dimensions.add(dimension);
                        } else {
                            dimensions.remove(dimension);
                        }
                    }));
        }
        toggleMenu(ui, compact(form.row(tr("dimensions"), tr("dimension.tooltip"))), tr("dimensions.button"), tr("dimension.tooltip"),
                options, !options.isEmpty(), null);
        helpRect = section(columns.second(), tr("help.title"));
    }

    @Override
    protected void renderContent(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (helpRect != null) {
            graphics.wrappedText(tr("help"), helpRect.x(), helpRect.y(), helpRect.width(), KineticTheme.current().text());
        }
    }
}
