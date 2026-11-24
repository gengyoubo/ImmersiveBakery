package github.com.gengyoubo.immersivebakery.mixin;

import com.renyigesai.bakeries.block.blender.BlenderBlockEntity;
import com.renyigesai.bakeries.inventory.blender.BlenderMenu;
import github.com.gengyoubo.immersivebakery.blender.BlenderPower;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlenderMenu.class, remap = false)
abstract class BlenderMenuMixin {
    @Shadow public abstract BlenderBlockEntity getBlockEntity();

    @Inject(method = {"stillValid", "m_6875_"}, at = @At("HEAD"), cancellable = true)
    private void immersiveBakery$closeWhilePowered(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!BlenderPower.allowsNativeMenu(getBlockEntity())) {
            cir.setReturnValue(false);
        }
    }
}
