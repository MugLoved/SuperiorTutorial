package com.mugloved.superiortutorial.client;

import com.mugloved.superiortutorial.entity.Larry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Stand-in for Mug's own Larry model: the vanilla skeleton, sitting slumped with limp arms.
 * Replaced by the real model when it is ready.
 */
public class LarryModel extends HumanoidModel<Larry> {
    /** Set by the renderer each frame: the viewing player left him, so his head hangs and stops following. */
    boolean decayed;

    public LarryModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(Larry larry, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        riding = true;
        if (decayed) {
            netHeadYaw = 0.0f;
            headPitch = 40.0f;
        }
        super.setupAnim(larry, 0.0f, 0.0f, ageInTicks, netHeadYaw, headPitch);
        // Arms hang at his sides instead of the riding reach.
        rightArm.xRot = -0.2f;
        leftArm.xRot = -0.2f;
        rightArm.yRot = 0.0f;
        leftArm.yRot = 0.0f;
        rightArm.zRot = 0.12f;
        leftArm.zRot = -0.12f;
    }
}
