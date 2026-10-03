package com.mugloved.superiortutorial.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mugloved.superiortutorial.entity.Larry;
import com.mugloved.superiortutorial.entity.LarryState;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws Larry for the local player: normal, decayed (they left him), or not at all (they ended him).
 * The state is that player's own, so each player in a world sees their own Larry.
 */
public class LarryRenderer extends HumanoidMobRenderer<Larry, LarryModel> {
    private static final ResourceLocation NORMAL = new ResourceLocation("textures/entity/skeleton/skeleton.png");
    private static final ResourceLocation DECAYED = new ResourceLocation("textures/entity/skeleton/wither_skeleton.png");
    /** How far the sitting model is lowered so he rests on the floor. */
    private static final double SIT_DROP = 0.6;

    public LarryRenderer(EntityRendererProvider.Context context) {
        super(context, new LarryModel(context.bakeLayer(ModelLayers.SKELETON)), 0.4f);
    }

    @Override
    public boolean shouldRender(Larry larry, Frustum frustum, double x, double y, double z) {
        if (LarryState.slainHere()) return false;
        return super.shouldRender(larry, frustum, x, y, z);
    }

    @Override
    public void render(Larry larry, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        model.decayed = LarryState.leftHere();
        pose.pushPose();
        pose.translate(0.0, -SIT_DROP, 0.0);
        super.render(larry, yaw, partialTicks, pose, buffers, light);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(Larry larry) {
        return LarryState.leftHere() ? DECAYED : NORMAL;
    }
}
