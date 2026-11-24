package github.com.gengyoubo.immersivebakery.moka;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType.Orientation;
import com.renyigesai.bakeries.block.moka_pot.MokaPotBlockEntity;
import com.renyigesai.bakeries.init.BakeriesBlocks;
import com.renyigesai.bakeries.init.BakeriesItems;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID)
public final class MokaPotStation {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, "moka_pot");
    public static final TagKey<Item> COFFEE_GROUNDS = ItemTags.create(ResourceLocation.fromNamespaceAndPath("forge", "coffee_grounds"));
    public static final String GROUNDS = "grounds";
    public static final String PROGRESS = "progress";
    public static final String HEATED = "heated";
    public static final String FILLED = "filled";
    public static final String ACTION_TAKE = "take";
    /** Bakeries brews for this long. */
    static final int BREW_TICKS = 200;

    private MokaPotStation() {
    }

    public static void register() {
        Diegesis.station(ID)
                .block(BakeriesBlocks.MOKA_POT)
                .orientation(Orientation.PLAYER)
                .openWhen((player, pos, state) -> !player.isShiftKeyDown()
                        && player.level().getBlockEntity(pos) instanceof MokaPotBlockEntity pot && !pot.getFill()
                        && pot.isCraft(player.level(), pos))
                .exclusive()
                .schema(StationSchema.builder()
                        .slots(GROUNDS, 1, SlotRules.DEFAULT.maxStack(1).kept().accepts(stack -> stack.is(COFFEE_GROUNDS)))
                        .control(PROGRESS, ControlSpec.stepped(0, 1, BREW_TICKS / 5, 0).locked())
                        .control(HEATED, ControlSpec.toggle(false).locked())
                        .control(FILLED, ControlSpec.toggle(false).locked())
                        .build())
                .logic(MokaPotLogic::new)
                .register();
    }

    /** A pot that is done is taken with whatever is in the hand; Bakeries wants it empty and would brew grounds on top of the coffee. */
    @SubscribeEvent
    public static void takeFilledPot(PlayerInteractEvent.RightClickBlock event) {
        var level = event.getLevel();
        if (!event.getEntity().isShiftKeyDown() && level.getBlockEntity(event.getPos()) instanceof MokaPotBlockEntity pot && pot.getFill()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
            if (!level.isClientSide()) {
                take(pot, event.getEntity());
            }
        }
    }

    /** Gives the player the pot with its coffee in place of the block. */
    static boolean take(MokaPotBlockEntity pot, Player player) {
        var level = pot.getLevel();
        BlockPos pos = pot.getBlockPos();
        if (level == null || !pot.getFill() || !level.mayInteract(player, pos) || !level.removeBlock(pos, false)) {
            return false;
        }
        ItemStack filled = new ItemStack(BakeriesItems.MOKA_POT_FILL.get());
        if (!player.getInventory().add(filled)) {
            player.drop(filled, false);
        }
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS);
        return true;
    }
}
