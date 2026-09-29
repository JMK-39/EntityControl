package dev.xyat.entitycontrol.spawn.client.gui;

import dev.xyat.entitycontrol.spawn.Network.SpawnNetwork;
import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.list.SelectionItem;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

final class SpawnProfilePage extends KineticPage {
    private final BiomeSpawnConfig.GlobalSettings globals;
    private final int editIndex;

    SpawnProfilePage(BiomeSpawnConfig.GlobalSettings globals, int editIndex) {
        super(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.profile.title"));
        this.globals = globals;
        this.editIndex = editIndex;
    }

    @Override
    protected void build(KineticUi ui) {
        int panelX = 180;
        int panelW = 280;
        int min = Math.max(BiomeSpawnConfig.MIN_PROFILE_COUNT, Math.max(globals.current_index, editIndex));
        boolean canRemove = globals.config_amount > min;
        boolean canAdd = globals.config_amount < BiomeSpawnConfig.MAX_PROFILE_COUNT;

        ui.button(panelX + 12, 58, 32).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.profile_remove"))
                .enabled(canRemove)
                .onClick(() -> changeCount(globals.config_amount - 1)).build();
        ui.button(panelX + panelW - 44, 58, 32).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.profile_add"))
                .enabled(canAdd)
                .onClick(() -> changeCount(globals.config_amount + 1)).build();
        ui.button(panelX + panelW - 80, 318, 68).compact()
                .text(KineticI18n.translatable("gui.entitycontrol.spawn.spawn.exit"))
                .onClick(this::navigateBack).build();

        List<SelectionItem> profiles = new ArrayList<>();
        for (int i = 1; i <= globals.config_amount; i++) {
            profiles.add(new SelectionItem(
                    KineticI18n.translatable("gui.entitycontrol.spawn.spawn.profile_btn_numbered", i),
                    Component.empty(),
                    KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip.profile"),
                    true,
                    false
            ));
        }
        ui.selectionList(panelX + 12, 90, panelW - 24, 214, profiles)
                .selected(Math.max(0, Math.min(profiles.size() - 1, editIndex - 1)))
                .onSelect(index -> {
                    int profile = index + 1;
                    if (profile < 1 || profile > globals.config_amount || profile == editIndex) return;
                    navigateBack();
                    SpawnNetwork.switchProfile(profile);
                }).build();
    }

    private void changeCount(int count) {
        int minimum = Math.max(BiomeSpawnConfig.MIN_PROFILE_COUNT, Math.max(globals.current_index, editIndex));
        int normalized = Math.max(minimum, Math.min(BiomeSpawnConfig.MAX_PROFILE_COUNT, count));
        if (normalized == globals.config_amount) return;
        globals.config_amount = normalized;
        SpawnNetwork.updateProfileCount(normalized);
        rebuild();
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.canvasBackground(graphics, width(), height());
        KineticTheme.panel(graphics, 180, 28, 280, 310);
        graphics.centeredText(title(), width() / 2, 38, KineticTheme.current().text(), false);
        graphics.centeredText(
                KineticI18n.translatable("gui.entitycontrol.spawn.spawn.profile_count", globals.config_amount),
                width() / 2, 63, KineticTheme.current().text(), false
        );
        graphics.wrappedText(
                KineticI18n.translatable("gui.entitycontrol.spawn.spawn.profile_count.tooltip"),
                192, 309, 180, KineticTheme.current().mutedText()
        );
    }
}
