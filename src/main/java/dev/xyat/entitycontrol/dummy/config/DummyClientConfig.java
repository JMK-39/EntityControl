package dev.xyat.entitycontrol.dummy.config;

import dev.xyat.kineticcore.api.config.client.KTClientConfigAdapter;
import dev.xyat.kineticcore.api.config.client.KTClientConfigSpec;

public class DummyClientConfig {
    public static final KTClientConfigSpec SPEC;

    public static final KTClientConfigSpec.BooleanValue showDamageParticles;
    public static final KTClientConfigSpec.BooleanValue accumulateDamage;
    public static final KTClientConfigSpec.DoubleValue particleScale;
    public static final KTClientConfigSpec.DoubleValue damageTextScale;
    public static final KTClientConfigSpec.DoubleValue particleSpread;
    public static final KTClientConfigSpec.IntValue colorNormal;
    public static final KTClientConfigSpec.IntValue colorCrit;
    public static final KTClientConfigSpec.BooleanValue showMinionDamage;
    public static final KTClientConfigSpec.IntValue colorMinion;

    public static final KTClientConfigSpec.BooleanValue showOverheadSource;
    public static final KTClientConfigSpec.BooleanValue showOverheadType;
    public static final KTClientConfigSpec.BooleanValue showOverheadAvgDps;
    public static final KTClientConfigSpec.DoubleValue overheadScale;
    public static final KTClientConfigSpec.DoubleValue overheadOffset;
    public static final KTClientConfigSpec.IntValue colorOverheadSource;
    public static final KTClientConfigSpec.IntValue colorOverheadType;
    public static final KTClientConfigSpec.IntValue colorOverheadStats;
    public static final KTClientConfigSpec.IntValue colorOverheadDps;

    public static final KTClientConfigSpec.BooleanValue showDeathSummary;
    public static final KTClientConfigSpec.BooleanValue showSummaryKill;
    public static final KTClientConfigSpec.BooleanValue showSummaryStats;
    public static final KTClientConfigSpec.BooleanValue showSummaryTime;
    public static final KTClientConfigSpec.IntValue summaryDuration;
    public static final KTClientConfigSpec.DoubleValue summaryScale;
    public static final KTClientConfigSpec.IntValue colorSummaryTitle;
    public static final KTClientConfigSpec.IntValue colorSummaryStats;
    public static final KTClientConfigSpec.IntValue colorSummaryTime;

