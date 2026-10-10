package com.mugloved.superiortutorial.client;

import com.mugloved.superiortutorial.SuperiorTutorial;
import com.mugloved.superiortutorial.entity.DecayingCorpse;
import com.mugloved.superiortutorial.entity.DecayingCorpseState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client wiring: the Corpse's renderer, and starting his ending when the local player's state changes. */
public final class TutorialClient {
    private TutorialClient() {}

    @Mod.EventBusSubscriber(modid = SuperiorTutorial.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModEvents {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(SuperiorTutorial.DECAYING_CORPSE.get(), DecayingCorpseRenderer::new);
        }
    }

    @Mod.EventBusSubscriber(modid = SuperiorTutorial.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        /** 0 = untouched, 1 = left, 2 = slain; -1 = not known yet. */
        private static int lastState = -1;
        private static ClientLevel lastLevel;
        private static int settleTicks;
        /** Ticks the main animation takes to blend into an ending; the ending's clock starts after it. */
        private static final int BLEND_TICKS = 3;

        /** The local player's state as of the last client tick: 0, 1 (left) or 2 (slain); -1 when not known. */
        static int settledState() {
            return lastState;
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft minecraft = Minecraft.getInstance();
            ClientLevel level = minecraft.level;
            CorpseAudio.ensureRegistered();
            if (level == null || minecraft.player == null) {
                lastLevel = null;
                lastState = -1;
                return;
            }
            if (level != lastLevel) {
                // Joining or changing worlds: unlock keys arrive a moment after the world does, so settle first.
                // Anything already decided then simply shows as it is (gone or rotten), with no ending replayed.
                lastLevel = level;
                lastState = -1;
                settleTicks = 100;
            }
            int state = DecayingCorpseState.slainHere() ? 2 : DecayingCorpseState.leftHere() ? 1 : 0;
            var nearby = level.getEntitiesOfClass(DecayingCorpse.class, minecraft.player.getBoundingBox().inflate(48.0));
            if (settleTicks > 0) {
                settleTicks--;
            } else if (lastState != -1 && state != lastState) {
                for (DecayingCorpse corpse : nearby) {
                    if (state > lastState) {
                        // The ending was just chosen: play it from the start.
                        DecayingCorpseView.update(corpse, 0.0f);
                        corpse.clientGlowAtEnding = DecayingCorpseView.glow(corpse, 0.0f);
                        corpse.clientEnding = state == 2 ? DecayingCorpse.PHASE_SLAY : DecayingCorpse.PHASE_LEAVE;
                        corpse.clientEndingStart = level.getGameTime() + BLEND_TICKS;
                        corpse.clientEffectsDone = 0.0;
                    } else {
                        // Keys taken back (testing with /superior_lib lock): he is simply waiting again.
                        corpse.clientEnding = DecayingCorpse.PHASE_IDLE;
                        corpse.clientEndingStart = -1;
                    }
                }
            }
            lastState = state;
            for (DecayingCorpse corpse : nearby) DecayingCorpseEffects.tick(level, corpse);
        }
    }
}
