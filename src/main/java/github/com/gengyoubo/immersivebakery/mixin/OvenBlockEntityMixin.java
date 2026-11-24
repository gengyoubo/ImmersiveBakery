package github.com.gengyoubo.immersivebakery.mixin;

import com.renyigesai.bakeries.block.oven.OvenBlockEntity;
import github.com.gengyoubo.immersivebakery.oven.OvenPower;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OvenBlockEntity.class, remap = false)
abstract class OvenBlockEntityMixin {
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void immersiveBakery$waitForPower(Level level, BlockPos pos, BlockState state,
                                                     OvenBlockEntity oven, CallbackInfo ci) {
        if (!OvenPower.canCook(oven)) {
            ci.cancel();
        }
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private static void immersiveBakery$finishBatch(Level level, BlockPos pos, BlockState state,
                                                    OvenBlockEntity oven, CallbackInfo ci) {
        OvenPower.stopAfterBatch(oven);
    }
}
