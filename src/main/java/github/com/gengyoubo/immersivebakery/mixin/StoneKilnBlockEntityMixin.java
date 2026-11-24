package github.com.gengyoubo.immersivebakery.mixin;

import com.renyigesai.bakeries.block.stone_kiln.StoneKilnBlockEntity;
import github.com.gengyoubo.immersivebakery.client.KilnClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StoneKilnBlockEntity.class, remap = false)
abstract class StoneKilnBlockEntityMixin {
    @Shadow public float progress;
    @Shadow public float progressOld;

    // Bakeries only starts over on the server, so clients see nothing but the first turn.
    @Inject(method = {"triggerEvent", "m_7531_"}, at = @At("HEAD"), cancellable = true)
    private void immersiveBakery$turnAgain(int id, int type, CallbackInfoReturnable<Boolean> cir) {
        var kiln = (StoneKilnBlockEntity) (Object) this;
        if (id == 1 && kiln.getLevel() != null && kiln.getLevel().isClientSide()) {
            if (KilnClient.wasTurnedByHand(kiln)) {
                cir.setReturnValue(true);
            } else {
                progress = 0;
                progressOld = 0;
            }
        }
    }
}
