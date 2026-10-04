package com.mugloved.superiortutorial.client;

import com.mugloved.superiortutorial.SuperiorTutorial;
import com.mugloved.superiortutorial.entity.Larry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/** Mug's Larry: the sitting model, his two skins, and his five animations. */
public class LarryModel extends GeoModel<Larry> {
    private static final ResourceLocation MODEL = id("geo/entity/larry.geo.json");
    private static final ResourceLocation ANIMATIONS = id("animations/entity/larry.animation.json");
    static final ResourceLocation NORMAL = id("textures/entity/larry.png");
    static final ResourceLocation DECAYED = id("textures/entity/larry_decayed.png");
    static final ResourceLocation GLOW = id("textures/entity/larry_glow.png");
    /**
     * The normal skin split in two for the leave ending: everything the decayed skin also has ("base"), and the
     * bits of flesh the decayed skin has lost ("flesh"). Together they are exactly larry.png. The flesh fades out
     * while the decayed skin fades in, so nothing pops when he turns fully rotten. Made from Mug's two skins;
     * if either skin changes, these two need making again.
     */
    static final ResourceLocation LEAVE_BASE = id("textures/entity/larry_leave_base.png");
    static final ResourceLocation LEAVE_FLESH = id("textures/entity/larry_leave_flesh.png");
    /** He looks up and down at the player only half as far as he turns: his neck is not what it was. */
    private static final float PITCH_SHARE = 0.5f;

    private static ResourceLocation id(String path) {
        return new ResourceLocation(SuperiorTutorial.MOD_ID, path);
    }

    @Override
    public ResourceLocation getModelResource(Larry larry) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(Larry larry) {
        return switch (larry.clientPhase) {
            case Larry.PHASE_ROTTEN -> DECAYED;
            case Larry.PHASE_LEAVE -> LEAVE_BASE;
            default -> NORMAL;
        };
    }

    @Override
    public ResourceLocation getAnimationResource(Larry larry) {
        return ANIMATIONS;
    }

    /**
     * Runs after the animations are applied. Head turning is added on top of the animated head (GeckoLib's own
     * head turning would replace it, losing his slump and the head movement in his animations), and the body's
     * lean is read for the eye glow, so the glow breathes exactly with him.
     */
    @Override
    public void setCustomAnimations(Larry larry, long instanceId, AnimationState<Larry> state) {
        CoreGeoBone body = getAnimationProcessor().getBone("body");
        if (body != null) {
            LarryView.breathFromBody(larry, (body.getRotX() - body.getInitialSnapshot().getRotX()) * Mth.RAD_TO_DEG);
        }
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        float follow = LarryView.headFollow(larry, state.getPartialTick());
        if (head != null && data != null && follow > 0.0f) {
            head.setRotX(head.getRotX() + data.headPitch() * PITCH_SHARE * follow * Mth.DEG_TO_RAD);
            head.setRotY(head.getRotY() + data.netHeadYaw() * follow * Mth.DEG_TO_RAD);
        }
    }
}
