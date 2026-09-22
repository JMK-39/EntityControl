package dev.xyat.entitycontrol.dummy;

import dev.xyat.entitycontrol.dummy.entity.DummyEntityTest;
import dev.xyat.kineticcore.api.registry.KineticEntityAttributes;
import dev.xyat.kineticcore.api.registry.KineticEntityTypes;
import dev.xyat.kineticcore.api.registry.KineticMenuTypes;
import dev.xyat.kineticcore.api.registry.KineticRegistryHandle;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;

public final class DummyInit {
    public static final KineticRegistryHandle<EntityType<DummyEntityTest>> DUMMY =
            KineticEntityTypes.register(
                    KineticResourceIds.of(DummyModule.MODID, "dummy"),
                    () -> EntityType.Builder.of(DummyEntityTest::new, MobCategory.MISC)
                            .sized(0.6f, 1.95f)
                            .clientTrackingRange(10)
                            .build("dummy")
            );

    public static final KineticRegistryHandle<MenuType<DummyMenu>> DUMMY_MENU =
            KineticMenuTypes.register(
                    KineticResourceIds.of(DummyModule.MODID, "dummy_menu"),
                    (containerId, inventory, data) -> new DummyMenu(
                            containerId,
                            inventory,
                            (DummyEntityTest) inventory.player.level().getEntity(data.readInt())
                    )
            );

    private DummyInit() {
    }

    public static void register() {
        KineticEntityAttributes.registerDefault(DUMMY, () -> DummyEntityTest.createAttributes().build());
    }
}
