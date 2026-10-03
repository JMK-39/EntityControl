//? if >=1.21 {
/*package dev.xyat.entitycontrol.reset.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.xyat.entitycontrol.dummy.entity.DummyCategories;
import dev.xyat.entitycontrol.dummy.entity.DummyEntityTest;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.EntityTypePredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityPredicate.class)
public abstract class DummyCategoryPredicateMixin {
    @WrapOperation(method = "matches(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/critereon/EntityTypePredicate;matches(Lnet/minecraft/world/entity/EntityType;)Z"), require = 1)
    private boolean entitycontrol$dummyCategory(EntityTypePredicate predicate, EntityType<?> type, Operation<Boolean> original,
                                               ServerLevel level, Vec3 origin, Entity entity) {
        if (entity instanceof DummyEntityTest dummy) {
            Boolean result = DummyCategories.matches(predicate, dummy);
            if (result != null) return result;
        }
        return original.call(predicate, type);
    }
}
*///?}
