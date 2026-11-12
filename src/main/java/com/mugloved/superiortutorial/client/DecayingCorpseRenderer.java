package com.mugloved.superiortutorial.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mugloved.superiortutorial.entity.DecayingCorpse;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Draws the Corpse for the local player: waiting, the ending as it plays, rotten, or not at all.
 * The state is that player's own, so each player in a world sees their own the Corpse.
 */
public class DecayingCorpseRenderer extends GeoEntityRenderer<DecayingCorpse> {
    /** His lowest point (a foot) sits this far below the model's origin; lift him so he rests on the floor. */
    static final double LIFT = 0.83 / 16.0;
    /** Moved this far forward so his back and head rest against a wall behind his block instead of sinking in. */
    static final double FORWARD = 2.0 / 16.0;

    public DecayingCorpseRenderer(EntityRendererProvider.Context context) {
        super(context, new DecayingCorpseModel());
        shadowRadius = 0.5f;
        addRenderLayer(new DecayLayer(this));
        addRenderLayer(new GlowLayer(this));
    }

    @Override
    public boolean shouldRender(DecayingCorpse corpse, Frustum frustum, double x, double y, double z) {
        if (DecayingCorpseView.update(corpse, 0.0f) == DecayingCorpse.PHASE_GONE) return false;
        return super.shouldRender(corpse, frustum, x, y, z);
    }

    @Override
    public void render(DecayingCorpse corpse, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (DecayingCorpseView.update(corpse, partialTick) == DecayingCorpse.PHASE_GONE) return;
        // His shadow fades with him (the game draws it after this, from this value).
        shadowStrength = DecayingCorpseView.alpha(corpse, partialTick);
        boolean portrait = isPortrait(corpse);
        float facing = corpse.yBodyRot * Mth.DEG_TO_RAD;
        pose.pushPose();
        pose.translate(-Mth.sin(facing) * FORWARD, LIFT, Mth.cos(facing) * FORWARD);
        if (!portrait) DecayingCorpseStain.render(corpse, partialTick, pose, buffers, light);
        GeoBone head = portrait ? getGeoModel().getBakedModel(getGeoModel().getModelResource(corpse)).getBone("head").orElse(null) : null;
        if (head != null) {
            // Looking straight out of the portrait: take off the turn toward the player, put it back afterwards.
            head.setRotX(head.getRotX() - corpse.clientHeadPitchAdded);
            head.setRotY(head.getRotY() - corpse.clientHeadYawAdded);
        }
        try {
            super.render(corpse, yaw, partialTick, pose, buffers, light);
        } finally {
            if (head != null) {
                head.setRotX(head.getRotX() + corpse.clientHeadPitchAdded);
                head.setRotY(head.getRotY() + corpse.clientHeadYawAdded);
            }
            pose.popPose();
        }
    }

    /**
     * The dialogue portrait draws him by turning his body toward the camera for a moment (and, to the animation
     * system, from a frozen point in time). In the world his body always faces exactly his facing, so a different
     * body turn means the portrait.
     */
    private static boolean isPortrait(DecayingCorpse corpse) {
        float facing = corpse.facing();
        return !Float.isNaN(facing) && Math.abs(Mth.wrapDegrees(corpse.yBodyRot - facing)) > 0.5f;
    }

    /**
     * In the portrait he is drawn in whatever pose the world last gave him: running his animations from the
     * portrait's frozen clock would drag them back in time every frame (which is what made his head spin and
     * his slay ending stall while the last line was still on screen).
     */
    @Override
    public void actuallyRender(PoseStack pose, DecayingCorpse corpse, BakedGeoModel model, RenderType type, MultiBufferSource buffers,
                               VertexConsumer buffer, boolean isReRender, float partialTick, int light, int overlay,
                               float red, float green, float blue, float alpha) {
        super.actuallyRender(pose, corpse, model, type, buffers, buffer, isReRender || isPortrait(corpse), partialTick,
            light, overlay, red, green, blue, alpha);
    }

    /** While he fades away (end of the slay ending) he is drawn see-through. */
    @Override
    public RenderType getRenderType(DecayingCorpse corpse, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        if (DecayingCorpseView.alpha(corpse, partialTick) < 0.999f) return RenderType.entityTranslucent(texture);
        return super.getRenderType(corpse, texture, buffers, partialTick);
    }

    @Override
    public Color getRenderColor(DecayingCorpse corpse, float partialTick, int light) {
        return Color.ofRGBA(1.0f, 1.0f, 1.0f, DecayingCorpseView.alpha(corpse, partialTick));
    }

    /**
     * During the leave ending: the decayed skin grows in over him, and the flesh it no longer has fades away.
     * (The body underneath is drawn with the part of the normal skin both share; see {@link DecayingCorpseModel#LEAVE_BASE}.)
     */
    private static final class DecayLayer extends GeoRenderLayer<DecayingCorpse> {
        private DecayLayer(DecayingCorpseRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack pose, DecayingCorpse corpse, BakedGeoModel model, RenderType type, MultiBufferSource buffers,
                           VertexConsumer buffer, float partialTick, int light, int overlay) {
            if (corpse.clientPhase != DecayingCorpse.PHASE_LEAVE) return;
            float amount = DecayingCorpseView.decay(corpse, partialTick);
            if (amount > 0.005f) draw(model, pose, buffers, corpse, DecayingCorpseModel.DECAYED, partialTick, light, overlay, amount);
            if (amount < 0.995f) draw(model, pose, buffers, corpse, DecayingCorpseModel.LEAVE_FLESH, partialTick, light, overlay, 1.0f - amount);
        }

        private void draw(BakedGeoModel model, PoseStack pose, MultiBufferSource buffers, DecayingCorpse corpse, ResourceLocation texture,
                          float partialTick, int light, int overlay, float alpha) {
            RenderType type = RenderType.entityTranslucent(texture);
            getRenderer().reRender(model, pose, buffers, corpse, type, buffers.getBuffer(type), partialTick, light, overlay, 1.0f, 1.0f, 1.0f, alpha);
        }
    }

    /** His eyes: drawn full-bright over everything, as strong as {@link DecayingCorpseView#glow} says. */
    private static final class GlowLayer extends GeoRenderLayer<DecayingCorpse> {
        private GlowLayer(DecayingCorpseRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack pose, DecayingCorpse corpse, BakedGeoModel model, RenderType type, MultiBufferSource buffers,
                           VertexConsumer buffer, float partialTick, int light, int overlay) {
            float strength = DecayingCorpseView.glow(corpse, partialTick) * DecayingCorpseView.alpha(corpse, partialTick);
            if (strength < 0.005f) return;
            RenderType eyes = RenderType.eyes(DecayingCorpseModel.GLOW);
            // The eyes render type adds light, so darker colour means a weaker glow.
            getRenderer().reRender(model, pose, buffers, corpse, eyes, buffers.getBuffer(eyes), partialTick, light, overlay, strength, strength, strength, 1.0f);
        }
    }
}
