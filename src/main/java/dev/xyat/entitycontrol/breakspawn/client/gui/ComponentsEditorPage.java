//? if >=1.21 {
/*package dev.xyat.entitycontrol.breakspawn.client.gui;

import dev.xyat.entitycontrol.breakspawn.data.EquipmentData;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.KineticButton;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

public final class ComponentsEditorPage extends KineticPage {
    private final String itemId;
    private final Consumer<String> onSave;
    private String data;
    private KineticButton save;
    private boolean valid;

    public ComponentsEditorPage(String itemId, String initial, Consumer<String> onSave) {
        super(KineticI18n.translatable("gui.entitycontrol.components.title"));
        this.itemId = itemId;
        this.data = initial == null || initial.isBlank() ? "[]" : initial;
        this.onSave = onSave;
    }

    @Override
    protected void build(KineticUi ui) {
        int editorWidth = Math.min(width() - 30, 700);
        int x = (width() - editorWidth) / 2;
        var input = ui.textArea(x, 45, editorWidth, Math.max(30, height() - 110))
                .label(title()).maxLength(32767).value(data).onChange(text -> {
                    data = text;
                    validate();
                }).build();
        save = ui.button(width() / 2 - 85, height() - 30, 80)
                .text(KineticI18n.translatable("gui.entitycontrol.components.save"))
                .onClick(button -> {
                    validate();
                    if (!valid) return;
                    onSave.accept(data.isBlank() ? "[]" : data.trim());
                    navigateBack();
                }).build();
        ui.button(width() / 2 + 5, height() - 30, 80)
                .text(KineticI18n.translatable("gui.entitycontrol.components.back"))
                .onClick(button -> navigateBack()).build();
        validate();
        focus(input);
    }

    private void validate() {
        try {
            EquipmentData.compile(itemId, data);
            valid = true;
            if (save != null) save.setTooltip((Component) null);
        } catch (RuntimeException invalid) {
            valid = false;
            if (save != null) save.setTooltip(Component.literal(invalid.getMessage() == null ? "" : invalid.getMessage()));
        }
        if (save != null) save.setEnabled(valid);
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.centeredText(title(), width() / 2, 8, 0xFFFFFF, true);
        graphics.centeredText(KineticI18n.translatable("gui.entitycontrol.components.hint"), width() / 2, 27, 0xAAAAAA, false);
        if (!valid) graphics.centeredText(KineticI18n.translatable("gui.entitycontrol.components.invalid"),
                width() / 2, height() - 53, 0xFF5555, false);
    }
}
*///?}