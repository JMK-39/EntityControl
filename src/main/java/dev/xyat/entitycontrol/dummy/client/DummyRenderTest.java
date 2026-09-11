package dev.xyat.entitycontrol.dummy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.xyat.entitycontrol.dummy.entity.DummyEntityTest;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class DummyRenderTest extends LivingEntityRenderer<DummyEntityTest, VillagerModel<DummyEntityTest>> {
    private static final ResourceLocation VILLAGER_TEXTURE = new ResourceLocation("minecraft", "textures/entity/villager/villager.png");

    public DummyRenderTest(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    protected void scale(@NotNull DummyEntityTest entity, PoseStack poseStack, float partialTickTime) {
        float s = 0.9375F;
        poseStack.scale(s, s, s);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull DummyEntityTest entity) {
        return VILLAGER_TEXTURE;
    }

    @Override
    protected void renderNameTag(@NotNull DummyEntityTest entity, @NotNull Component displayName, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        super.renderNameTag(entity, displayName, poseStack, buffer, packedLight);
    }
}
