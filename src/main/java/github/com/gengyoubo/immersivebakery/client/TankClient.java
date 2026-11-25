package github.com.gengyoubo.immersivebakery.client;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.renyigesai.bakeries.block.MilkTankBlock;
import com.renyigesai.bakeries.block.YeastTankBlock;
import com.renyigesai.bakeries.init.BakeriesBlocks;
import com.renyigesai.bakeries.init.BakeriesItems;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.client.recipebook.RecipeBook;
import github.com.gengyoubo.immersivebakery.tank.TankContents;
import github.com.gengyoubo.immersivebakery.tank.TankStation;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TankClient implements StationBehavior {
    private static final String LANG = "immersive_bakery.fermentation_tank.";
    private static final String BOOK = "book_open";
    private static final float CAPTION_SECONDS = 4;
    private static final float LID_PER_SECOND = 4;
    private static final AddedItems.Place MOUTH = new AddedItems.Place(4.8F, 5, -5, 3.4F, 3.5F, 18.6F, 5);

    /** A tank ferments for some 70 seconds on average. */
    private static final float FERMENTING_TICKS = 900;
    private static final float FERMENTED_MOST = 0.92F;
    /** Since when tanks were seen fermenting. */
    private static final Map<Level, Map<BlockPos, Long>> FERMENTING = new WeakHashMap<>();

    private final RecipeBook book = new RecipeBook(new TankBook());
    private final AddedItems added = new AddedItems(MOUTH, TankClient::isVessel, vessel -> FoodColors.liquid(liquid(vessel)));
    private float lid;
    @Nullable
    private BlockState shown;

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DiegesisClient.registerBehavior(TankStation.ID, view -> new TankClient()));
    }

    @Override
    public void onOpen(StationView view) {
        shown = null;
        tick(view);
        book.opened(view);
    }

    @Override
    public void onClose(StationView view) {
        book.shut(view);
    }

    @Override
    public boolean onKey(StationView view, int keyCode, int scanCode, int modifiers) {
        return book.key(view, keyCode, modifiers);
    }

    @Override
    public boolean onChar(StationView view, char character, int modifiers) {
        return book.typed(view, character);
    }

    @Override
    public boolean onScroll(StationView view, String node, double amount) {
        return BOOK.equals(node) && book.scroll(amount);
    }

    @Override
    public void onSceneReady(StationView view) {
        added.setup(view);
        shown = null;
        view.node(BOOK).ifPresent(node -> node.setPainter(book));
        view.node("jar").ifPresent(node -> node.setPainter((pose, buffers, light) -> {
            if (view.isBlockHidden()) {
                BlockState tank = view.level().getBlockState(view.pos());
                TankPainter.draw(view, tank, pose, buffers, lid * lid * (3 - 2 * lid));
                if (TankContents.isFermenting(tank) && added.isEmpty()) {
                    TankPainter.drawOver(view, TankContents.fermented(tank), pose, buffers, fermented(view));
                }
            }
        }));
    }

    /** All there is to know is in the block state. */
    @Override
    public void tick(StationView view) {
        BlockState tank = view.level().getBlockState(view.pos());
        if (tank != shown) {
            if (shown != null || !TankContents.isFermenting(tank)) {
                FERMENTING.getOrDefault(view.level(), new HashMap<>()).remove(view.pos());
            }
            shown = tank;
            refresh(view, tank);
        }
    }

    @Override
    public void frame(StationView view, float deltaSeconds) {
        book.frame(view, deltaSeconds);
        boolean open = view.isInUse() && shown != null && (!TankContents.isFermenting(shown) || !added.isEmpty());
        lid = Mth.clamp(lid + (open ? deltaSeconds : -deltaSeconds) * LID_PER_SECOND, 0, 1);
        added.frame(view, deltaSeconds);
    }

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (BOOK.equals(node)) {
            book.click(view, button);
            return true;
        }
        if (!"tank".equals(node) || TankContents.use(view.level().getBlockState(view.pos()), stack) == null) {
            return false;
        }
        view.sendAction(TankStation.ACTION_USE, new CompoundTag());
        return true;
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && BOOK.equals(click.node())) {
            book.click(view, click.button());
        } else if (event instanceof SceneEvent.Click click && "tank".equals(click.node())) {
            view.sendAction(TankStation.ACTION_TAKE, new CompoundTag());
        }
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        if (TankStation.EFFECT_ADDED.equals(effect)) {
            added.add(ItemStack.of(data.getCompound(TankStation.ITEM)));
        }
    }

    /**
     * How far the tank looks fermented. Nothing knows how far it is, as it all happens at once at a random moment; so it looks the
     * further the longer it has been seen fermenting.
     */
    private static float fermented(StationView view) {
        long since = FERMENTING.computeIfAbsent(view.level(), level -> new HashMap<>()).computeIfAbsent(view.pos(), pos -> view.level().getGameTime());
        return FERMENTED_MOST * (1 - (float) Math.exp(-(view.level().getGameTime() - since) / FERMENTING_TICKS));
    }

    private static ItemStack liquid(ItemStack vessel) {
        return new ItemStack(vessel.is(Items.POTION) ? Items.WATER_BUCKET : Items.MILK_BUCKET);
    }

    private static boolean isVessel(ItemStack stack) {
        return stack.is(Items.POTION) || stack.is(Items.MILK_BUCKET) || stack.is(BakeriesItems.BOTTLE_MILK.get());
    }

    private void refresh(StationView view, BlockState tank) {
        view.setInventoryFilter(stack -> TankContents.use(tank, stack) != null);
        Component told = told(tank);
        view.node("tank").ifPresent(node -> node.setTooltip(told));
        if (view.isFocused()) {
            view.showCaption(told, CAPTION_SECONDS);
        }
    }

    private static Component told(BlockState tank) {
        if (TankContents.isCheese(tank)) {
            return text("cheese");
        }
        if (TankContents.isYeast(tank)) {
            return text("yeast", tank.getValue(YeastTankBlock.YEAST));
        }
        if (TankContents.isFermenting(tank)) {
            return text("fermenting");
        }
        if (TankContents.isMilk(tank)) {
            int milk = tank.getValue(MilkTankBlock.MILK);
            return text(milk < TankContents.MOST_MILK ? "milk" : "milk_full", milk, TankContents.MOST_MILK);
        }
        if (!tank.is(BakeriesBlocks.FERMENTATION_TANK.get())) {
            return Component.empty();
        }
        int flour = TankContents.flour(tank);
        boolean water = TankContents.hasWater(tank);
        return flour == 0 && !water ? text("empty") : text(water ? "flour_with_water" : "flour", flour, TankContents.MOST_FLOUR);
    }

    private static MutableComponent text(String key, Object... with) {
        return Component.translatable(LANG + key, with);
    }
}
