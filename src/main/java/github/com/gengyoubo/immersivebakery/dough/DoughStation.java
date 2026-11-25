package github.com.gengyoubo.immersivebakery.dough;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType.Orientation;
import com.renyigesai.bakeries.init.BakeriesBlocks;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.storage.Drawers;
import net.minecraft.resources.ResourceLocation;

/** A board that dough is shaped on, on a cupboard. */
public final class DoughStation {
    public static final ResourceLocation ID = id("dough_crafting_table");
    public static final ResourceLocation EFFECT_SHAPED = id("dough_shaped");
    public static final String DOUGH = "dough";
    public static final String PRODUCTS = "products";
    public static final int PRODUCT_SLOTS = 4;
    /** The dough was shaped; data: {@link #RECIPE}, the id of what it becomes. */
    public static final String ACTION_SHAPE = "shape";
    public static final String RECIPE = "recipe";
    /** The dough goes back into the inventory. */
    public static final String ACTION_TAKE = "take";
    public static final StationSchema SCHEMA = Drawers.addTo(StationSchema.builder()
            .slots(DOUGH, 1, SlotRules.DEFAULT)
            .slots(PRODUCTS, PRODUCT_SLOTS, SlotRules.DEFAULT.accepts(stack -> false)))
            .build();

    private DoughStation() {
    }

    public static void register() {
        Diegesis.station(ID)
                .block(BakeriesBlocks.DOUGH_CRAFTING_TABLE)
                .orientation(Orientation.BLOCK_OPPOSITE)
                .openWhen((player, pos, state) -> !player.isShiftKeyDown())
                .exclusive()
                .hidesBlock()
                .schema(SCHEMA)
                .logic(DoughLogic::new)
                .register();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, path);
    }
}
