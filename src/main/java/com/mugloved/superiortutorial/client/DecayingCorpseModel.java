package com.mugloved.superiortutorial.client;

import com.mugloved.superiortutorial.SuperiorTutorial;
import com.mugloved.superiortutorial.entity.DecayingCorpse;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/** Mug's the Corpse: the sitting model, his two skins, and his five animations. */
public class DecayingCorpseModel extends GeoModel<DecayingCorpse> {
    private static final ResourceLocation MODEL = id("geo/entity/decaying_corpse.geo.json");
    private static final ResourceLocation ANIMATIONS = id("animations/entity/decaying_corpse.animation.json");
    static final ResourceLocation NORMAL = id("textures/entity/decaying_corpse.png");
    static final ResourceLocation DECAYED = id("textures/entity/decaying_corpse_decayed.png");
    static final ResourceLocation GLOW = id("textures/entity/decaying_corpse_glow.png");
    /**
     * The normal skin split in two for the leave ending: everything the decayed skin also has ("base"), and the
     * bits of flesh the decayed skin has lost ("flesh"). Together they are exactly corpse.png. The flesh fades out
     * while the decayed skin fades in, so nothing pops when he turns fully rotten. Made from Mug's two skins;
     * if either skin changes, these two need making again.
     */
    static final ResourceLocation LEAVE_BASE = id("textures/entity/decaying_corpse_leave_base.png");
    static final ResourceLocation LEAVE_FLESH = id("textures/entity/decaying_corpse_leave_flesh.png");
    /** He follows the player up and down a little less than fully: his neck is not what it was. */
    private static final float PITCH_SHARE = 0.75f;

    private static ResourceLocation id(String path) {
        return new ResourceLocation(SuperiorTutorial.MOD_ID, path);
    }

    @Override
    public ResourceLocation getModelResource(DecayingCorpse corpse) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(DecayingCorpse corpse) {
        return switch (corpse.clientPhase) {
            case DecayingCorpse.PHASE_ROTTEN -> DECAYED;
            case DecayingCorpse.PHASE_LEAVE -> LEAVE_BASE;
            default -> NORMAL;
        };
    }

    @Override
    public ResourceLocation getAnimationResource(DecayingCorpse corpse) {
        return ANIMATIONS;
    }

    /**
     * Runs after the animations are applied. Head turning is added on top of the animated head (GeckoLib's own
     * head turning would replace it, losing his slump and the head movement in his animations), and the body's
     * lean is read for the eye glow, so the glow breathes exactly with him.
     */
    @Override
    public void setCustomAnimations(DecayingCorpse corpse, long instanceId, AnimationState<DecayingCorpse> state) {
        CoreGeoBone body = getAnimationProcessor().getBone("body");
        if (body != null) {
            DecayingCorpseView.breathFromBody(corpse, (body.getRotX() - body.getInitialSnapshot().getRotX()) * Mth.RAD_TO_DEG);
        }
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        float follow = DecayingCorpseView.headFollow(corpse, state.getPartialTick());
        float yaw = 0.0f;
        float pitch = 0.0f;
        if (head != null && data != null && follow > 0.0f) {
            // His head hangs forward at rest, so when someone is in front of him he lifts it to their eyes.
            float lift = DecayingCorpseView.LOOK_UP * DecayingCorpseView.engage(corpse, state.getPartialTick());
            pitch = (data.headPitch() * PITCH_SHARE + lift) * follow * Mth.DEG_TO_RAD;
            yaw = data.netHeadYaw() * follow * Mth.DEG_TO_RAD;
            head.setRotX(head.getRotX() + pitch);
            head.setRotY(head.getRotY() + yaw);
        }
        // Remembered so the dialogue portrait can show him looking straight out (see DecayingCorpseRenderer).
        corpse.clientHeadPitchAdded = pitch;
        corpse.clientHeadYawAdded = yaw;
    }
}
