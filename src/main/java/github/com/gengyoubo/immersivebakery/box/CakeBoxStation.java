package github.com.gengyoubo.immersivebakery.box;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType.Orientation;
import com.renyigesai.bakeries.init.BakeriesBlocks;
import com.renyigesai.bakeries.item.CakeBoxItem;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A cake box: its ribbon is untied, its lid lifted, and then there is room for one thing in it. Closed again when the player leaves. */
public final class CakeBoxStation {
    public static final ResourceLocation ID = id("cake_box");
    public static final String CAKE = "cake";
    public static final String UNTIED = "untied";
    public static final String OPEN = "open";
    public static final String ACTION_UNTIE = "untie";
    public static final String ACTION_OPEN = "open";
    public static final String ACTION_CLOSE = "close";
    private static final TagKey<Item> NOT_FOR_BOXES = ItemTags.create(ResourceLocation.fromNamespaceAndPath("bakeries", "not_cake_box_ingredients"));

    private CakeBoxStation() {
    }

    public static void register() {
        Diegesis.station(ID)
                .block(BakeriesBlocks.CAKE_BOX)
                .orientation(Orientation.BLOCK_OPPOSITE)
                .openWhen((player, pos, state) -> !player.isShiftKeyDown())
                .exclusive()
                .hidesBlock()
                .schema(StationSchema.builder()
                        .slots(CAKE, 1, SlotRules.DEFAULT.maxStack(1).kept().accepts(CakeBoxStation::fits))
                        .control(UNTIED, ControlSpec.toggle(false).locked())
                        .control(OPEN, ControlSpec.toggle(false).locked())
                        .build())
                .logic(CakeBoxLogic::new)
                .register();
    }

    /** As when Bakeries' box is filled in the hand. */
    public static boolean fits(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof CakeBoxItem) && !stack.is(NOT_FOR_BOXES);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, path);
    }
}
