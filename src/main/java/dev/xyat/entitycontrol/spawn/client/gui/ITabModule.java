package dev.xyat.entitycontrol.spawn.client.gui;

import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;

/**
 * Spawn editor tab contract. Controls are created through KineticCore; tabs only keep business state.
 */
interface ITabModule {
    void build(KineticUi ui);

    default void render(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    default void onTick() {
    }

    default void updateSelection() {
    }
}
