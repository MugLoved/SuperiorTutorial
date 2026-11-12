package com.mugloved.superiortutorial.story;

import com.google.gson.JsonElement;
import com.mugloved.superiorstory.api.StoryContext;
import com.mugloved.superiorstory.api.StoryHooks;
import com.mugloved.superiortutorial.SuperiorTutorial;
import com.mugloved.superiortutorial.block.SunkenCrateBlock;
import com.mugloved.superiortutorial.entity.FishermanState;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Haskel's quest: a crate went off his dock; fish it out (dive for it, or hook it with a rod) and hand it back.
 * The reward is Haskel's Map (an explorer map to the nearest village) and the Haskelsack (a Supplementaries sack filled
 * from the loot table {@code superior_tutorial:sack/haskel}).
 *
 * <p>Story modules added here: condition {@code "has_haskel_crate": true|false}, action {@code "haskel_reward": true}.
 */
@Mod.EventBusSubscriber(modid = SuperiorTutorial.MOD_ID)
public final class HaskelQuest {
    public static final ResourceLocation SACK = new ResourceLocation("supplementaries", "sack");
    public static final ResourceLocation SACK_LOOT = new ResourceLocation(SuperiorTutorial.MOD_ID, "sack/haskel");
    private static final int SACK_SLOTS = 9;
    /** How far a fishing hook may be from the sunken crate (sideways) to catch it, and the odds per catch. */
    private static final int HOOK_REACH = 6;
    private static final int HOOK_ODDS = 2;

    private HaskelQuest() {}

    public static void register() {
        StoryHooks.registerCondition("has_haskel_crate", HaskelQuest::hasCrateCondition);
        StoryHooks.registerAction("haskel_reward", HaskelQuest::compileReward);
    }

