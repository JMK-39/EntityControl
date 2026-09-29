package dev.xyat.entitycontrol.modifier.client.gui.panel;

import dev.xyat.entitycontrol.modifier.client.gui.EntityModifierScreen;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import net.minecraft.world.entity.LivingEntity;

public interface IModifierPanel {
    void build(EntityModifierScreen parent, KineticUi ui, int x, int y, int w, int h);

    void render(KineticGraphics graphics, int mouseX, int mouseY, float partialTick);

    void onEntitySelected(String entityId, LivingEntity previewEntity);

    void setVisible(boolean visible);
}
