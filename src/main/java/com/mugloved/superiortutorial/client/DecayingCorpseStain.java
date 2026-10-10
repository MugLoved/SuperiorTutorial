package com.mugloved.superiortutorial.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mugloved.superiortutorial.SuperiorTutorial;
import com.mugloved.superiortutorial.entity.DecayingCorpse;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The dark stain that soaks into the floor under him while he rots (leave ending), and stays afterwards.
 * A flat picture lying on the floor, drawn in the renderer's space (already moved to where he sits).
 */
final class DecayingCorpseStain {
    private static final ResourceLocation TEXTURE = new ResourceLocation(SuperiorTutorial.MOD_ID, "textures/entity/decaying_corpse_stain.png");
    /** Width of the stain in blocks, and how far forward of his seat its middle lies (his legs reach forward). */
    private static final float SIZE = 1.75f;
    private static final float AHEAD = 0.12f;
    /** Strongest the stain gets. */
    private static final float DARKNESS = 0.9f;

    private DecayingCorpseStain() {}

    static void render(DecayingCorpse corpse, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float amount = amount(corpse, partialTick);
        if (amount < 0.005f) return;
        pose.pushPose();
        // Back down to the floor (the renderer lifted him off it), and turned with his body.
        pose.translate(0.0, -DecayingCorpseRenderer.LIFT + 0.01, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(-corpse.yBodyRot));
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        float half = SIZE / 2.0f;
        float alpha = amount * DARKNESS;
        vertex(buffer, matrix, normal, -half, AHEAD - half, 0.0f, 0.0f, alpha, light);
        vertex(buffer, matrix, normal, -half, AHEAD + half, 0.0f, 1.0f, alpha, light);
        vertex(buffer, matrix, normal, half, AHEAD + half, 1.0f, 1.0f, alpha, light);
        vertex(buffer, matrix, normal, half, AHEAD - half, 1.0f, 0.0f, alpha, light);
        pose.popPose();
    }

    /** 0 to 1: it starts soaking in as his hand drops (1.6 s) and has spread fully by 5 s; there for good after. */
    private static float amount(DecayingCorpse corpse, float partialTick) {
        return switch (corpse.clientPhase) {
            case DecayingCorpse.PHASE_ROTTEN -> 1.0f;
            case DecayingCorpse.PHASE_LEAVE -> DecayingCorpseView.smooth(DecayingCorpseView.endingSeconds(corpse, partialTick), 1.6, 5.0);
            default -> 0.0f;
        };
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Matrix3f normal, float x, float z, float u, float v, float alpha, int light) {
        buffer.vertex(matrix, x, 0.0f, z)
            .color(1.0f, 1.0f, 1.0f, alpha)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(light)
            .normal(normal, 0.0f, 1.0f, 0.0f)
            .endVertex();
    }
}
