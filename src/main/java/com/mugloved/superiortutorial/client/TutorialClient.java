package com.mugloved.superiortutorial.client;

import com.mugloved.superiortutorial.SuperiorTutorial;
import com.mugloved.superiortutorial.entity.Larry;
import com.mugloved.superiortutorial.entity.LarryState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client wiring: Larry's renderer, and the dissolve / decay moment when the local player's state changes. */
public final class TutorialClient {
    private TutorialClient() {}

    @Mod.EventBusSubscriber(modid = SuperiorTutorial.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModEvents {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(SuperiorTutorial.LARRY.get(), LarryRenderer::new);
        }
    }

    @Mod.EventBusSubscriber(modid = SuperiorTutorial.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        /** 0 = untouched, 1 = left, 2 = slain; -1 = not known yet. */
        private static int lastState = -1;
        private static ClientLevel lastLevel;
        private static int settleTicks;

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft minecraft = Minecraft.getInstance();
            ClientLevel level = minecraft.level;
            if (level == null || minecraft.player == null) {
                lastLevel = null;
                lastState = -1;
                return;
            }
            if (level != lastLevel) {
                // Joining or changing worlds: unlock keys arrive a moment after the world does, so settle first.
                lastLevel = level;
                lastState = -1;
                settleTicks = 100;
            }
            int state = LarryState.slainHere() ? 2 : LarryState.leftHere() ? 1 : 0;
            if (settleTicks > 0) {
                settleTicks--;
                lastState = state;
                return;
            }
            if (state != lastState && lastState != -1 && state > lastState) {
                for (Larry larry : level.getEntitiesOfClass(Larry.class, minecraft.player.getBoundingBox().inflate(48.0))) {
                    if (state == 2) burst(level, larry, ParticleTypes.SOUL, 24, ParticleTypes.SMOKE, 16);
                    else burst(level, larry, ParticleTypes.WHITE_ASH, 40, ParticleTypes.SMOKE, 8);
                }
            }
            lastState = state;
        }

        private static void burst(ClientLevel level, Larry larry, ParticleOptions main, int mainCount, ParticleOptions extra, int extraCount) {
            var random = level.random;
            for (int i = 0; i < mainCount + extraCount; i++) {
                ParticleOptions type = i < mainCount ? main : extra;
                double x = larry.getX() + (random.nextDouble() - 0.5) * 0.7;
                double y = larry.getY() + random.nextDouble() * 1.1;
                double z = larry.getZ() + (random.nextDouble() - 0.5) * 0.7;
                level.addParticle(type, x, y, z, (random.nextDouble() - 0.5) * 0.02, 0.02 + random.nextDouble() * 0.04, (random.nextDouble() - 0.5) * 0.02);
            }
        }
    }
}
