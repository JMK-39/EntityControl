package dev.xyat.entitycontrol.dummy.client.jade;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.entitycontrol.dummy.DummyModule;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * 假人 JADE 提示信息提供者
 */
public enum DummyProvider implements IEntityComponentProvider {
    INSTANCE;

    // 定义唯一的 UID
    public static final ResourceLocation UID = KineticResourceIds.of(DummyModule.MODID, "dummy_info");

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        // 添加国际化说明文本
        // 样式：灰色 + 意大利斜体，使其看起来像系统备注
        tooltip.add(Component.translatable("jade.entitycontrol.dummy.dummy.edit_hint")
                .withStyle(ChatFormatting.ITALIC));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
