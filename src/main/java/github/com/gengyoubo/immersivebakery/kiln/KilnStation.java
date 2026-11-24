package github.com.gengyoubo.immersivebakery.kiln;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType.Orientation;
import com.renyigesai.bakeries.block.stone_kiln.StoneKilnBlock;
import com.renyigesai.bakeries.init.BakeriesBlocks;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.state.BlockState;

public final class KilnStation {
    public static final ResourceLocation ID = id("stone_kiln");
    public static final ResourceLocation EFFECT_PUT = id("kiln_put");
    public static final ResourceLocation EFFECT_TURNED = id("kiln_turned");
    public static final ResourceLocation EFFECT_TAKEN = id("kiln_taken");
    /** The peel at work here; it goes back to the player with whatever lies on it. */
    public static final String PEEL = "peel";
    /** What lies on that peel. */
    public static final String LOAD = "load";
    public static final String HEARTH = "hearth";
    public static final String WANTS_TURN = "wants_turn";
    public static final String DONE = "done";
    public static final String LIT = "lit";
    /** The peel was pushed into the kiln. */
    public static final String ACTION_REACH = "reach";
    /** The peel was swung under what is in the kiln until that was turned. */
    public static final String ACTION_TURN = "turn";

    private KilnStation() {
    }

    public static void register() {
        Diegesis.station(ID)
                .block(BakeriesBlocks.STONE_KILN)
                .orientation(Orientation.BLOCK_OPPOSITE)
                .openWhen((player, pos, state) -> !player.isShiftKeyDown() && !tendsFire(player, state))
                .exclusive()
                .schema(StationSchema.builder()
                        .slots(PEEL, 1, SlotRules.DEFAULT.maxStack(1).accepts(Peel::isPeel))
                        .slots(LOAD, 1, SlotRules.DEFAULT.maxStack(1).kept())
                        .slots(HEARTH, 1, SlotRules.DEFAULT.maxStack(1).kept().accepts(stack -> false))
                        .control(WANTS_TURN, ControlSpec.toggle(false).locked())
                        .control(DONE, ControlSpec.toggle(false).locked())
                        .control(LIT, ControlSpec.toggle(false).locked())
                        .build())
                .logic(KilnLogic::new)
                .register();
    }

    /** Lighting the kiln and putting it out stay as in Bakeries. */
    private static boolean tendsFire(Player player, BlockState state) {
        ItemStack held = player.getMainHandItem();
        return state.getValue(StoneKilnBlock.LIT)
                ? held.is(Items.WATER_BUCKET) || held.getItem() instanceof ShovelItem && !Peel.isPeel(held)
                : held.getItem() instanceof FlintAndSteelItem;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, path);
    }
}
