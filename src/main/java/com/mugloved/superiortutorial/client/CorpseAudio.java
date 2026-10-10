package com.mugloved.superiortutorial.client;

import com.mugloved.superiortutorial.SuperiorTutorial;
import com.mugloved.superiortutorial.entity.DecayingCorpse;
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
 * The Corpse's sounds, all played by Superior Sounds (this mod plays nothing itself). Two parts:
 * <ul>
 *   <li>His voice: while the local player talks to him, the signal {@code superior_tutorial:corpse_speaking} is
 *   true, and the catalog {@code assets/superior_tutorial/superior_sounds/audio/corpse.json} swaps Story's typing
 *   blip for his own 45 ms voice blips (the same length as the intro's, so they never pile up).</li>
 *   <li>His endings: cue events at set moments of the slay and leave endings, placed at his body.</li>
 * </ul>
 * Without Superior Sounds everything here quietly does nothing.
 */
final class CorpseAudio {
    private static final String SOUNDS_MOD = "superior_sounds";
    private static final String CONVERSATION_SCREEN = "com.mugloved.superiorstory.client.ConversationScreen";
    private static boolean tried;
    private static boolean warned;
    private static int attempts;

    /** Slay (4.4 s): a last breath, bones settle, cloth slumps as he goes limp, a low ring as he fades, dust. */
    private static final Cue[] SLAY = {
        new Cue(0.05, "corpse_slay_breath"),
        new Cue(0.35, "corpse_slay_bones"),
        new Cue(1.20, "corpse_slay_cloth"),
        new Cue(2.15, "corpse_slay_slump"),
        new Cue(2.90, "corpse_slay_release"),
        new Cue(4.30, "corpse_slay_dust"),
    };
    /** Leave (6.0 s): wet rot, the hand drops on bone and grit, roots and moss take him, he settles. */
    private static final Cue[] LEAVE = {
        new Cue(0.60, "corpse_decay_wet"),
        new Cue(1.55, "corpse_decay_bones"),
        new Cue(1.60, "corpse_decay_grit"),
        new Cue(2.40, "corpse_decay_roots"),
        new Cue(3.40, "corpse_decay_moss"),
        new Cue(4.50, "corpse_decay_settle"),
    };

    private record Cue(double at, String event) {}

    private CorpseAudio() {}

    private static boolean available() {
        return ModList.get().isLoaded(SOUNDS_MOD);
    }

    /** Called every client tick; registers the voice signal once Superior Sounds is up. */
    static void ensureRegistered() {
        if (tried || !available()) return;
        // Superior Sounds may not be ready on the very first ticks: try once a second, up to 30 times.
        if (attempts++ % 20 != 0) return;
        try {
            Bridge.register();
            tried = true;
        } catch (Throwable error) {
            if (attempts > 20 * 30) {
                tried = true;
                SuperiorTutorial.LOGGER.warn("Could not register the Corpse's voice with Superior Sounds", error);
            }
        }
    }

    /** Plays the ending cues whose moment fell between {@code before} and {@code now} (seconds into the ending). */
    static void endingCues(DecayingCorpse corpse, double before, double now) {
        Cue[] cues = corpse.clientEnding == DecayingCorpse.PHASE_SLAY ? SLAY
            : corpse.clientEnding == DecayingCorpse.PHASE_LEAVE ? LEAVE : null;
        if (cues == null || !available()) return;
        Vec3 at = corpse.position().add(0.0, 0.6, 0.0);
        for (Cue cue : cues) {
            if (before < cue.at() && now >= cue.at()) emit(cue.event(), at);
        }
    }

    private static void emit(String event, Vec3 at) {
        try {
            Bridge.emit(event, at);
        } catch (Throwable error) {
            if (!warned) {
                warned = true;
                SuperiorTutorial.LOGGER.warn("Could not play a Corpse sound through Superior Sounds", error);
            }
        }
    }

    /** True while the local player has Story's dialogue box open with a Corpse (Story holds its speaker still). */
    static boolean corpseSpeaking() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (screen == null || minecraft.player == null || minecraft.level == null) return false;
        if (!screen.getClass().getName().equals(CONVERSATION_SCREEN)) return false;
        return !minecraft.level.getEntitiesOfClass(DecayingCorpse.class,
            minecraft.player.getBoundingBox().inflate(10.0), Mob::isNoAi).isEmpty();
    }

    /** Everything that touches Superior Sounds, loaded only when it is installed. */
    private static final class Bridge {
        private static final AudioSignalKey<Boolean> SPEAKING =
            new AudioSignalKey<>(new ResourceLocation(SuperiorTutorial.MOD_ID, "corpse_speaking"), Boolean.class);

        static void register() {
            SuperiorServiceRegistry.getRequired(AudioSignalRegistrationApi.class).registerProvider(new AudioSignalProvider() {
                @Override
                public ResourceLocation id() {
                    return new ResourceLocation(SuperiorTutorial.MOD_ID, "corpse");
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
                    emitter.set(SPEAKING, corpseSpeaking());
                }
            });
        }

        static void emit(String event, Vec3 at) {
            SuperiorServiceRegistry.getRequired(SituationalAudioApi.class)
                .emit(new ResourceLocation(SuperiorTutorial.MOD_ID, event), new AudioEventOptions(null, 1.0f, 0.0f, 0, at));
        }
    }
}
