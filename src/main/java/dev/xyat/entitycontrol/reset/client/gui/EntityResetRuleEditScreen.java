package dev.xyat.entitycontrol.reset.client.gui;

import dev.xyat.entitycontrol.reset.config.EntityReseConfig;
import dev.xyat.entitycontrol.reset.config.EntityReseConfigGui;
import dev.xyat.entitycontrol.reset.network.EntityReseRuleNetwork;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.ui.NumberType;
import dev.xyat.kineticcore.api.client.gui.widget.KineticButton;
import dev.xyat.kineticcore.api.client.gui.widget.KineticEntityPreview;
import dev.xyat.kineticcore.api.client.gui.widget.KineticNumberField;
import dev.xyat.kineticcore.api.client.gui.widget.KineticToggle;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public final class EntityResetRuleEditScreen extends KineticPage {
    private static final int PREVIEW_X = 46;
    private static final int PREVIEW_Y = 62;
    private static final int PREVIEW_W = 224;
    private static final int PREVIEW_H = 220;
    private static final int EDIT_X = 304;
    private static final int EDIT_Y = 62;
    private static final int EDIT_W = 260;
    private static final int EDIT_H = 220;

    private final EntityResetRuleListScreen parent;
    private final String entityId;
    private final KineticEntityPreview preview = KineticEntityPreview.create();

    private KineticNumberField thresholdBox;
    private KineticToggle realDeathButton;
    private KineticToggle preventedDeathButton;
    private KineticToggle cancelledDeathButton;
    private KineticButton clearButton;
    private KineticButton saveButton;
    private boolean countRealDeath;
    private boolean countPreventedDeath;
    private boolean countCancelledDeath;
    private String thresholdText;
    private boolean pendingRemoval;
    private boolean waitingForServer;

    private record RuleDraftSnapshot(
            String thresholdText,
            boolean countRealDeath,
            boolean countPreventedDeath,
            boolean countCancelledDeath,
            boolean pendingRemoval
    ) {
    }

    public EntityResetRuleEditScreen(EntityResetRuleListScreen parent, String entityId) {
        super(KineticI18n.translatable("gui.entitycontrol.reset.rule_edit.title"));
        this.parent = parent;
        this.entityId = entityId;

        EntityReseConfig.EntityRule rule = EntityReseConfig.getRule(entityId);
        if (rule == null) {
            countRealDeath = true;
            countPreventedDeath = false;
            countCancelledDeath = false;
            thresholdText = "1";
        } else {
            countRealDeath = rule.countRealDeath;
            countPreventedDeath = rule.countPreventedDeath;
            countCancelledDeath = rule.countCancelledDeath;
            thresholdText = Integer.toString(Math.max(1, rule.threshold));
        }
        configureStandaloneDraft(this::captureRuleDraftSnapshot, this::restoreRuleDraftSnapshot);
    }

    private RuleDraftSnapshot captureRuleDraftSnapshot() {
        return new RuleDraftSnapshot(
                thresholdText,
                countRealDeath,
                countPreventedDeath,
                countCancelledDeath,
                pendingRemoval
        );
    }

    private void restoreRuleDraftSnapshot(RuleDraftSnapshot snapshot) {
        if (snapshot == null) return;
        thresholdText = snapshot.thresholdText();
        countRealDeath = snapshot.countRealDeath();
        countPreventedDeath = snapshot.countPreventedDeath();
        countCancelledDeath = snapshot.countCancelledDeath();
        pendingRemoval = snapshot.pendingRemoval();
    }

    @Override
    protected void build(KineticUi ui) {
        Component thresholdLabel = KineticI18n.translatable("gui.entitycontrol.reset.rule_edit.threshold");
        thresholdBox = ui.numberField(338, 108, 192, NumberType.INT)
                .label(thresholdLabel)
                .allowNegative(false)
                .range(1, Integer.MAX_VALUE)
                .value(parseThresholdForBuild())
                .onChange(value -> thresholdText = value == null ? "" : value)
                .firstShownTextAsDefault().build();

        realDeathButton = ui.toggle(326, 146, 216)
                .labels(
                        KineticI18n.translatable("gui.entitycontrol.reset.rule_edit.count_real.on"),
                        KineticI18n.translatable("gui.entitycontrol.reset.rule_edit.count_real.off")
                )
                .tooltip(KineticI18n.translatable("gui.entitycontrol.reset.rule_edit.count_real.tooltip"))
                .value(countRealDeath)
                .onChange(value -> countRealDeath = value)
                .build();

        preventedDeathButton = ui.toggle(326, 174, 216)
                .labels(
                        KineticI18n.translatable("gui.entitycontrol.reset.rule_edit.count_prevented.on"),
                        KineticI18n.translatable("gui.entitycontrol.reset.rule_edit.count_prevented.off")
                )
                .tooltip(KineticI18n.translatable("gui.entitycontrol.reset.rule_edit.count_prevented.tooltip"))
                .value(countPreventedDeath)
                .onChange(value -> countPreventedDeath = value)
                .build();

        cancelledDeathButton = ui.toggle(326, 202, 216)
                .labels(
                        KineticI18n.translatable("gui.entitycontrol.reset.rule_edit.count_cancelled.on"),
                        KineticI18n.translatable("gui.entitycontrol.reset.rule_edit.count_cancelled.off")
                )
                .tooltip(KineticI18n.translatable("gui.entitycontrol.reset.rule_edit.count_cancelled.tooltip"))
                .value(countCancelledDeath)
                .onChange(value -> countCancelledDeath = value)
                .build();

        clearButton = ui.button(326, 244, 68)
                .text(KineticI18n.translatable("gui.entitycontrol.reset.rule_list.clear"))
                .onClick(this::clearRule)
                .build();
        saveButton = ui.button(400, 244, 68)
                .text(KineticI18n.translatable("gui.entitycontrol.reset.rule_edit.save"))
                .onClick(this::saveRule)
                .build();
        ui.button(474, 244, 68)
                .text(KineticI18n.translatable("gui.entitycontrol.reset.rule_edit.back"))
                .onClick(this::navigateBack)
                .build();

        updateControlsEnabled();
    }

    private int parseThresholdForBuild() {
        try {
            return Math.max(1, Integer.parseInt(thresholdText == null ? "1" : thresholdText.trim()));
        } catch (Exception ignored) {
            return 1;
        }
    }

    private void saveRule() {
        if (waitingForServer) return;
        if (!KineticClientRuntime.connected()) {
            KineticOverlays.toast(KineticI18n.translatable("msg.entitycontrol.reset.rule_list.save_failed"));
            return;
        }

        if (pendingRemoval) {
            waitingForServer = true;
            updateControlsEnabled();
            EntityReseRuleNetwork.removeRule(entityId);
            return;
        }

        Integer threshold = thresholdBox == null ? null : thresholdBox.getIntValue();
        if (threshold == null || threshold < 1) {
            KineticOverlays.toast(KineticI18n.translatable("msg.entitycontrol.reset.rule_edit.invalid_threshold"));
            if (thresholdBox != null) thresholdBox.flashValidationError();
            return;
        }

        thresholdText = Integer.toString(threshold);
        waitingForServer = true;
        updateControlsEnabled();
        EntityReseRuleNetwork.saveRule(
                entityId,
                threshold,
                countRealDeath,
                countPreventedDeath,
                countCancelledDeath
        );
    }

    private void clearRule() {
        if (waitingForServer || pendingRemoval || !EntityReseConfig.hasRule(entityId)) return;
        pendingRemoval = true;
        updateControlsEnabled();
    }

    public void onServerOperationResult(byte result) {
        if (!waitingForServer) return;
        waitingForServer = false;
        updateControlsEnabled();

        if (result == EntityReseRuleNetwork.RESULT_SAVE_SUCCESS
                || result == EntityReseRuleNetwork.RESULT_REMOVE_SUCCESS) {
            commitDraft();
            parent.onRuleSaved();
            KTConfigApi.notifySaved(EntityReseConfigGui.PAGE_ID);
            return;
        }
        KineticOverlays.toast(KineticI18n.translatable("msg.entitycontrol.reset.rule_list.save_failed"));
    }

    private void updateControlsEnabled() {
        boolean enabled = !waitingForServer;
        boolean editable = enabled && !pendingRemoval;
        if (thresholdBox != null) thresholdBox.setEnabled(editable);
        if (realDeathButton != null) realDeathButton.setEnabled(editable);
        if (preventedDeathButton != null) preventedDeathButton.setEnabled(editable);
        if (cancelledDeathButton != null) cancelledDeathButton.setEnabled(editable);
        if (clearButton != null) clearButton.setEnabled(editable && EntityReseConfig.hasRule(entityId));
        if (saveButton != null) saveButton.setEnabled(enabled);
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.canvasBackground(graphics, width(), height());
        KineticTheme.panel(graphics, 24, 18, 592, 324);
        KineticTheme.itemGrid(graphics, PREVIEW_X, PREVIEW_Y, PREVIEW_W, PREVIEW_H);
        KineticTheme.stateOutline(graphics, PREVIEW_X, PREVIEW_Y, PREVIEW_W, PREVIEW_H, false, false, false);
        KineticTheme.panelAlt(graphics, EDIT_X, EDIT_Y, EDIT_W, EDIT_H);

        graphics.centeredText(title(), width() / 2, 30, KineticTheme.current().text(), false);
        ResourceLocation location = KineticResourceIds.tryParse(entityId);
        EntityType<?> type = location == null ? null : KineticRegistries.entityTypes().get(location);
        graphics.centeredText(
                type == null ? Component.literal(entityId) : type.getDescription(),
                PREVIEW_X + PREVIEW_W / 2,
                46,
                KineticTheme.current().text(),
                false
        );

        boolean hovered = mouseX >= PREVIEW_X && mouseX < PREVIEW_X + PREVIEW_W
                && mouseY >= PREVIEW_Y && mouseY < PREVIEW_Y + PREVIEW_H;
        String stateKey = "entityrese:edit:" + entityId;
        preview.render(
                graphics,
                entityId,
                stateKey,
                PREVIEW_X + 8,
                PREVIEW_Y + 8,
                PREVIEW_W - 16,
                PREVIEW_H - 16,
                hovered
        );
        registerPreviewZoomArea(preview, stateKey, PREVIEW_X, PREVIEW_Y, PREVIEW_W, PREVIEW_H);

        graphics.text(
                KineticI18n.translatable("gui.entitycontrol.reset.rule_edit.entity_id", entityId),
                318,
                78,
                KineticTheme.current().text()
        );
        graphics.text(
                KineticI18n.translatable("gui.entitycontrol.reset.rule_edit.threshold"),
                318,
                94,
                KineticTheme.current().text()
        );
        graphics.text(
                KineticI18n.translatable("gui.entitycontrol.reset.rule_edit.zoom_hint"),
                PREVIEW_X + 8,
                PREVIEW_Y + PREVIEW_H - 14,
                KineticTheme.current().mutedText()
        );
    }

    @Override
    protected void onRemoved() {
        preview.clear();
    }
}
