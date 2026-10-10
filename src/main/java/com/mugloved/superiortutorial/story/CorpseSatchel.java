package com.mugloved.superiortutorial.story;

import com.google.gson.JsonElement;
import com.mugloved.superiorstory.api.StoryContext;
import com.mugloved.superiorstory.api.StoryHooks;
import com.mugloved.superiortutorial.SuperiorTutorial;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * The Corpse's Satchel: a Sophisticated Backpack that fills itself from the loot table
 * {@code superior_tutorial:satchel/corpse} the first time it is opened (2 oak planks, 1 cobblestone, 2 andesite alloy).
 * Written in dialogue as {@code "corpse_satchel": true} on a choice or line. Without Sophisticated Backpacks the
 * player simply gets the loot table's items.
 */
public final class CorpseSatchel {
    public static final ResourceLocation BACKPACK = new ResourceLocation("sophisticatedbackpacks", "backpack");
    public static final ResourceLocation LOOT = new ResourceLocation(SuperiorTutorial.MOD_ID, "satchel/corpse");

    private CorpseSatchel() {}

    public static void register() {
        StoryHooks.registerAction("corpse_satchel", CorpseSatchel::compile);
    }

    private static StoryHooks.Action compile(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean() || !value.getAsBoolean()) {
            throw new IllegalArgumentException("corpse_satchel must be true");
        }
        return CorpseSatchel::give;
    }

    private static void give(StoryContext context) {
        ServerPlayer player = context.player();
        for (ItemStack stack : contents(player)) {
            ItemHandlerHelper.giveItemToPlayer(player, stack);
        }
    }

    private static List<ItemStack> contents(ServerPlayer player) {
        Item backpack = ForgeRegistries.ITEMS.getValue(BACKPACK);
        if (backpack != null && backpack != Items.AIR) {
            ItemStack satchel = new ItemStack(backpack);
            CompoundTag tag = satchel.getOrCreateTag();
            tag.putString("lootTableName", LOOT.toString());
            tag.putFloat("lootPercentage", 1.0f);
            satchel.setHoverName(Component.translatable("item.superior_tutorial.corpse_satchel").withStyle(style -> style.withItalic(false)));
            return List.of(satchel);
        }
        SuperiorTutorial.LOGGER.warn("Sophisticated Backpacks is not installed; giving the satchel's contents loose.");
        LootTable table = player.server.getLootData().getLootTable(LOOT);
        LootParams params = new LootParams.Builder(player.serverLevel())
            .withParameter(LootContextParams.ORIGIN, player.position())
            .withParameter(LootContextParams.THIS_ENTITY, player)
            .create(LootContextParamSets.CHEST);
        return table.getRandomItems(params);
    }
}
