package com.mugloved.superiortutorial;

import com.mugloved.superiortutorial.block.SunkenCrateBlock;
import com.mugloved.superiortutorial.entity.DecayingCorpse;
import com.mugloved.superiortutorial.entity.Fisherman;
import com.mugloved.superiortutorial.item.HaskelCrateItem;
import com.mugloved.superiortutorial.story.CorpseSatchel;
import com.mugloved.superiortutorial.story.HaskelQuest;
import com.mugloved.superiortutorial.story.TutorialConditions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
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
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MOD_ID);

    /** The Corpse's sounds (see assets/superior_tutorial/sounds.json); Superior Sounds plays them. */
    private static final String[] SOUND_NAMES = {
        "corpse.voice",
        "corpse.slay.breath", "corpse.slay.bones", "corpse.slay.cloth", "corpse.slay.slump", "corpse.slay.release", "corpse.slay.dust",
        "corpse.decay.wet", "corpse.decay.bones", "corpse.decay.grit", "corpse.decay.roots", "corpse.decay.moss", "corpse.decay.settle",
        "fisherman.voice", "fisherman.vanish", "fisherman.appear",
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

    /** Haskel Hobb, the fisherman in the cove. About a villager's size. */
    public static final RegistryObject<EntityType<Fisherman>> FISHERMAN = ENTITIES.register("fisherman", () ->
        EntityType.Builder.of(Fisherman::new, MobCategory.MISC)
            .sized(0.6f, 1.95f)
            .fireImmune()
            .clientTrackingRange(10)
            .build(MOD_ID + ":fisherman"));

    public static final RegistryObject<Item> FISHERMAN_SPAWN_EGG = ITEMS.register("fisherman_spawn_egg", () ->
        new ForgeSpawnEggItem(FISHERMAN, 0xB0562A, 0x56702F, new Item.Properties()));

    /** The crate on the pond floor; it can't be broken in survival (see SunkenCrateBlock). */
    public static final RegistryObject<Block> SUNKEN_CRATE = BLOCKS.register("sunken_crate", () ->
        new SunkenCrateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(-1.0f, 3600000.0f)
            .sound(SoundType.WOOD).noLootTable()));

    public static final RegistryObject<Item> SUNKEN_CRATE_ITEM = ITEMS.register("sunken_crate", () ->
        new BlockItem(SUNKEN_CRATE.get(), new Item.Properties()));

    /** Haskel's Crate as the player carries it. */
    public static final RegistryObject<Item> HASKEL_CRATE = ITEMS.register("haskel_crate", () ->
        new HaskelCrateItem(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.UNCOMMON)));

    public SuperiorTutorial() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modBus);
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TutorialConfig.SPEC);
        modBus.addListener(SuperiorTutorial::attributes);
        modBus.addListener(SuperiorTutorial::creativeTabs);
        TutorialConditions.register();
        CorpseSatchel.register();
        HaskelQuest.register();
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(DECAYING_CORPSE.get(), DecayingCorpse.createAttributes().build());
        event.put(FISHERMAN.get(), Fisherman.createAttributes().build());
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(DECAYING_CORPSE_SPAWN_EGG);
            event.accept(FISHERMAN_SPAWN_EGG);
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(SUNKEN_CRATE_ITEM);
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(HASKEL_CRATE);
    }
}
