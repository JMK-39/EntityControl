package dev.xyat.entitycontrol.dummy.client;

import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import net.minecraft.network.chat.Component;

public final class NotifyManager {
    private static final String TOAST_ID = "entitycontrol:dummy_notify";

    private NotifyManager() {
    }

    public static void notify(Component msg) {
        if (msg == null) return;
        KineticOverlays.toast(
                TOAST_ID,
                msg,
                KineticOverlays.Position.BOTTOM_CENTER,
                3000,
                0,
                -30
        );
    }
}
