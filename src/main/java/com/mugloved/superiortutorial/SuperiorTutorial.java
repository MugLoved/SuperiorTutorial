package com.mugloved.superiortutorial;

import com.mugloved.superiortutorial.entity.Larry;
import com.mugloved.superiortutorial.story.TutorialConditions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Superior Tutorial: the optional first cave of Superior, an addon to Superior Story.
 * Everything the tutorial needs lives here; Story is extended only through its public module registry.
 */
@Mod(SuperiorTutorial.MOD_ID)
public final class SuperiorTutorial {
    public static final String MOD_ID = "superior_tutorial";
    public static final Logger LOGGER = LoggerFactory.getLogger("SuperiorTutorial");

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    public static final RegistryObject<EntityType<Larry>> LARRY = ENTITIES.register("larry", () ->
        EntityType.Builder.of(Larry::new, MobCategory.MISC)
            .sized(0.6f, 1.4f)
            .fireImmune()
            .clientTrackingRange(10)
            .build(MOD_ID + ":larry"));

    public static final RegistryObject<Item> LARRY_SPAWN_EGG = ITEMS.register("larry_spawn_egg", () ->
        new ForgeSpawnEggItem(LARRY, 0xC9C3B4, 0x4A4438, new Item.Properties()));

    public SuperiorTutorial() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(SuperiorTutorial::attributes);
        modBus.addListener(SuperiorTutorial::creativeTabs);
        TutorialConditions.register();
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(LARRY.get(), Larry.createAttributes().build());
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(LARRY_SPAWN_EGG);
    }
}
