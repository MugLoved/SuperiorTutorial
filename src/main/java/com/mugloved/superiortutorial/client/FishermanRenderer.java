package com.mugloved.superiortutorial.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mugloved.superiortutorial.entity.Fisherman;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Draws Haskel; hides him while he is "in the smoke", and keeps him looking straight out of the dialogue portrait. */
public class FishermanRenderer extends GeoEntityRenderer<Fisherman> {
    /** Longest he stays hidden if the "appear" never arrives (a lost packet), in ticks. */
    private static final int HIDDEN_TIMEOUT = 20;

    public FishermanRenderer(EntityRendererProvider.Context context) {
        super(context, new FishermanModel());
        shadowRadius = 0.45f;
    }

    private static boolean hidden(Fisherman fisherman) {
        if (fisherman.clientHidden && fisherman.level().getGameTime() - fisherman.clientHiddenSince > HIDDEN_TIMEOUT) {
            fisherman.clientHidden = false;
        }
        return fisherman.clientHidden;
    }

    @Override
    public boolean shouldRender(Fisherman fisherman, Frustum frustum, double x, double y, double z) {
        if (hidden(fisherman)) return false;
        return super.shouldRender(fisherman, frustum, x, y, z);
    }

    /**
     * The dialogue portrait draws him by turning his body toward the camera for a moment. In the world his body turn
     * only changes on ticks, so a body turn that differs from the last tick's means the portrait.
     */
    private static boolean isPortrait(Fisherman fisherman) {
        return Math.abs(Mth.wrapDegrees(fisherman.yBodyRot - fisherman.clientTickBodyRot)) > 0.5f;
    }

    @Override
    public void render(Fisherman fisherman, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (hidden(fisherman)) return;
        boolean portrait = isPortrait(fisherman);
        GeoBone head = portrait ? getGeoModel().getBakedModel(getGeoModel().getModelResource(fisherman)).getBone("head").orElse(null) : null;
        if (head != null) {
            // Looking straight out of the portrait: take off the turn toward the player, put it back afterwards.
            head.setRotX(head.getRotX() - fisherman.clientHeadPitchAdded);
            head.setRotY(head.getRotY() - fisherman.clientHeadYawAdded);
        }
        try {
            super.render(fisherman, yaw, partialTick, pose, buffers, light);
        } finally {
            if (head != null) {
                head.setRotX(head.getRotX() + fisherman.clientHeadPitchAdded);
                head.setRotY(head.getRotY() + fisherman.clientHeadYawAdded);
            }
        }
    }

    /** In the portrait he keeps the pose the world last gave him (its frozen clock would drag his animations back). */
    @Override
    public void actuallyRender(PoseStack pose, Fisherman fisherman, BakedGeoModel model, RenderType type, MultiBufferSource buffers,
                               VertexConsumer buffer, boolean isReRender, float partialTick, int light, int overlay,
                               float red, float green, float blue, float alpha) {
        super.actuallyRender(pose, fisherman, model, type, buffers, buffer, isReRender || isPortrait(fisherman), partialTick,
            light, overlay, red, green, blue, alpha);
    }
}
