package dev.xyat.entitycontrol.dummy.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public class DummyClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue showDamageParticles;
    public static final ForgeConfigSpec.BooleanValue accumulateDamage;
    public static final ForgeConfigSpec.DoubleValue particleScale;
    public static final ForgeConfigSpec.DoubleValue particleSpread;
    public static final ForgeConfigSpec.IntValue colorNormal;
    public static final ForgeConfigSpec.IntValue colorCrit;
    public static final ForgeConfigSpec.BooleanValue showMinionDamage;
    public static final ForgeConfigSpec.IntValue colorMinion;

    public static final ForgeConfigSpec.BooleanValue showOverheadSource;
    public static final ForgeConfigSpec.BooleanValue showOverheadType;
    public static final ForgeConfigSpec.BooleanValue showOverheadAvgDps;
    public static final ForgeConfigSpec.DoubleValue overheadScale;
    public static final ForgeConfigSpec.DoubleValue overheadOffset;
    public static final ForgeConfigSpec.IntValue colorOverheadSource;
    public static final ForgeConfigSpec.IntValue colorOverheadType;
    public static final ForgeConfigSpec.IntValue colorOverheadStats;
    public static final ForgeConfigSpec.IntValue colorOverheadDps;

    public static final ForgeConfigSpec.BooleanValue showDeathSummary;
    public static final ForgeConfigSpec.BooleanValue showSummaryKill;
    public static final ForgeConfigSpec.BooleanValue showSummaryStats;
    public static final ForgeConfigSpec.BooleanValue showSummaryTime;
    public static final ForgeConfigSpec.IntValue summaryDuration;
    public static final ForgeConfigSpec.DoubleValue summaryScale;
    public static final ForgeConfigSpec.IntValue colorSummaryTitle;
    public static final ForgeConfigSpec.IntValue colorSummaryStats;
    public static final ForgeConfigSpec.IntValue colorSummaryTime;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.translation("cfg.entitycontrol.dummy.dummy.particles").push("PeacockParticles");
        showDamageParticles = builder.comment(
                "是否显示生物受伤时的浮动伤害数字。",
                "Whether to show floating damage numbers when mobs take damage."
        ).translation("cfg.entitycontrol.dummy.dummy.showParticles").define("showDamageParticles", true);

        accumulateDamage = builder.comment(
                "是否累计显示同一生物在连续攻击期间受到的伤害。停止受到伤害 3 秒后自动清空。",
                "Whether to accumulate damage dealt to the same mob during continuous attacks. The value clears after 3 seconds without damage."
        ).translation("cfg.entitycontrol.dummy.dummy.accumulateDamage").define("accumulateDamage", false);

        particleScale = builder.comment(
                "伤害数字粒子的整体缩放比例。",
                "Overall scale multiplier for damage number particles."
        ).translation("cfg.entitycontrol.dummy.dummy.particleScale").defineInRange("scale", 1.0, 0.1, 5.0);

        particleSpread = builder.comment(
                "伤害数字粒子的随机散布范围（防止数字重叠）。",
                "Random spread range for damage particles (prevents overlapping)."
        ).translation("cfg.entitycontrol.dummy.dummy.particleSpread").defineInRange("spread", 0.15, 0.0, 1.0);

        colorNormal = builder.comment(
                "普通伤害数字的颜色 (24 位十六进制 RGB)。",
                "Color for normal damage numbers (24-bit Hex RGB)."
        ).translation("cfg.entitycontrol.dummy.dummy.colorNormal").defineInRange("colorNormal", 0xFF69B4, 0, 0xFFFFFF);

        colorCrit = builder.comment(
                "暴击伤害数字的颜色 (24 位十六进制 RGB)。",
                "Color for critical hit damage numbers (24-bit Hex RGB)."
        ).translation("cfg.entitycontrol.dummy.dummy.colorCrit").defineInRange("colorCrit", 0xFF5555, 0, 0xFFFFFF);

        showMinionDamage = builder.comment(
                "是否显示仆从造成的伤害数字 (仅主人可见)。",
                "Whether to show damage numbers dealt by minions (Only visible to owner)."
        ).translation("cfg.entitycontrol.dummy.dummy.showMinionDamage").define("showMinionDamage", true);

        colorMinion = builder.comment(
                "仆从伤害数字的颜色 (24 位十六进制 RGB)。",
                "Color for minion damage numbers (24-bit Hex RGB)."
        ).translation("cfg.entitycontrol.dummy.dummy.colorMinion").defineInRange("colorMinion", 0x55FF55, 0, 0xFFFFFF);
        builder.pop();

        builder.translation("cfg.entitycontrol.dummy.dummy.overhead").push("OverheadHUD");
        showOverheadSource = builder.comment(
                "是否在实体头顶显示伤害来源（如玩家ID）。",
                "Whether to display the damage source above the entity."
        ).translation("cfg.entitycontrol.dummy.dummy.showOverSource").define("showSource", true);

        showOverheadType = builder.comment(
                "是否在实体头顶显示伤害类型（如暴击/火焰）。",
                "Whether to display the damage type above the entity."
        ).translation("cfg.entitycontrol.dummy.dummy.showOverType").define("showType", true);

        showOverheadAvgDps = builder.comment(
                "是否在实体头顶显示平均每秒伤害(DPS)。",
                "Whether to display the average DPS above the entity."
        ).translation("cfg.entitycontrol.dummy.dummy.showOverAvgDps").define("showAvgDps", true);

        overheadScale = builder.comment(
                "头顶文字信息的整体缩放比例。",
                "Overall scale multiplier for the overhead text information."
        ).translation("cfg.entitycontrol.dummy.dummy.overScale").defineInRange("scale", 1.0, 0.1, 5.0);

        overheadOffset = builder.comment(
                "头顶文字相对于实体头部的垂直高度偏移量。",
                "Vertical height offset for the overhead text."
        ).translation("cfg.entitycontrol.dummy.dummy.overOffset").defineInRange("offset", 0.5, 0.0, 10.0);

        colorOverheadSource = builder.comment(
                "伤害来源文字的颜色。",
                "Color for the damage source text."
        ).translation("cfg.entitycontrol.dummy.dummy.colorOverSource").defineInRange("colorSource", 0xBBFFFF, 0, 0xFFFFFF);

        colorOverheadType = builder.comment(
                "伤害类型文字的颜色。",
                "Color for the damage type text."
        ).translation("cfg.entitycontrol.dummy.dummy.colorOverType").defineInRange("colorType", 0xFFFF55, 0, 0xFFFFFF);

        colorOverheadStats = builder.comment(
                "统计数值文字的颜色。",
                "Color for the statistical value text."
        ).translation("cfg.entitycontrol.dummy.dummy.colorOverStats").defineInRange("colorStats", 0x55FF55, 0, 0xFFFFFF);

        colorOverheadDps = builder.comment(
                "DPS文字的颜色。",
                "Color for the DPS text."
        ).translation("cfg.entitycontrol.dummy.dummy.colorOverDps").defineInRange("colorDps", 0x00F6F6, 0, 0xFFFFFF);
        builder.pop();

        builder.translation("cfg.entitycontrol.dummy.dummy.summary").push("DeathSummaryHUD");
        showDeathSummary = builder.comment(
                "是否启用怪物/假人死亡后的战斗结算界面。",
                "Whether to enable the combat summary HUD after entity death."
        ).translation("cfg.entitycontrol.dummy.dummy.showSummary").define("enable", true);

        showSummaryKill = builder.comment(
                "是否在结算界面中显示击杀目标的名称。",
                "Whether to display the killed target's name in the summary."
        ).translation("cfg.entitycontrol.dummy.dummy.showSummaryKill").define("showKill", true);

        showSummaryStats = builder.comment(
                "是否在结算界面中显示详细伤害统计（总伤害/连击数）。",
                "Whether to display detailed damage stats (total damage/hits)."
        ).translation("cfg.entitycontrol.dummy.dummy.showSummaryStats").define("showStats", true);

        showSummaryTime = builder.comment(
                "是否在结算界面中显示战斗持续时间。",
                "Whether to display the combat duration in the summary."
        ).translation("cfg.entitycontrol.dummy.dummy.showSummaryTime").define("showTime", true);

        summaryDuration = builder.comment(
                "结算界面在屏幕上的停留时间（单位：游戏刻/ticks）。",
                "Duration the summary stays on screen (in game ticks)."
        ).translation("cfg.entitycontrol.dummy.dummy.summaryDuration").defineInRange("durationTicks", 100, 20, 600);

        summaryScale = builder.comment(
                "结算界面文字的整体缩放比例。",
                "Overall scale multiplier for the summary HUD."
        ).translation("cfg.entitycontrol.dummy.dummy.sumScale").defineInRange("scale", 1.0, 0.1, 5.0);

        colorSummaryTitle = builder.comment(
                "结算界面标题的颜色。",
                "Color for the summary title text."
        ).translation("cfg.entitycontrol.dummy.dummy.colorSumTitle").defineInRange("colorTitle", 0xFFAA00, 0, 0xFFFFFF);

        colorSummaryStats = builder.comment(
                "结算界面统计数值的颜色。",
                "Color for the summary statistics text."
        ).translation("cfg.entitycontrol.dummy.dummy.colorSumStats").defineInRange("colorStats", 0x55FF55, 0, 0xFFFFFF);

        colorSummaryTime = builder.comment(
                "结算界面时间文字的颜色。",
                "Color for the summary time text."
        ).translation("cfg.entitycontrol.dummy.dummy.colorSumTime").defineInRange("colorTime", 0x55FFFF, 0, 0xFFFFFF);
        builder.pop();

        SPEC = builder.build();
    }

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.CLIENT, SPEC, "kineticcore/dummy_client.toml");
    }
}
