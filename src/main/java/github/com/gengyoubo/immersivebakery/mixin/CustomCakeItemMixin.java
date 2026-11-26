package github.com.gengyoubo.immersivebakery.mixin;

import com.renyigesai.bakeries.item.CustomCakeItem;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import java.util.List;

@Mixin(value = CustomCakeItem.class, remap = false)
abstract class CustomCakeItemMixin extends BlockItem {
    protected CustomCakeItemMixin(Block block, Item.Properties properties) { super(block, properties); }
    /** @author Immersive Bakery @reason Compose the display name from the cake layers. */
    @Overwrite
    public Component getName(ItemStack stack) {
        if (stack.hasTag() && !stack.getTag().getString("Name").isEmpty()) return Component.literal(stack.getTag().getString("Name"));
        List<String> ids = CustomCakeItem.getPartIds(stack);
        String base = null, filling = null, cream = null;
        for (String id : ids) {
            switch (id) {
                case "bakeries:cake_base", "bakeries:red_velvet_cake_base", "bakeries:matcha_cake", "bakeries:basque_cake", "bakeries:blaze_cake" -> base = id;
                case "bakeries:mashed_taro", "bakeries:bearnaise" -> filling = id;
                case "bakeries:cream", "bakeries:cocoa_cream", "bakeries:cheese_cream", "bakeries:meat_floss" -> cream = id;
            }
        }
        if (base == null) return super.getName(stack);
        MutableComponent name = Component.empty();
        if (filling != null) name.append(Component.translatable("immersive_bakery.cake_part." + filling.substring(filling.indexOf(':') + 1))).append(Component.translatable("immersive_bakery.cake_part.filling_suffix"));
        if (cream != null) name.append(Component.translatable("immersive_bakery.cake_part." + cream.substring(cream.indexOf(':') + 1)));
        name.append(Component.translatable("immersive_bakery.cake_part." + base.substring(base.indexOf(':') + 1)));
        return name;
    }
}
