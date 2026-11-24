package github.com.gengyoubo.immersivebakery.dough;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType.Orientation;
import com.renyigesai.bakeries.init.BakeriesBlocks;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

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
    /** How much dough is worked at once. */
    public static final int MOST_DOUGH = 16;
    private static final double LOOK_REACH = 8;

    private DoughStation() {
    }

    public static void register() {
        Diegesis.station(ID)
                .block(BakeriesBlocks.DOUGH_CRAFTING_TABLE)
                .orientation(Orientation.BLOCK_OPPOSITE)
                .openWhen((player, pos, state) -> !player.isShiftKeyDown() && !usesStorage(player, pos))
                .exclusive()
                .schema(StationSchema.builder()
                        .slots(DOUGH, 1, SlotRules.DEFAULT.maxStack(MOST_DOUGH))
                        .slots(PRODUCTS, PRODUCT_SLOTS, SlotRules.DEFAULT.accepts(stack -> false))
                        .build())
                .logic(DoughLogic::new)
                .register();
    }

    /** As in Bakeries, only the top is for shaping dough; the other sides open the storage. The client alone knows where it looks. */
    private static boolean usesStorage(Player player, BlockPos pos) {
        return player.level().isClientSide && player.pick(LOOK_REACH, 1, false) instanceof BlockHitResult hit
                && hit.getBlockPos().equals(pos) && hit.getDirection() != Direction.UP;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, path);
    }
}
