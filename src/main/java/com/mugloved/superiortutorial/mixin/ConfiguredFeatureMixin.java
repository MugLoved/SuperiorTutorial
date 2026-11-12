package com.mugloved.superiortutorial.mixin;

import com.mugloved.superiortutorial.world.CoveGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every feature world generation runs: the cove's guard skips trees that would grow next to the opening. */
@Mixin(ConfiguredFeature.class)
public abstract class ConfiguredFeatureMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void superiorTutorial$noTreesByTheCove(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (CoveGuard.denyFeature(level, ((ConfiguredFeature<?, ?>) (Object) this).feature(), origin)) cir.setReturnValue(false);
    }
}
