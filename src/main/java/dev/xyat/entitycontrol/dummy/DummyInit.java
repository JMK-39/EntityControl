package dev.xyat.entitycontrol.dummy;

import dev.xyat.entitycontrol.dummy.DummyModule;
import dev.xyat.entitycontrol.dummy.entity.DummyEntityTest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = DummyModule.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DummyInit {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, DummyModule.MODID);

    public static final RegistryObject<EntityType<DummyEntityTest>> DUMMY =
            ENTITY_TYPES.register("dummy", () -> EntityType.Builder.of(DummyEntityTest::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("dummy"));

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, DummyModule.MODID);

    public static final RegistryObject<MenuType<DummyMenu>> DUMMY_MENU =
            MENU_TYPES.register("dummy_menu", () -> IForgeMenuType.create(DummyMenu::new));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
        MENU_TYPES.register(eventBus);
    }

    @SubscribeEvent
    public static void entityAttributeEvent(EntityAttributeCreationEvent event) {
        event.put(DUMMY.get(), DummyEntityTest.createAttributes().build());
    }
}
