package github.com.gengyoubo.immersivebakery.mixin;

import com.renyigesai.bakeries.block.oven.OvenBlockEntity;
import github.com.gengyoubo.immersivebakery.oven.OvenHeat;
import github.com.gengyoubo.immersivebakery.oven.OvenPower;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OvenBlockEntity.class, remap = false)
abstract class OvenBlockEntityMixin implements OvenHeat {
    @Shadow @Final public int[] cooking_times;
    @Shadow @Final public int[] max_cooking_times;
    @Shadow @Final private int[] max_temperatures;

    @Override
    public int immersiveBakery$mostTemperature(int slot) {
        return max_temperatures[slot];
    }

    // What Bakeries sends to clients has other names than what it reads there, so they never learn how far anything is.
    @Inject(method = {"load", "m_142466_"}, at = @At("RETURN"))
    private void immersiveBakery$readProgress(CompoundTag tag, CallbackInfo ci) {
        immersiveBakery$read(tag, "cooking_times", cooking_times);
        immersiveBakery$read(tag, "max_cooking_times", max_cooking_times);
        immersiveBakery$read(tag, "max_temperatures", max_temperatures);
    }

    @Unique
    private static void immersiveBakery$read(CompoundTag tag, String name, int[] values) {
        if (tag.contains(name, Tag.TAG_INT_ARRAY)) {
            int[] read = tag.getIntArray(name);
            System.arraycopy(read, 0, values, 0, Math.min(read.length, values.length));
        }
    }

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
