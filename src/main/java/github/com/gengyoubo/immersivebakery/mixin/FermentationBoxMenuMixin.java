package github.com.gengyoubo.immersivebakery.mixin;

import com.renyigesai.bakeries.block.fermentation_box.FermentationBoxBlockEntity;
import com.renyigesai.bakeries.inventory.fermentation_box.FermentationBoxMenu;
import github.com.gengyoubo.immersivebakery.fermentation.FermentationPower;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FermentationBoxMenu.class, remap = false)
abstract class FermentationBoxMenuMixin {
    @Shadow public abstract FermentationBoxBlockEntity getBlockEntity();

    @Inject(method = {"stillValid", "m_6875_"}, at = @At("HEAD"), cancellable = true)
    private void immersiveBakery$closeWhilePowered(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (FermentationPower.isPowered(getBlockEntity())) {
            cir.setReturnValue(false);
        }
    }
}
