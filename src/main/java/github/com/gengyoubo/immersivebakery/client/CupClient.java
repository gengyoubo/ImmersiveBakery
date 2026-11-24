package github.com.gengyoubo.immersivebakery.client;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.DiegesisSounds;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.renyigesai.bakeries.block.glass_drink_cup.GlassDrinkCupBlockEntity;
import com.renyigesai.bakeries.recipe.CoffeeRecipe;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.client.recipebook.RecipeBook;
import github.com.gengyoubo.immersivebakery.cup.CupContents;
import github.com.gengyoubo.immersivebakery.cup.CupStation;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** The drink cup: what goes into it, and shaking it up and down until that is mixed. */
@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CupClient implements StationBehavior {
    private static final String LANG = "immersive_bakery.drink_cup.";
    private static final String BOOK = "book_open";
    private static final String CUP = "cup";
    private static final float CAPTION_SECONDS = 4;
    private static final AddedItems.Place MOUTH = new AddedItems.Place(3.2F, 4, -4, 2.4F, 2.2F, 11.6F, 2);
    private static final float MIDDLE = 8;
    /** How far up and down the cup goes in all until the drink is mixed, pixels, and the shortest stroke that counts. */
    private static final float SHAKEN = 60;
    private static final float STROKE_LEAST = 2;
    /** Faster than this, per frame, does not count. */
    private static final float FASTEST = 5;
    /** A shorter hold without a shake is a click, which takes something out. */
    private static final float CLICK = 0.2F;
    private static final float ANSWER_SECONDS = 1;
    private static final float SLOSH_PER_SPEED = 0.05F;
    private static final float SLOSH_MOST = 2;

    private final RecipeBook book = new RecipeBook(new CupBook());
    private final AddedItems added = new AddedItems(MOUTH, CupContents::isPoured, FoodColors::liquid);
    private List<ItemStack> shown = List.of();
    @Nullable
    private CoffeeRecipe made;
    private ItemStack drink = ItemStack.EMPTY;
    private float level;
    private float shaken;
    private float waiting;
    private float lastY = Float.NaN;
    /** The stroke the cup is on: its direction, and how far it went. */
    private int direction;
    private float stroke;
    private float held;
    private float slosh;
    private float time;
    private String hint = "";

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DiegesisClient.registerBehavior(CupStation.ID, view -> new CupClient()));
    }

    @Override
    public void onOpen(StationView view) {
        hint = "";
        refresh(view);
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
        view.node(BOOK).ifPresent(node -> node.setPainter(book));
        view.node(CUP).ifPresent(node -> node.setPainter((pose, buffers, light) -> {
            List<ItemStack> arrived = added.arrived(shown);
            if (!arrived.isEmpty()) {
                CupPainter.draw(pose, buffers, light, arrived, level, drink, shaken / SHAKEN, slosh, time);
            }
            if (view.isBlockHidden() && view.level().getBlockEntity(view.pos()) instanceof GlassDrinkCupBlockEntity cup) {
                pose.pushPose();
                pose.translate(-MIDDLE, 0, -MIDDLE);
                TankPainter.draw(view, cup.getBlockState(), Minecraft.getInstance().getBlockRenderer().getBlockModel(cup.getBlockState()), pose, buffers);
                pose.popPose();
            }
        }));
        shown = in(view);
        level = CupPainter.level(shown);
        refresh(view);
    }

    /** What is in the cup reaches clients with its block entity. */
    @Override
    public void tick(StationView view) {
        List<ItemStack> now = in(view);
        boolean same = now.size() == shown.size();
        for (int i = 0; same && i < now.size(); i++) {
            same = ItemStack.matches(now.get(i), shown.get(i));
        }
        if (!same) {
            shown = now;
            refresh(view);
        }
        if (waiting > 0 && (waiting -= 0.05F) <= 0) {
            // The server made nothing of it.
            shaken = 0;
        }
    }

    @Override
    public void frame(StationView view, float deltaSeconds) {
        book.frame(view, deltaSeconds);
        added.frame(view, deltaSeconds);
        time += deltaSeconds;
        // Mixed, it fills the glass, as the finished drink does.
        float full = Mth.lerp(shaken / SHAKEN, CupPainter.level(added.arrived(shown)), CupPainter.full());
        level = Mth.lerp(Math.min(1, deltaSeconds * 4), level, full);
        NodeView cup = view.node(CUP).orElse(null);
        float speed = 0;
        if (cup != null && cup.isHeld()) {
            held += deltaSeconds;
            float y = cup.scenePosition().y;
            if (!Float.isNaN(lastY)) {
                float way = y - lastY;
                speed = deltaSeconds <= 0 ? 0 : Math.abs(way) / deltaSeconds;
                if (view.isFocused()) {
                    shake(view, Mth.clamp(way, -FASTEST, FASTEST));
                }
            }
            lastY = y;
        } else {
            lastY = Float.NaN;
            direction = 0;
        }
        float thrown = Math.min(SLOSH_MOST, speed * SLOSH_PER_SPEED);
        slosh = Mth.lerp(Math.min(1, deltaSeconds * (thrown > slosh ? 12 : 5)), slosh, thrown);
        if (slosh < 0.02F) {
            slosh = 0;
        }
    }

    private void shake(StationView view, float way) {
        if (way == 0 || made == null || waiting > 0) {
            return;
        }
        int now = way > 0 ? 1 : -1;
        if (now != direction) {
            if (stroke > STROKE_LEAST) {
                view.playSound(DiegesisSounds.SLOSH, CUP, 0.5F, 1.1F + 0.3F * shaken / SHAKEN);
            }
            direction = now;
            stroke = 0;
        }
        stroke += Math.abs(way);
        if (stroke > STROKE_LEAST) {
            shaken = Math.min(SHAKEN, shaken + Math.abs(way));
        }
        if (shaken >= SHAKEN) {
            waiting = ANSWER_SECONDS;
            view.sendAction(CupStation.ACTION_SHAKEN, new CompoundTag());
        }
    }

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (BOOK.equals(node)) {
            book.click(view, button);
            return true;
        }
        if (!"glass".equals(node) || !CupContents.accepts(view.level(), shown, stack)) {
            return false;
        }
        view.sendAction(CupStation.ACTION_ADD, new CompoundTag());
        return true;
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && BOOK.equals(click.node())) {
            book.click(view, click.button());
        } else if (event instanceof SceneEvent.Click click && "glass".equals(click.node())) {
            view.sendAction(CupStation.ACTION_TAKE, new CompoundTag());
        } else if (event instanceof SceneEvent.Release release && CUP.equals(release.node())) {
            if (held < CLICK && shaken == 0) {
                view.sendAction(CupStation.ACTION_TAKE, new CompoundTag());
            }
            held = 0;
            refresh(view);
        }
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        if (CupStation.EFFECT_ADDED.equals(effect)) {
            added.add(ItemStack.of(data.getCompound(CupStation.ITEM)));
        }
    }

    private static List<ItemStack> in(StationView view) {
        return view.level().getBlockEntity(view.pos()) instanceof GlassDrinkCupBlockEntity cup
                ? CupContents.in(cup).stream().map(ItemStack::copy).toList() : List.of();
    }

    private void refresh(StationView view) {
        CoffeeRecipe before = made;
        made = CupContents.made(view.level(), shown);
        if (made != before) {
            shaken = 0;
            waiting = 0;
        }
        drink = made == null ? ItemStack.EMPTY : made.getResultItem(view.level().registryAccess());
        List<ItemStack> in = shown;
        view.setInventoryFilter(stack -> CupContents.accepts(view.level(), in, stack));
        view.node(CUP).ifPresent(node -> node.setEnabled(made != null || node.isHeld()));
        String next = shown.isEmpty() ? "empty" : made == null ? "more" : "shake";
        Component told = made == null ? Component.translatable(LANG + next)
                : Component.translatable(LANG + next, drink.getHoverName());
        view.node(CUP).ifPresent(node -> node.setTooltip(told));
        view.node("glass").ifPresent(node -> node.setTooltip(told));
        if (view.isFocused() && !told.getString().equals(hint)) {
            hint = told.getString();
            view.showCaption(told, CAPTION_SECONDS);
        }
    }
}
