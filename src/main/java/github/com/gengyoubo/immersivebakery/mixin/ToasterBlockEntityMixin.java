package github.com.gengyoubo.immersivebakery.mixin;

import com.renyigesai.bakeries.block.toaster.ToasterBlock;
import com.renyigesai.bakeries.block.toaster.ToasterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ToasterBlockEntity.class, remap = false)
abstract class ToasterBlockEntityMixin {
    // Bakeries tells clients how far the toast is only when something else changes: they count on by themselves until then.
    @Inject(method = "clientTick", at = @At("RETURN"))
    private static void immersiveBakery$countOn(Level level, BlockPos pos, BlockState state, ToasterBlockEntity toaster, CallbackInfo ci) {
        if (state.getValue(ToasterBlock.STATE) != ToasterBlock.State.LIT) {
            return;
        }
        int[] progress = toaster.getCookingProgress();
        for (int slot = 0; slot < progress.length; slot++) {
            if (!toaster.getItems().getStackInSlot(slot).isEmpty() && progress[slot] < toaster.getCookingTime()[slot]) {
                progress[slot]++;
            }
        }
    }
}
