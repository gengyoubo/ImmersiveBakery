package github.com.gengyoubo.immersivebakery.mixin;

import com.renyigesai.bakeries.block.oven.OvenBlockEntity;
import com.renyigesai.bakeries.inventory.oven.OvenMenu;
import github.com.gengyoubo.immersivebakery.oven.OvenPower;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = OvenMenu.class, remap = false)
abstract class OvenMenuMixin {
    @Shadow public BlockEntity boundBlockEntity;

    // The native menu may already be open when another player powers on the oven.
    @Inject(method = {"stillValid", "m_6875_"}, at = @At("HEAD"), cancellable = true)
    private void immersiveBakery$closeWhilePowered(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (boundBlockEntity instanceof OvenBlockEntity oven && OvenPower.isPowered(oven)) {
            cir.setReturnValue(false);
        }
    }
}
