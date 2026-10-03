package com.mugloved.superiortutorial.story;

import com.google.gson.JsonElement;
import com.mugloved.superiorstory.api.StoryHooks;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;

/** Story condition modules added by the tutorial. */
public final class TutorialConditions {
    private TutorialConditions() {}

    public static void register() {
        // "armed": true when the player holds a weapon in the main hand; "armed": false when not.
        StoryHooks.registerCondition("armed", TutorialConditions::armed);
    }

    private static StoryHooks.Condition armed(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("armed must be true or false");
        }
        boolean wanted = value.getAsBoolean();
        return context -> isWeapon(context.player().getMainHandItem()) == wanted;
    }

    /**
     * Any weapon counts: a bow or crossbow, a trident, or anything that adds attack damage in the main hand
     * (vanilla swords and axes, and modular weapons, which report their damage the same way).
     */
    public static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof ProjectileWeaponItem || stack.getItem() instanceof TridentItem) return true;
        for (AttributeModifier modifier : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
            if (modifier.getAmount() > 0) return true;
        }
        return false;
    }
}
