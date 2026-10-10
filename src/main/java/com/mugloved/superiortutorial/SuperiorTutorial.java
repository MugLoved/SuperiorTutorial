package com.mugloved.superiortutorial;

import com.mugloved.superiortutorial.entity.DecayingCorpse;
import com.mugloved.superiortutorial.story.CorpseSatchel;
import com.mugloved.superiortutorial.story.TutorialConditions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
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
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MOD_ID);

    /** The Corpse's sounds (see assets/superior_tutorial/sounds.json); Superior Sounds plays them. */
    private static final String[] SOUND_NAMES = {
        "corpse.voice",
        "corpse.slay.breath", "corpse.slay.bones", "corpse.slay.cloth", "corpse.slay.slump", "corpse.slay.release", "corpse.slay.dust",
        "corpse.decay.wet", "corpse.decay.bones", "corpse.decay.grit", "corpse.decay.roots", "corpse.decay.moss", "corpse.decay.settle",
    };

    static {
        for (String name : SOUND_NAMES) {
            SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MOD_ID, name)));
        }
    }

    public static final RegistryObject<EntityType<DecayingCorpse>> DECAYING_CORPSE = ENTITIES.register("decaying_corpse", () ->
        EntityType.Builder.of(DecayingCorpse::new, MobCategory.MISC)
            .sized(1.3f, 1.45f)          // his sitting model: about 1.4 wide, 1.2 deep, 1.45 tall
            .fireImmune()
            .clientTrackingRange(10)
            .build(MOD_ID + ":decaying_corpse"));

    public static final RegistryObject<Item> DECAYING_CORPSE_SPAWN_EGG = ITEMS.register("decaying_corpse_spawn_egg", () ->
        new ForgeSpawnEggItem(DECAYING_CORPSE, 0xC9C3B4, 0x4A4438, new Item.Properties()));

    public SuperiorTutorial() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(SuperiorTutorial::attributes);
        modBus.addListener(SuperiorTutorial::creativeTabs);
        TutorialConditions.register();
        CorpseSatchel.register();
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(DECAYING_CORPSE.get(), DecayingCorpse.createAttributes().build());
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(DECAYING_CORPSE_SPAWN_EGG);
    }
}