    private static StoryHooks.Condition hasCrateCondition(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("has_haskel_crate must be true or false");
        }
        boolean wanted = value.getAsBoolean();
        return context -> hasCrate(context.player()) == wanted;
    }

    private static StoryHooks.Action compileReward(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean() || !value.getAsBoolean()) {
            throw new IllegalArgumentException("haskel_reward must be true");
        }
        return HaskelQuest::reward;
    }

    // ---- The crate --------------------------------------------------------------------------------------------

    private static Item crateItem() {
        return SuperiorTutorial.HASKEL_CRATE.get();
    }

    public static boolean hasCrate(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(crateItem())) return true;
        }
        return false;
    }

    private static boolean takeCrate(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(crateItem())) {
                stack.shrink(1);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }

    /** Whether this player still needs the crate: not handed in yet, and not already carrying it. */
    private static boolean wantsCrate(ServerPlayer player) {
        return !FishermanState.has(player, FishermanState.DONE) && !hasCrate(player);
    }

    /** Right-clicking (or hitting) the sunken crate. */
    public static void takeFromSunkenCrate(ServerPlayer player, BlockPos pos) {
        if (FishermanState.has(player, FishermanState.DONE)) {
            player.displayClientMessage(Component.translatable("superior_tutorial.fisherman.crate.done"), true);
            return;
        }
        if (hasCrate(player)) {
            player.displayClientMessage(Component.translatable("superior_tutorial.fisherman.crate.have"), true);
            return;
        }
        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(crateItem()));
        player.displayClientMessage(Component.translatable("superior_tutorial.fisherman.crate.taken"), true);
        player.serverLevel().sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 16, 0.3, 0.2, 0.3, 0.05);
    }

    /** A fishing hook near the sunken crate can bring the crate up instead of a fish (one catch in two). */
    @SubscribeEvent
    public static void fished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        FishingHook hook = event.getHookEntity();
        if (hook == null || !wantsCrate(player) || player.getRandom().nextInt(HOOK_ODDS) != 0) return;
        if (!nearSunkenCrate(player.serverLevel(), hook.blockPosition())) return;
        event.setCanceled(true);
        event.damageRodBy(1);
        ItemEntity item = new ItemEntity(hook.level(), hook.getX(), hook.getY(), hook.getZ(), new ItemStack(crateItem()));
        // Thrown toward the angler, the way vanilla reels in a catch.
        double dx = player.getX() - hook.getX();
        double dy = player.getY() - hook.getY();
        double dz = player.getZ() - hook.getZ();
        item.setDeltaMovement(dx * 0.1, dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08, dz * 0.1);
        hook.level().addFreshEntity(item);
        player.displayClientMessage(Component.translatable("superior_tutorial.fisherman.crate.hooked"), true);
    }

    private static boolean nearSunkenCrate(ServerLevel level, BlockPos hook) {
        for (BlockPos pos : BlockPos.betweenClosed(hook.offset(-HOOK_REACH, -14, -HOOK_REACH), hook.offset(HOOK_REACH, 2, HOOK_REACH))) {
            if (level.getBlockState(pos).getBlock() instanceof SunkenCrateBlock) return true;
        }
        return false;
    }

    // ---- The reward -------------------------------------------------------------------------------------------

    private static void reward(StoryContext context) {
        ServerPlayer player = context.player();
        if (FishermanState.has(player, FishermanState.DONE) || !takeCrate(player)) return;
        ItemStack map = villageMap(player);
        if (map == null) player.displayClientMessage(Component.translatable("superior_tutorial.fisherman.no_village"), false);
        for (ItemStack stack : sack(player, map)) ItemHandlerHelper.giveItemToPlayer(player, stack);
        FishermanState.set(player, FishermanState.DONE);
    }

    /**
     * An explorer map centred on the nearest village, or null if none is in reach. Villages are the structures in
     * #minecraft:village plus any structure whose name has "village" in it (modded villages are often not tagged).
     */
    private static ItemStack villageMap(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<Holder<Structure>> villages = level.registryAccess().registryOrThrow(Registries.STRUCTURE).holders()
            .filter(holder -> holder.is(StructureTags.VILLAGE) || holder.key().location().getPath().contains("village"))
            .<Holder<Structure>>map(holder -> holder)
            .toList();
        if (villages.isEmpty()) return null;
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
            .findNearestMapStructure(level, HolderSet.direct(villages), player.blockPosition(), 100, false);
        if (found == null) return null;
        BlockPos village = found.getFirst();
        ItemStack map = MapItem.create(level, village.getX(), village.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        MapItemSavedData.addTargetDecoration(map, village, "+", MapDecoration.Type.RED_X);
        map.setHoverName(Component.translatable("item.superior_tutorial.haskel_map").withStyle(style -> style.withItalic(false)));
        return map;
    }

    /**
     * The Haskelsack with the loot table inside, the map taking the fish stew's place (or the last slot); without
     * Supplementaries, the loot itself.
     */
    private static List<ItemStack> sack(ServerPlayer player, ItemStack map) {
        LootTable table = player.server.getLootData().getLootTable(SACK_LOOT);
        LootParams params = new LootParams.Builder(player.serverLevel())
            .withParameter(LootContextParams.ORIGIN, player.position())
            .withParameter(LootContextParams.THIS_ENTITY, player)
            .create(LootContextParamSets.CHEST);
        List<ItemStack> loot = new ArrayList<>(table.getRandomItems(params));
        if (map != null) {
            int stew = -1;
            for (int i = 0; i < loot.size(); i++) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(loot.get(i).getItem());
                if (id != null && id.getPath().equals("fish_stew")) stew = i;
            }
            if (stew >= 0) loot.set(stew, map);
            else if (loot.size() >= SACK_SLOTS) loot.set(SACK_SLOTS - 1, map);
            else loot.add(map);
        }
        Item sackItem = ForgeRegistries.ITEMS.getValue(SACK);
        if (sackItem == null || sackItem == Items.AIR) {
            SuperiorTutorial.LOGGER.warn("Supplementaries is not installed; giving the Haskelsack's contents loose.");
            return loot;
        }
        NonNullList<ItemStack> slots = NonNullList.withSize(SACK_SLOTS, ItemStack.EMPTY);
        NonNullList<ItemStack> extra = NonNullList.create();
        int next = 0;
        for (ItemStack stack : loot) {
            if (next < SACK_SLOTS) slots.set(next++, stack);
            else extra.add(stack);
        }
        ItemStack sack = new ItemStack(sackItem);
        CompoundTag blockEntity = new CompoundTag();
        ContainerHelper.saveAllItems(blockEntity, slots);
        sack.addTagElement("BlockEntityTag", blockEntity);
        sack.setHoverName(Component.translatable("item.superior_tutorial.haskelsack").withStyle(style -> style.withItalic(false)));
        NonNullList<ItemStack> result = NonNullList.create();
        result.add(sack);
        result.addAll(extra);
        return result;
    }
}
