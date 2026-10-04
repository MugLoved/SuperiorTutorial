package com.mugloved.superiortutorial.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Catches a swing at Larry before anything else can swallow it. Combat mods can cancel or reroute an attack so
 * that his own hurt method is never reached; this listens last, and also to attacks others cancelled, so he
 * still flinches. On the server it makes him shake for everyone watching; on the client it makes him flinch at
 * once for the player who swung, without waiting for the network.
 */
@Mod.EventBusSubscriber(modid = com.mugloved.superiortutorial.SuperiorTutorial.MOD_ID)
public final class LarryHitEvents {
    private LarryHitEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void attack(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof Larry larry)) return;
        Player player = event.getEntity();
        if (player.getAbilities().instabuild) return;
        if (player instanceof ServerPlayer serverPlayer) {
            larry.reactToHit(serverPlayer);
        } else if (player.level().isClientSide) {
            larry.shakeNow();
        }
    }
}
