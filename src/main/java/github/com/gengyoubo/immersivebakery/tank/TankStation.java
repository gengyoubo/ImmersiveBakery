package github.com.gengyoubo.immersivebakery.tank;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.station.StationType.Orientation;
import com.renyigesai.bakeries.init.BakeriesBlocks;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/** Bakeries' fermentation tank, whatever is in it: each content is a block of its own. */
public final class TankStation {
    public static final ResourceLocation ID = id("fermentation_tank");
    /** Something went in; data: {@link #ITEM}. */
    public static final ResourceLocation EFFECT_ADDED = id("tank_added");
    public static final String ITEM = "item";
    /** One of what is on the cursor goes in or is filled. */
    public static final String ACTION_USE = "use";
    /** With an empty hand: cheese comes out, or a bottle from the inventory is filled with yeast. */
    public static final String ACTION_TAKE = "take";
    /** A recipe picked in the book; data: {@link #RECIPE}, its id. */
    public static final String ACTION_FILL = "fill";
    public static final String RECIPE = "recipe";

    private TankStation() {
    }

    public static void register() {
        Diegesis.station(ID)
                .block(BakeriesBlocks.FERMENTATION_TANK)
                .block(BakeriesBlocks.Milk_TANK)
                .block(BakeriesBlocks.YEAST_TANK)
                .block(BakeriesBlocks.CHEESE_TANK)
                .orientation(Orientation.PLAYER)
                .openWhen((player, pos, state) -> !player.isShiftKeyDown() && !takesOut(player, state))
                .exclusive()
                .hidesBlock()
                .logic(TankLogic::new)
                .register();
    }

    /** What is done is taken out directly, as in Bakeries. */
    private static boolean takesOut(Player player, BlockState tank) {
        return TankContents.isCheese(tank) || TankContents.isYeast(tank) && player.getMainHandItem().is(Items.GLASS_BOTTLE);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, path);
    }
}
