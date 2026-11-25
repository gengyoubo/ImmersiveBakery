package github.com.gengyoubo.immersivebakery.mixin;

import com.renyigesai.bakeries.event.BakeriesEvents;
import github.com.gengyoubo.immersivebakery.bread.BreadStation;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BakeriesEvents.class, remap = false)
abstract class BakeriesEventsMixin {
    // A knife in the hand cuts the bread at the station. That opens on the client after this event, and the server asks with the
    // same event whether the player may use the block: cut here, the bread would be cut before the station is open.
    // Sneaking players and machines still cut it as in Bakeries.
    @Inject(method = "onUseKnifeBlock", at = @At("HEAD"), cancellable = true)
    private static void immersiveBakery$openStation(PlayerInteractEvent.RightClickBlock event, CallbackInfo ci) {
        if (!(event.getEntity() instanceof FakePlayer) && event.getHand() == InteractionHand.MAIN_HAND
                && BreadStation.opens(event.getEntity(), event.getLevel().getBlockState(event.getPos()))) {
            ci.cancel();
        }
    }
}
