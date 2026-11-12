package com.mugloved.superiortutorial.client;

import com.mugloved.superiortutorial.SuperiorTutorial;
import com.mugloved.superiortutorial.entity.Fisherman;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/** Haskel Hobb: Mug's Legendary Pack trader with the colour swap, the rod and the stance, and his five animations. */
public class FishermanModel extends GeoModel<Fisherman> {
    private static final ResourceLocation MODEL = id("geo/entity/fisherman.geo.json");
    private static final ResourceLocation TEXTURE = id("textures/entity/fisherman.png");
    private static final ResourceLocation ANIMATIONS = id("animations/entity/fisherman.animation.json");
    /** He looks up and down a little less than he turns: an old neck under a heavy hat. */
    private static final float PITCH_SHARE = 0.7f;

    private static ResourceLocation id(String path) {
        return new ResourceLocation(SuperiorTutorial.MOD_ID, path);
    }

    @Override
    public ResourceLocation getModelResource(Fisherman fisherman) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(Fisherman fisherman) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(Fisherman fisherman) {
        return ANIMATIONS;
    }

    /**
     * The head turn toward the player is added on top of the animated head, so his idle head drift and his stance
     * (head turned back toward the front) are kept rather than replaced.
     */
    @Override
    public void setCustomAnimations(Fisherman fisherman, long instanceId, AnimationState<Fisherman> state) {
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        float yaw = 0.0f;
        float pitch = 0.0f;
        if (head != null && data != null) {
            yaw = Mth.clamp(data.netHeadYaw(), -Fisherman.MAX_HEAD_TURN, Fisherman.MAX_HEAD_TURN) * Mth.DEG_TO_RAD;
            pitch = Mth.clamp(data.headPitch() * PITCH_SHARE, -15.0f, 15.0f) * Mth.DEG_TO_RAD;
            head.setRotX(head.getRotX() + pitch);
            head.setRotY(head.getRotY() + yaw);
        }
        fisherman.clientHeadYawAdded = yaw;
        fisherman.clientHeadPitchAdded = pitch;
    }
}
