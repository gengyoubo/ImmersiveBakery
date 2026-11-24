package github.com.gengyoubo.immersivebakery.mixin;

import com.renyigesai.bakeries.block.fermentation_box.FermentationBoxBlockEntity;
import github.com.gengyoubo.immersivebakery.fermentation.FermentationPower;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FermentationBoxBlockEntity.class, remap = false)
abstract class FermentationBoxBlockEntityMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target =
            "Lcom/renyigesai/bakeries/block/fermentation_box/FermentationBoxBlockEntity;cookTick("
            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lcom/renyigesai/bakeries/block/fermentation_box/FermentationBoxBlockEntity;)V"))
    private static void immersiveBakery$waitForPower(Level level, BlockPos pos, BlockState state,
                                                     FermentationBoxBlockEntity box) {
        if (FermentationPower.canFerment(box)) {
            FermentationBoxBlockEntity.cookTick(level, pos, state, box);
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private static void immersiveBakery$finishBatch(Level level, BlockPos pos, BlockState state,
                                                    FermentationBoxBlockEntity box, CallbackInfo ci) {
        FermentationPower.stopAfterBatch(box);
    }
}
