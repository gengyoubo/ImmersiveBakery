package github.com.gengyoubo.immersivebakery.mixin;

import com.renyigesai.bakeries.block.moka_pot.MokaPotBlockEntity;
import github.com.gengyoubo.immersivebakery.moka.MokaPotContents;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = MokaPotBlockEntity.class, remap = false)
abstract class MokaPotBlockEntityMixin implements MokaPotContents {
    @Shadow @Final protected ItemStackHandler inventory;

    @Override
    public ItemStackHandler immersiveBakery$inventory() {
        return inventory;
    }
}
