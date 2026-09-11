package dev.xyat.entitycontrol.spawn.mixin;

import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobCategory.class)
public abstract class MobCategoryMixin {

    @Shadow
    public abstract String getName();

    @Inject(
            method = "getMaxInstancesPerChunk",
            at = @At("HEAD"),
            cancellable = true
    )
    private void entitycontrol_spawn$overrideCategoryCap(CallbackInfoReturnable<Integer> cir) {
        Integer override = BiomeSpawnConfig.getCategoryCapOverride(getName());
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
