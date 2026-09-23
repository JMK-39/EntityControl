package dev.xyat.entitycontrol.reset.client.gui;

import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticEditBox;
import dev.xyat.kineticcore.api.client.widget.render.KineticEntityPreview.EntityPreviewRenderer;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.entitycontrol.reset.config.EntityReseConfig;
import dev.xyat.entitycontrol.reset.config.EntityReseConfigGui;
import dev.xyat.entitycontrol.reset.network.EntityReseRuleNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.NotNull;

public final class EntityResetRuleEditScreen extends KineticScreen {
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
    private final EntityPreviewRenderer previewRenderer = KineticWidgets.createEntityPreviewRenderer();

    private KineticEditBox thresholdBox;
    private StateButton realDeathButton;
    private StateButton preventedDeathButton;
    private StateButton cancelledDeathButton;
    private StateButton clearButton;
    private StateButton saveButton;
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

    public EntityResetRuleEditScreen(EntityResetRuleListScreen parent, String entityId) {
        super(Component.translatable("gui.entitycontrol.reset.rule_edit.title"));
        this.parent = parent;
        setParentScreen(parent);
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

    @Override
    protected void buildUi() {
        thresholdBox = addTextField(
                338,
                108,
                192,
                Component.translatable("gui.entitycontrol.reset.rule_edit.threshold"),
                null,
                value -> value.isEmpty() || value.chars().allMatch(Character::isDigit),
                null
        );
        thresholdBox.setMaxLength(9);
        thresholdBox.setValue(thresholdText == null || thresholdText.isBlank() ? "1" : thresholdText);
        thresholdBox.setResponder(value -> thresholdText = value == null ? "" : value);

        realDeathButton = addButtonWithHandler(
                326, 146, 216,
                realDeathMessage(),
                Component.translatable("gui.entitycontrol.reset.rule_edit.count_real.tooltip"),
                button -> {
                    countRealDeath = !countRealDeath;
                    button.setText(realDeathMessage());
                }
        );

        preventedDeathButton = addButtonWithHandler(
                326, 174, 216,
                preventedDeathMessage(),
                Component.translatable("gui.entitycontrol.reset.rule_edit.count_prevented.tooltip"),
                button -> {
                    countPreventedDeath = !countPreventedDeath;
                    button.setText(preventedDeathMessage());
                }
        );

        cancelledDeathButton = addButtonWithHandler(
                326, 202, 216,
                cancelledDeathMessage(),
                Component.translatable("gui.entitycontrol.reset.rule_edit.count_cancelled.tooltip"),
                button -> {
                    countCancelledDeath = !countCancelledDeath;
                    button.setText(cancelledDeathMessage());
                }
        );

        clearButton = addButton(
                326, 244, 68,
                Component.translatable("gui.entitycontrol.reset.rule_list.clear"),
                null,
                this::clearRule
        );

        saveButton = addButton(
                400, 244, 68,
                Component.translatable("gui.entitycontrol.reset.rule_edit.save"),
                null,
                this::saveRule
        );

        addButton(
                474, 244, 68,
                Component.translatable("gui.entitycontrol.reset.rule_edit.back"),
                null,
                this::closeToParent
        );

        updateControlsEnabled();
    }

    private Component realDeathMessage() {
        return Component.translatable(
                countRealDeath
                        ? "gui.entitycontrol.reset.rule_edit.count_real.on"
                        : "gui.entitycontrol.reset.rule_edit.count_real.off"
        );
    }

    private Component preventedDeathMessage() {
        return Component.translatable(
                countPreventedDeath
                        ? "gui.entitycontrol.reset.rule_edit.count_prevented.on"
                        : "gui.entitycontrol.reset.rule_edit.count_prevented.off"
        );
    }

    private Component cancelledDeathMessage() {
        return Component.translatable(
                countCancelledDeath
                        ? "gui.entitycontrol.reset.rule_edit.count_cancelled.on"
                        : "gui.entitycontrol.reset.rule_edit.count_cancelled.off"
        );
    }

    private void saveRule() {
        if (waitingForServer) return;

        if (!KineticClientRuntime.connected()) {
            KineticOverlays.toast(Component.translatable("msg.entitycontrol.reset.rule_list.save_failed"));
            return;
        }

        if (pendingRemoval) {
            waitingForServer = true;
            updateControlsEnabled();
            EntityReseRuleNetwork.removeRule(entityId);
            return;
        }

        int threshold;
        try {
            threshold = Integer.parseInt((thresholdText == null ? "" : thresholdText).trim());
        } catch (Exception ignored) {
            KineticOverlays.toast(Component.translatable("msg.entitycontrol.reset.rule_edit.invalid_threshold"));
            return;
        }

        if (threshold < 1) {
            KineticOverlays.toast(Component.translatable("msg.entitycontrol.reset.rule_edit.invalid_threshold"));
            return;
        }

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

        if (result == EntityReseRuleNetwork.RESULT_SAVE_SUCCESS) {
            commitDraft();
            parent.onRuleSaved();
            KTConfigApi.notifySaved(EntityReseConfigGui.PAGE_ID);
            return;
        }

        if (result == EntityReseRuleNetwork.RESULT_REMOVE_SUCCESS) {
            commitDraft();
            parent.onRuleSaved();
            KTConfigApi.notifySaved(EntityReseConfigGui.PAGE_ID);
            return;
        }

        KineticOverlays.toast(Component.translatable("msg.entitycontrol.reset.rule_list.save_failed"));
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
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.canvasBackground(graphics, canvasWidth(), canvasHeight());
        GuiTheme.panel(graphics, 24, 18, 592, 324);
        GuiTheme.itemGrid(graphics, PREVIEW_X, PREVIEW_Y, PREVIEW_W, PREVIEW_H);
        GuiTheme.stateOutline(graphics, PREVIEW_X, PREVIEW_Y, PREVIEW_W, PREVIEW_H, false, false, false);
        GuiTheme.panelAlt(graphics, EDIT_X, EDIT_Y, EDIT_W, EDIT_H);

        graphics.drawCenteredString(font, title, canvasWidth() / 2, 30, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.literal(entityName()), PREVIEW_X + PREVIEW_W / 2, 46, 0xFFFFFFFF);

        boolean hovered = inPreview(mouseX, mouseY);
        previewRenderer.render(
                graphics,
                entityId,
                "entityrese:edit:" + entityId,
                PREVIEW_X + 8,
                PREVIEW_Y + 8,
                PREVIEW_W - 16,
                PREVIEW_H - 16,
                canvasScale(),
                canvasX(),
                canvasY(),
                hovered
        );

        graphics.drawString(
                font,
                Component.translatable("gui.entitycontrol.reset.rule_edit.entity_id", Component.literal(entityId)),
                318,
                78,
                0xFFFFFFFF,
                false
        );
        graphics.drawString(
                font,
                Component.translatable("gui.entitycontrol.reset.rule_edit.threshold"),
                318,
                94,
                0xFFFFFFFF,
                false
        );
        graphics.drawString(
                font,
                Component.translatable("gui.entitycontrol.reset.rule_edit.zoom_hint"),
                PREVIEW_X + 8,
                PREVIEW_Y + PREVIEW_H - 14,
                0xFFFFFFFF,
                false
        );
    }

    private boolean inPreview(double mouseX, double mouseY) {
        return mouseX >= PREVIEW_X && mouseX < PREVIEW_X + PREVIEW_W
                && mouseY >= PREVIEW_Y && mouseY < PREVIEW_Y + PREVIEW_H;
    }

    private String entityName() {
        ResourceLocation location = KineticResourceIds.tryParse(entityId);
        EntityType<?> type = location == null ? null : KineticRegistries.entityTypes().get(location);
        return type == null ? entityId : type.getDescription().getString();
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (inPreview(mouseX, mouseY)) {
            previewRenderer.adjustZoom("entityrese:edit:" + entityId, delta);
            return true;
        }
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    private void closeToParent() {
        navigateBack();
    }

    @Override
    protected boolean handleCloseRequest() {
        return false;
    }

    @Override
    protected void screenRemoved() {
        previewRenderer.clear();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
