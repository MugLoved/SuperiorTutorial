package com.mugloved.superiortutorial.mixin;

import com.mugloved.superiortutorial.world.CoveGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** World generation's block writes (features and structures): the cove's guard may refuse one (see CoveGuard). */
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"), cancellable = true)
    private void superiorTutorial$guardCove(BlockPos pos, BlockState state, int flags, int recursionLeft,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (CoveGuard.denyWrite((WorldGenLevel) (Object) this, pos, state)) cir.setReturnValue(false);
    }
}
