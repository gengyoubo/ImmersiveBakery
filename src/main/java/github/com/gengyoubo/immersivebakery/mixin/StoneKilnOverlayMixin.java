package github.com.gengyoubo.immersivebakery.mixin;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.renyigesai.bakeries.block.stone_kiln.StoneKilnBlockEntity;
import com.renyigesai.bakeries.client.overlay.StoneKilnOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderGuiEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StoneKilnOverlay.class, remap = false)
abstract class StoneKilnOverlayMixin {
    @Unique private static final String CREATE = "create(Lnet/minecraftforge/client/event/RenderGuiEvent$Pre;"
            + "Lcom/renyigesai/bakeries/block/stone_kiln/StoneKilnBlockEntity;Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/client/Minecraft;)V";
    /** Where Bakeries puts it below the middle of the screen, and how far from the top it goes instead. */
    @Unique private static final int BELOW_MIDDLE = 15;
    @Unique private static final int FROM_TOP = 20;

    // At the station the middle of the screen is where the peel is worked with.
    @Inject(method = CREATE, at = @At("HEAD"))
    private void immersiveBakery$moveUp(RenderGuiEvent.Pre event, StoneKilnBlockEntity kiln, Player player, Minecraft minecraft, CallbackInfo ci) {
        var pose = event.getGuiGraphics().pose();
        pose.pushPose();
        if (DiegesisClient.isFocused()) {
            pose.translate(0, FROM_TOP - BELOW_MIDDLE - event.getWindow().getGuiScaledHeight() / 2, 0);
        }
    }

    @Inject(method = CREATE, at = @At("RETURN"))
    private void immersiveBakery$moveBack(RenderGuiEvent.Pre event, StoneKilnBlockEntity kiln, Player player, Minecraft minecraft, CallbackInfo ci) {
        event.getGuiGraphics().pose().popPose();
    }
}
