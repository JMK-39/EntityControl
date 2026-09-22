package dev.xyat.entitycontrol.dummy.Network;

import dev.xyat.entitycontrol.dummy.client.DeathSummaryOverlay;
import dev.xyat.entitycontrol.dummy.client.DummyTextManager;
import dev.xyat.entitycontrol.dummy.client.NotifyManager;
import dev.xyat.entitycontrol.dummy.config.DummyClientConfig;

public class DummyNetworkClient {
    public static void handleSync(DummyNetwork.Sync packet) {
        if (packet.type == DummyNetwork.Sync.Type.REALTIME) {
            DummyTextManager.handlePacket(
                    packet.entityId,
                    packet.name,
                    packet.typeName,
                    packet.total,
                    packet.dps,
                    packet.avgDps,
                    packet.hits,
                    packet.currentDamage,
                    packet.isDummy,
                    packet.minionOwnerId
            );
        } else if (DummyClientConfig.showDeathSummary.get()) {
            DeathSummaryOverlay.show(packet.name, packet.total, packet.dps, packet.time, packet.hits);
        }
    }

    public static void handleNotify(DummyNetwork.SyncNotify packet) {
        NotifyManager.notify(packet.msg());
    }
}
