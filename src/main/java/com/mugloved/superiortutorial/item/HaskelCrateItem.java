package com.mugloved.superiortutorial.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Haskel's Crate as carried: it can't be placed, and it doesn't burn (it's been in a pond). */
public class HaskelCrateItem extends Item {
    public HaskelCrateItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.superior_tutorial.haskel_crate.tooltip").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
