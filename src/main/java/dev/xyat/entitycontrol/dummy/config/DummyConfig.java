package dev.xyat.entitycontrol.dummy.config;

import dev.xyat.kineticcore.api.config.common.KTCommonConfigApi;
import dev.xyat.kineticcore.api.config.common.KTCommonConfigSpec;

import java.util.Arrays;

public class DummyConfig {
    public static final KTCommonConfigSpec SPEC;

    public static final KTCommonConfigSpec.IntValue dummyStandbyRange;
    public static final KTCommonConfigSpec.IntValue dummyBroadcastRange;
    public static final KTCommonConfigSpec.IntValue dummyStandbyCheckIntervalTicks;
    public static final KTCommonConfigSpec.IntValue dummySyncIntervalTicks;
    public static final KTCommonConfigSpec.IntValue dummyCurioExtraSlots;
    public static final KTCommonConfigSpec.StringListValue equipmentBlacklist;

    static {
        KTCommonConfigSpec.Builder builder = KTCommonConfigSpec.builder();

        builder.push("DummySettings");
        dummyStandbyRange = builder.comment(
                "假人休眠范围（方块距离）。若该范围内无玩家，假人进入无敌状态且不拉仇恨。0 = 关闭休眠机制。",
                "Standby range for dummies (in blocks). If no players are near, dummies become invincible. 0 = Disable standby."
        ).translation("cfg.entitycontrol.dummy.dummy.standbyRange").defineInt("standbyRange", 16, 0, 64);

        dummyBroadcastRange = builder.comment(
                "假人战斗数据广播范围（方块距离）。只有该范围内的玩家会收到假人战斗数据显示。0 = 仅攻击者/召唤物主人可见，性能最好。",
                "Broadcast range for dummy combat data (in blocks). 0 = only attacker/minion owner, best performance."
        ).translation("cfg.entitycontrol.dummy.dummy.broadcastRange").defineInt("broadcastRange", 32, 0, 256);

        dummyStandbyCheckIntervalTicks = builder.comment(
                "假人休眠检测间隔（tick）。查询扫描附近玩家；按间隔缓存结果。20 = 每秒检查一次。",
                "Standby check interval in ticks. 20 = check once per second."
        ).translation("cfg.entitycontrol.dummy.dummy.standbyCheckInterval").defineInt("standbyCheckIntervalTicks", 20, 1, 200);

        dummySyncIntervalTicks = builder.comment(
                "假人实时伤害数据最小同步间隔（tick）。1 = 每次伤害都同步；2/3 可显著减少高攻速时的发包。",
                "Minimum realtime damage sync interval in ticks. 1 = every server tick; pending hits are coalesced between flushes."
        ).translation("cfg.entitycontrol.dummy.dummy.syncInterval").defineInt("syncIntervalTicks", 2, 1, 20);

        dummyCurioExtraSlots = builder.comment(
                "假人额外 curio 槽位数量。原版逻辑为 53（总计约54格）。槽位越多，Curios 的 LivingTick 开销越高；不需要大量饰品时建议改小，例如 0/7/15。",
                "Extra curio slots for dummies. Original behavior is 53. More slots means more Curios LivingTick cost; use 0/7/15 if you do not need many."
        ).translation("cfg.entitycontrol.dummy.dummy.curioExtraSlots").defineInt("curioExtraSlots", 53, 0, 53);
        builder.pop();

        builder.push("DummyServerSettings");
        equipmentBlacklist = builder.comment(
                "假人禁止佩戴的装备和饰品列表（支持 @模组名，#标签名，以及具体 物品ID）。",
                "List of items banned from being equipped by dummies (Supports @modid, #tag, and specific item ID)."
        ).translation("cfg.entitycontrol.dummy.dummy.blacklist").defineStringList(
                "equipmentBlacklist",
                Arrays.asList("kineticcore:levitation_backpack", "somerandomitem:infinite_potion"),
                o -> o instanceof String
        );
        builder.pop();

        SPEC = builder.build();
    }

    public static void register() {
        KTCommonConfigApi.register(SPEC, "kineticcore/dummy_server.toml");
    }
}
