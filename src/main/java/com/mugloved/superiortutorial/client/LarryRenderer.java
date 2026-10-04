package com.mugloved.superiortutorial.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mugloved.superiortutorial.entity.Larry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Draws Larry for the local player: waiting, the ending as it plays, rotten, or not at all.
 * The state is that player's own, so each player in a world sees their own Larry.
 */
public class LarryRenderer extends GeoEntityRenderer<Larry> {
    /** His lowest point (a foot) sits this far below the model's origin; lift him so he rests on the floor. */
    static final double LIFT = 0.83 / 16.0;
    /** Moved this far forward so his back and head rest against a wall behind his block instead of sinking in. */
    static final double FORWARD = 2.0 / 16.0;

    public LarryRenderer(EntityRendererProvider.Context context) {
        super(context, new LarryModel());
        shadowRadius = 0.5f;
        addRenderLayer(new DecayLayer(this));
        addRenderLayer(new GlowLayer(this));
    }

    @Override
    public boolean shouldRender(Larry larry, Frustum frustum, double x, double y, double z) {
        if (LarryView.update(larry, 0.0f) == Larry.PHASE_GONE) return false;
        return super.shouldRender(larry, frustum, x, y, z);
    }

    @Override
    public void render(Larry larry, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (LarryView.update(larry, partialTick) == Larry.PHASE_GONE) return;
        // His shadow fades with him (the game draws it after this, from this value).
        shadowStrength = LarryView.alpha(larry, partialTick);
        float facing = larry.yBodyRot * Mth.DEG_TO_RAD;
        pose.pushPose();
        pose.translate(-Mth.sin(facing) * FORWARD, LIFT, Mth.cos(facing) * FORWARD);
        super.render(larry, yaw, partialTick, pose, buffers, light);
        pose.popPose();
    }

    /** While he fades away (end of the slay ending) he is drawn see-through. */
    @Override
    public RenderType getRenderType(Larry larry, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        if (LarryView.alpha(larry, partialTick) < 0.999f) return RenderType.entityTranslucent(texture);
        return super.getRenderType(larry, texture, buffers, partialTick);
    }

    @Override
    public Color getRenderColor(Larry larry, float partialTick, int light) {
        return Color.ofRGBA(1.0f, 1.0f, 1.0f, LarryView.alpha(larry, partialTick));
    }

    /**
     * During the leave ending: the decayed skin grows in over him, and the flesh it no longer has fades away.
     * (The body underneath is drawn with the part of the normal skin both share; see {@link LarryModel#LEAVE_BASE}.)
     */
    private static final class DecayLayer extends GeoRenderLayer<Larry> {
        private DecayLayer(LarryRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack pose, Larry larry, BakedGeoModel model, RenderType type, MultiBufferSource buffers,
                           VertexConsumer buffer, float partialTick, int light, int overlay) {
            if (larry.clientPhase != Larry.PHASE_LEAVE) return;
            float amount = LarryView.decay(larry, partialTick);
            if (amount > 0.005f) draw(model, pose, buffers, larry, LarryModel.DECAYED, partialTick, light, overlay, amount);
            if (amount < 0.995f) draw(model, pose, buffers, larry, LarryModel.LEAVE_FLESH, partialTick, light, overlay, 1.0f - amount);
        }

        private void draw(BakedGeoModel model, PoseStack pose, MultiBufferSource buffers, Larry larry, ResourceLocation texture,
                          float partialTick, int light, int overlay, float alpha) {
            RenderType type = RenderType.entityTranslucent(texture);
            getRenderer().reRender(model, pose, buffers, larry, type, buffers.getBuffer(type), partialTick, light, overlay, 1.0f, 1.0f, 1.0f, alpha);
        }
    }

    /** His eyes: drawn full-bright over everything, as strong as {@link LarryView#glow} says. */
    private static final class GlowLayer extends GeoRenderLayer<Larry> {
        private GlowLayer(LarryRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack pose, Larry larry, BakedGeoModel model, RenderType type, MultiBufferSource buffers,
                           VertexConsumer buffer, float partialTick, int light, int overlay) {
            float strength = LarryView.glow(larry, partialTick) * LarryView.alpha(larry, partialTick);
            if (strength < 0.005f) return;
            RenderType eyes = RenderType.eyes(LarryModel.GLOW);
            // The eyes render type adds light, so darker colour means a weaker glow.
            getRenderer().reRender(model, pose, buffers, larry, eyes, buffers.getBuffer(eyes), partialTick, light, overlay, strength, strength, strength, 1.0f);
        }
    }
}
