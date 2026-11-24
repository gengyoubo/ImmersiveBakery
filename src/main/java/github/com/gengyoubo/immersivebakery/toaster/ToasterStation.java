package github.com.gengyoubo.immersivebakery.toaster;

import com.pockyl.diegesis.api.Diegesis;
import com.renyigesai.bakeries.block.toaster.ToasterBlockEntity;
import com.renyigesai.bakeries.init.BakeriesBlocks;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID)
public final class ToasterStation {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, "toaster");
    public static final String SLOT_0 = "slot_0";
    public static final String SLOT_1 = "slot_1";
    public static final String LEVER = "lever";
    public static final String TOTAL_TICKS = "total_ticks";
    public static final String REMAINING_TICKS = "remaining_ticks";

    private static final TagKey<Item> TOASTABLE_BREAD = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, "toastable_bread"));

    private ToasterStation() {
    }

    public static void register() {
        Diegesis.station(ID)
                .block(BakeriesBlocks.TOASTER)
                .openWhen((player, pos, state) -> !player.isShiftKeyDown())
                .exclusive()
                .logic(station -> new ToasterLogic())
                .register();
    }

    public static Optional<CampfireCookingRecipe> findRecipe(ToasterBlockEntity toaster, ItemStack input) {
        return input.is(TOASTABLE_BREAD) ? toaster.getSmokerRecipe(input) : Optional.empty();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void restrictDirectInsertion(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!held.isEmpty() && !held.is(TOASTABLE_BREAD)
                && event.getLevel().getBlockEntity(event.getPos()) instanceof ToasterBlockEntity toaster
                && toaster.getSmokerRecipe(held).isPresent()) {
            event.setUseBlock(Event.Result.DENY);
        }
    }
}