    static {
        KTClientConfigSpec.Builder builder = KTClientConfigSpec.builder();

        builder.push("PeacockParticles");
        showDamageParticles = builder.comment(
                "是否显示生物受伤时的浮动伤害数字。",
                "Whether to show floating damage numbers when mobs take damage."
        ).translation("cfg.entitycontrol.dummy.dummy.showParticles").defineBoolean("showDamageParticles", true);

        accumulateDamage = builder.comment(
                "是否累计显示同一生物在连续攻击期间受到的伤害。停止受到伤害 3 秒后自动清空。",
                "Whether to accumulate damage dealt to the same mob during continuous attacks. The value clears after 3 seconds without damage."
        ).translation("cfg.entitycontrol.dummy.dummy.accumulateDamage").defineBoolean("accumulateDamage", false);

        particleScale = builder.comment(
                "伤害数字粒子的整体缩放比例。",
                "Overall scale multiplier for damage number particles."
        ).translation("cfg.entitycontrol.dummy.dummy.particleScale").defineDouble("scale", 1.0, 0.1, 5.0);

        damageTextScale = builder.comment(
                "假人头顶伤害面板与攻击其他生物时伤害数字的共同文字缩放倍率。",
                "Shared text scale for the dummy damage panel and damage numbers on other mobs."
        ).translation("cfg.entitycontrol.dummy.dummy.damageTextScale")
                .defineDouble("textScale", 1.0, 0.1, 3.0);

        particleSpread = builder.comment(
                "伤害数字粒子的随机散布范围（防止数字重叠）。",
                "Random spread range for damage particles (prevents overlapping)."
        ).translation("cfg.entitycontrol.dummy.dummy.particleSpread").defineDouble("spread", 0.15, 0.0, 1.0);

        colorNormal = builder.comment(
                "普通伤害数字的颜色 (24 位十六进制 RGB)。",
                "Color for normal damage numbers (24-bit Hex RGB)."
        ).translation("cfg.entitycontrol.dummy.dummy.colorNormal").defineInt("colorNormal", 0xFF69B4, 0, 0xFFFFFF);

        colorCrit = builder.comment(
                "暴击伤害数字的颜色 (24 位十六进制 RGB)。",
                "Color for critical hit damage numbers (24-bit Hex RGB)."
        ).translation("cfg.entitycontrol.dummy.dummy.colorCrit").defineInt("colorCrit", 0xFF5555, 0, 0xFFFFFF);

        showMinionDamage = builder.comment(
                "是否显示仆从造成的伤害数字 (仅主人可见)。",
                "Whether to show damage numbers dealt by minions (Only visible to owner)."
        ).translation("cfg.entitycontrol.dummy.dummy.showMinionDamage").defineBoolean("showMinionDamage", true);

        colorMinion = builder.comment(
                "仆从伤害数字的颜色 (24 位十六进制 RGB)。",
                "Color for minion damage numbers (24-bit Hex RGB)."
        ).translation("cfg.entitycontrol.dummy.dummy.colorMinion").defineInt("colorMinion", 0x55FF55, 0, 0xFFFFFF);
        builder.pop();

        builder.push("OverheadHUD");
        showOverheadSource = builder.comment(
                "是否在实体头顶显示伤害来源（如玩家ID）。",
                "Whether to display the damage source above the entity."
        ).translation("cfg.entitycontrol.dummy.dummy.showOverSource").defineBoolean("showSource", true);

        showOverheadType = builder.comment(
                "是否在实体头顶显示伤害类型（如暴击/火焰）。",
                "Whether to display the damage type above the entity."
        ).translation("cfg.entitycontrol.dummy.dummy.showOverType").defineBoolean("showType", true);

        showOverheadAvgDps = builder.comment(
                "是否在实体头顶显示平均每秒伤害(DPS)。",
                "Whether to display the average DPS above the entity."
        ).translation("cfg.entitycontrol.dummy.dummy.showOverAvgDps").defineBoolean("showAvgDps", true);

        overheadScale = builder.comment(
                "头顶文字信息的整体缩放比例。",
                "Overall scale multiplier for the overhead text information."
        ).translation("cfg.entitycontrol.dummy.dummy.overScale").defineDouble("scale", 1.0, 0.1, 5.0);

        overheadOffset = builder.comment(
                "头顶文字相对于实体头部的垂直高度偏移量。",
                "Vertical height offset for the overhead text."
        ).translation("cfg.entitycontrol.dummy.dummy.overOffset").defineDouble("offset", 0.5, 0.0, 10.0);

        colorOverheadSource = builder.comment(
                "伤害来源文字的颜色。",
                "Color for the damage source text."
        ).translation("cfg.entitycontrol.dummy.dummy.colorOverSource").defineInt("colorSource", 0xBBFFFF, 0, 0xFFFFFF);

        colorOverheadType = builder.comment(
                "伤害类型文字的颜色。",
                "Color for the damage type text."
        ).translation("cfg.entitycontrol.dummy.dummy.colorOverType").defineInt("colorType", 0xFFFF55, 0, 0xFFFFFF);

        colorOverheadStats = builder.comment(
                "统计数值文字的颜色。",
                "Color for the statistical value text."
        ).translation("cfg.entitycontrol.dummy.dummy.colorOverStats").defineInt("colorStats", 0x55FF55, 0, 0xFFFFFF);

        colorOverheadDps = builder.comment(
                "DPS文字的颜色。",
                "Color for the DPS text."
        ).translation("cfg.entitycontrol.dummy.dummy.colorOverDps").defineInt("colorDps", 0x00F6F6, 0, 0xFFFFFF);
        builder.pop();

        builder.push("DeathSummaryHUD");
        showDeathSummary = builder.comment(
                "是否启用怪物/假人死亡后的战斗结算界面。",
                "Whether to enable the combat summary HUD after entity death."
        ).translation("cfg.entitycontrol.dummy.dummy.showSummary").defineBoolean("enable", true);

        showSummaryKill = builder.comment(
                "是否在结算界面中显示击杀目标的名称。",
                "Whether to display the killed target's name in the summary."
        ).translation("cfg.entitycontrol.dummy.dummy.showSummaryKill").defineBoolean("showKill", true);

        showSummaryStats = builder.comment(
                "是否在结算界面中显示详细伤害统计（总伤害/连击数）。",
                "Whether to display detailed damage stats (total damage/hits)."
        ).translation("cfg.entitycontrol.dummy.dummy.showSummaryStats").defineBoolean("showStats", true);

        showSummaryTime = builder.comment(
                "是否在结算界面中显示战斗持续时间。",
                "Whether to display the combat duration in the summary."
        ).translation("cfg.entitycontrol.dummy.dummy.showSummaryTime").defineBoolean("showTime", true);

        summaryDuration = builder.comment(
                "结算界面在屏幕上的停留时间（单位：游戏刻/ticks）。",
                "Duration the summary stays on screen (in game ticks)."
        ).translation("cfg.entitycontrol.dummy.dummy.summaryDuration").defineInt("durationTicks", 100, 20, 600);

        summaryScale = builder.comment(
                "结算界面文字的整体缩放比例。",
                "Overall scale multiplier for the summary HUD."
        ).translation("cfg.entitycontrol.dummy.dummy.sumScale").defineDouble("scale", 1.0, 0.1, 5.0);

        colorSummaryTitle = builder.comment(
                "结算界面标题的颜色。",
                "Color for the summary title text."
        ).translation("cfg.entitycontrol.dummy.dummy.colorSumTitle").defineInt("colorTitle", 0xFFAA00, 0, 0xFFFFFF);

        colorSummaryStats = builder.comment(
                "结算界面统计数值的颜色。",
                "Color for the summary statistics text."
        ).translation("cfg.entitycontrol.dummy.dummy.colorSumStats").defineInt("colorStats", 0x55FF55, 0, 0xFFFFFF);

        colorSummaryTime = builder.comment(
                "结算界面时间文字的颜色。",
                "Color for the summary time text."
        ).translation("cfg.entitycontrol.dummy.dummy.colorSumTime").defineInt("colorTime", 0x55FFFF, 0, 0xFFFFFF);
        builder.pop();

        SPEC = builder.build();
    }

    public static void register() {
        KTClientConfigAdapter.registerSpec(SPEC, "kineticcore/dummy_client.toml");
    }
}
