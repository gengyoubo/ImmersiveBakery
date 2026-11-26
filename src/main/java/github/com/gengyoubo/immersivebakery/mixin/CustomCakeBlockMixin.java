package github.com.gengyoubo.immersivebakery.mixin;

import com.renyigesai.bakeries.block.custom_cake.CustomCakeBlock;
import com.renyigesai.bakeries.block.custom_cake.CustomCakeBlockEntity;
import com.renyigesai.bakeries.init.BakeriesItems;
import com.renyigesai.bakeries.item.CustomCakeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CustomCakeBlock.class)
abstract class CustomCakeBlockMixin extends HorizontalDirectionalBlock {
    protected CustomCakeBlockMixin(BlockBehaviour.Properties properties) { super(properties); }
    @Override public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(BakeriesItems.CUSTOM_CAKE.get());
        if (level.getBlockEntity(pos) instanceof CustomCakeBlockEntity cake) CustomCakeItem.setCustomCakeNBT(cake, stack);
        return stack;
    }
}
