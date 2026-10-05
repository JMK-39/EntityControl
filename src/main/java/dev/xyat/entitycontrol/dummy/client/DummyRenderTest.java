//? if >=26.1 {
/*
package dev.xyat.entitycontrol.dummy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.xyat.entitycontrol.dummy.entity.DummyEntityTest;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/^* 26.1 renders entities from render states; the dummy keeps the plain villager look at the same scale. ^/
public class DummyRenderTest extends LivingEntityRenderer<DummyEntityTest, VillagerRenderState, VillagerModel> {
    private static final ResourceLocation VILLAGER_TEXTURE = KineticResourceIds.of("minecraft", "textures/entity/villager/villager.png");

    public DummyRenderTest(EntityRendererProvider.Context context) {
        super(context, new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public @NotNull VillagerRenderState createRenderState() {
        return new VillagerRenderState();
    }

    @Override
    protected void scale(@NotNull VillagerRenderState state, @NotNull PoseStack poseStack) {
        float s = 0.9375F;
        poseStack.scale(s, s, s);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull VillagerRenderState state) {
        return VILLAGER_TEXTURE;
    }
}
*///?} else if >=1.21 {
/*
package dev.xyat.entitycontrol.dummy.client;

import javax.annotation.Nonnull;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
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
    private static final ResourceLocation VILLAGER_TEXTURE = KineticResourceIds.of("minecraft", "textures/entity/villager/villager.png");

    public DummyRenderTest(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    protected void scale(@NotNull DummyEntityTest entity, @Nonnull PoseStack poseStack, float partialTickTime) {
        float s = 0.9375F;
        poseStack.scale(s, s, s);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull DummyEntityTest entity) {
        return VILLAGER_TEXTURE;
    }

    @Override
    protected void renderNameTag(@NotNull DummyEntityTest entity, @NotNull Component displayName, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, float partialTick) {
        super.renderNameTag(entity, displayName, poseStack, buffer, packedLight, partialTick);
    }

}
*///?} else {
package dev.xyat.entitycontrol.dummy.client;

import javax.annotation.Nonnull;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
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
    private static final ResourceLocation VILLAGER_TEXTURE = KineticResourceIds.of("minecraft", "textures/entity/villager/villager.png");

    public DummyRenderTest(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    protected void scale(@NotNull DummyEntityTest entity, @Nonnull PoseStack poseStack, float partialTickTime) {
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
//?}
