package github.com.gengyoubo.immersivebakery.mixin;

import com.renyigesai.bakeries.block.bread_rack.BreadRackBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BreadRackBlockEntity.class, remap = false)
abstract class BreadRackBlockEntityMixin {
    // Bakeries leaves out the last slot.
    // https://github.com/Renyigesai/bakery/issues/19
    @Inject(method = "drops", at = @At("HEAD"))
    private void immersiveBakery$dropLastSlot(BreadRackBlockEntity rack, CallbackInfo ci) {
        var items = rack.getItems();
        int last = items.getSlots() - 1;
        ItemStack stack = items.getStackInSlot(last);
        if (rack.getLevel() != null && !stack.isEmpty()) {
            items.setStackInSlot(last, ItemStack.EMPTY);
            Block.popResource(rack.getLevel(), rack.getBlockPos(), stack);
        }
    }
}
