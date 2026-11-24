package github.com.gengyoubo.immersivebakery.mixin;

import com.renyigesai.bakeries.item.StoneKilnShovelItem;
import github.com.gengyoubo.immersivebakery.client.PeelRenderer;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.Tier;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StoneKilnShovelItem.class, remap = false)
abstract class StoneKilnShovelItemMixin extends ShovelItem {
    private StoneKilnShovelItemMixin(Tier tier, float attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(PeelRenderer.EXTENSIONS);
    }

    // What lies on the peel is seen; the glint is left to enchantments.
    @Inject(method = {"isFoil", "m_5812_"}, at = @At("HEAD"), cancellable = true)
    private void immersiveBakery$showLoadInsteadOfGlint(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(stack.isEnchanted());
    }
}
