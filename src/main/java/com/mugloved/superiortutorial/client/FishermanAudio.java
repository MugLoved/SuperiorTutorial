package com.mugloved.superiortutorial.client;

import com.mugloved.superiortutorial.SuperiorTutorial;
import com.mugloved.superiortutorial.entity.Fisherman;
import com.superior.lib.api.service.SuperiorServiceRegistry;
import com.superior.sounds.api.audio.AudioEventOptions;
import com.superior.sounds.api.audio.AudioProviderAuthority;
import com.superior.sounds.api.audio.AudioProviderSchedule;
import com.superior.sounds.api.audio.AudioSignalEmitter;
import com.superior.sounds.api.audio.AudioSignalKey;
import com.superior.sounds.api.audio.AudioSignalProvider;
import com.superior.sounds.api.audio.AudioSignalRegistrationApi;
import com.superior.sounds.api.audio.AudioSignalUpdateContext;
import com.superior.sounds.api.audio.SituationalAudioApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import java.util.Set;

/**
 * Haskel's sounds, all played by Superior Sounds (this mod plays nothing itself), the same way as the Corpse's:
 * <ul>
 *   <li>His voice: while the local player talks to him, the signal {@code superior_tutorial:fisherman_speaking} is
 *   true and the catalog {@code superior_sounds/audio/fisherman.json} swaps Story's typing blip for his blips.</li>
 *   <li>His smoke: the events {@code superior_tutorial:fisherman_vanish} and {@code fisherman_appear}, at his body.</li>
 * </ul>
 */
final class FishermanAudio {
    private static final String SOUNDS_MOD = "superior_sounds";
    private static final String CONVERSATION_SCREEN = "com.mugloved.superiorstory.client.ConversationScreen";
    private static boolean tried;
    private static boolean warned;
    private static int attempts;

    private FishermanAudio() {}

    private static boolean available() {
        return ModList.get().isLoaded(SOUNDS_MOD);
    }

    static void ensureRegistered() {
        if (tried || !available()) return;
        if (attempts++ % 20 != 0) return;
        try {
            Bridge.register();
            tried = true;
        } catch (Throwable error) {
            if (attempts > 20 * 30) {
                tried = true;
                SuperiorTutorial.LOGGER.warn("Could not register Haskel's voice with Superior Sounds", error);
            }
        }
    }

    /** Plays the smoke sound the entity asked for (set when the vanish or appear event arrived). */
    static void pending(Fisherman fisherman) {
        int cue = fisherman.clientPendingSound;
        if (cue == 0) return;
        fisherman.clientPendingSound = 0;
        if (!available()) return;
        try {
            Bridge.emit(cue == 1 ? "fisherman_vanish" : "fisherman_appear", fisherman.position().add(0.0, 1.0, 0.0));
        } catch (Throwable error) {
            if (!warned) {
                warned = true;
                SuperiorTutorial.LOGGER.warn("Could not play Haskel's smoke through Superior Sounds", error);
            }
        }
    }

    /**
     * Now and then, while he isn't talking or hidden in smoke, a low villager grumble (event
     * {@code superior_tutorial:fisherman_ambient}; the sound is a villager's idle at 0.72 pitch, which also slows
     * it, see sounds.json). Each player's game times its own.
     */
    static void ambient(Fisherman fisherman, net.minecraft.util.RandomSource random, long now) {
        if (fisherman.isNoAi() || fisherman.clientHidden) {
            fisherman.clientNextAmbient = -1;
            return;
        }
        if (fisherman.clientNextAmbient < 0) {
            fisherman.clientNextAmbient = now + 20L * (10 + random.nextInt(16));
            return;
        }
        if (now < fisherman.clientNextAmbient) return;
        fisherman.clientNextAmbient = -1;
        if (!available()) return;
        try {
            Bridge.emit("fisherman_ambient", fisherman.position().add(0.0, 1.6, 0.0));
        } catch (Throwable error) {
            if (!warned) {
                warned = true;
                SuperiorTutorial.LOGGER.warn("Could not play Haskel's grumble through Superior Sounds", error);
            }
        }
    }

    /** True while the local player has Story's dialogue box open with Haskel (Story holds its speaker still). */
    static boolean speaking() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (screen == null || minecraft.player == null || minecraft.level == null) return false;
        if (!screen.getClass().getName().equals(CONVERSATION_SCREEN)) return false;
        return !minecraft.level.getEntitiesOfClass(Fisherman.class,
            minecraft.player.getBoundingBox().inflate(10.0), Mob::isNoAi).isEmpty();
    }

    private static final class Bridge {
        private static final AudioSignalKey<Boolean> SPEAKING =
            new AudioSignalKey<>(new ResourceLocation(SuperiorTutorial.MOD_ID, "fisherman_speaking"), Boolean.class);

        static void register() {
            SuperiorServiceRegistry.getRequired(AudioSignalRegistrationApi.class).registerProvider(new AudioSignalProvider() {
                @Override
                public ResourceLocation id() {
                    return new ResourceLocation(SuperiorTutorial.MOD_ID, "fisherman");
                }

                @Override
                public AudioProviderAuthority authority() {
                    return AudioProviderAuthority.CLIENT_LOCAL;
                }

                @Override
                public AudioProviderSchedule schedule() {
                    return AudioProviderSchedule.every(1);
                }

                @Override
                public Set<AudioSignalKey<?>> publishedKeys() {
                    return Set.of(SPEAKING);
                }

                @Override
                public void collect(AudioSignalUpdateContext context, AudioSignalEmitter emitter) {
                    emitter.set(SPEAKING, speaking());
                }
            });
        }

        static void emit(String event, Vec3 at) {
            SuperiorServiceRegistry.getRequired(SituationalAudioApi.class)
                .emit(new ResourceLocation(SuperiorTutorial.MOD_ID, event), new AudioEventOptions(null, 1.0f, 0.0f, 0, at));
        }
    }
}
