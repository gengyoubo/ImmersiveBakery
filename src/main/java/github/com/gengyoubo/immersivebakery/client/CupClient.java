package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.DiegesisSounds;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.renyigesai.bakeries.block.glass_drink_cup.GlassDrinkCupBlockEntity;
import com.renyigesai.bakeries.init.BakeriesSounds;
import com.renyigesai.bakeries.recipe.CoffeeRecipe;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.client.recipebook.RecipeBook;
import github.com.gengyoubo.immersivebakery.cup.CupContents;
import github.com.gengyoubo.immersivebakery.cup.CupStation;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Vector3f;

/**
 * The drink cup: what goes into it, shaking it up and down until that is mixed, pouring it into the cup the drink is served in, and
 * what falls into that at last.
 */
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
    /** Shaken, the cup moves aside, to where the scene has it for pouring, and the drink's own cup comes down in its place. */
    private static final String POURER = "pourer";
    private static final float ASIDE = 6.2F;
    private static final float ASIDE_SECONDS = 0.5F;
    private static final float PLACED_FROM = 0.6F;
    private static final float PLACED_SECONDS = 0.35F;
    private static final float PLACED_OVER = 7;
    /** Where the cup is held for pouring, its bottom, pixels, and from where on it tips by itself. */
    private static final float POUR_X = 3.5F;
    private static final float POUR_Y = 7.2F;
    private static final float POUR_FROM_X = 2.6F;
    private static final float POUR_FROM_Y = 2.2F;
    private static final float GRIP = 5;
    private static final float TIP = 78;
    private static final float TIP_PER_SECOND = 3.2F;
    private static final float RUNS_FROM = 0.85F;
    private static final float POUR_SECONDS = 1.8F;
    private static final float MOUTH_Y = 11.1F;
    /** What falls in: from how high, how long, and how long after the one before. */
    private static final float FALL = 12;
    private static final float FALL_SECONDS = 0.38F;
    private static final float STRAW_SECONDS = 0.5F;
    private static final float NEXT_SECONDS = 0.3F;
    private static final float FIRST_SECONDS = 0.35F;
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
    private int stage;
    private float aside;
    private float placed;
    private float tip;
    private float filled;
    private boolean pouredTold;
    /** What fell in so far, and how long ago the last of it began to. */
    private int fallen;
    private float falling;
    private boolean doneTold;

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DiegesisClient.registerBehavior(CupStation.ID, view -> new CupClient()));
    }

    @Override
    public void onOpen(StationView view) {
        hint = "";
        shaken = 0;
        waiting = 0;
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
            if (stage == CupStation.MIXING) {
                drawCup(view, pose, buffers, light, 1);
            }
        }));
        view.node(POURER).ifPresent(node -> node.setPainter((pose, buffers, light) -> {
            if (stage != CupStation.MIXING) {
                Vector3f at = node.scenePosition();
                float away = 1 - ease(aside);
                float tipped = ease(tip);
                pose.pushPose();
                pose.translate(ASIDE * away + (POUR_X - at.x) * tipped, 3 * Mth.sin(away * Mth.PI) + (POUR_Y - at.y) * tipped, 0);
                pose.translate(0, GRIP, 0);
                pose.mulPose(Axis.ZP.rotationDegrees(-TIP * tipped));
                pose.translate(0, -GRIP, 0);
                drawCup(view, pose, buffers, light, 1 - filled);
                pose.popPose();
            }
        }));
        view.node("product").ifPresent(node -> node.setPainter((pose, buffers, light) -> drawProduct(view, pose, buffers, light)));
        view.node("pour_stream").ifPresent(node -> node.setPainter((pose, buffers, light) -> {
            if (isRunning()) {
                BatterPainter.drawStream(pose, buffers, light, MOUTH_Y, DrinkParts.surface(filled), color());
            }
        }));
        shown = in(view);
        level = CupPainter.level(shown);
        refresh(view);
    }

    /** With so much of what is in it. */
    private void drawCup(StationView view, PoseStack pose, MultiBufferSource buffers, int light, float left) {
        List<ItemStack> arrived = added.arrived(shown);
        if (left < 0.3F) {
            // The ice went with the rest.
            arrived = arrived.stream().filter(CupContents::isPoured).toList();
        }
        if (!arrived.isEmpty() && left > 0.01F) {
            CupPainter.draw(pose, buffers, light, arrived, level * left, drink, shaken / SHAKEN, slosh, time);
        }
        if (view.isBlockHidden() && view.level().getBlockEntity(view.pos()) instanceof GlassDrinkCupBlockEntity cup) {
            pose.pushPose();
            pose.translate(-MIDDLE, 0, -MIDDLE);
            TankPainter.draw(view, cup.getBlockState(), Minecraft.getInstance().getBlockRenderer().getBlockModel(cup.getBlockState()), pose, buffers);
            pose.popPose();
        }
    }

    /** The cup the drink is served in, and what falls into it. */
    private void drawProduct(StationView view, PoseStack pose, MultiBufferSource buffers, int light) {
        DrinkParts parts = DrinkParts.of(drink);
        if (stage == CupStation.MIXING || placed <= 0 || parts == null) {
            return;
        }
        pose.pushPose();
        pose.translate(0, PLACED_OVER * (1 - ease(placed)), 0);
        float size = Math.min(1, 2 * placed);
        pose.scale(size, size, size);
        parts.drawCup(view, pose, buffers, light, filled);
        List<DrinkParts.Piece> pieces = parts.pieces();
        for (int i = 0; i < Math.min(pieces.size(), fallen); i++) {
            DrinkParts.Piece piece = pieces.get(i);
            float over = i < fallen - 1 ? 0 : over(piece, falling);
            parts.drawPiece(view, piece, pose, buffers, light, over);
        }
        pose.popPose();
    }

    /** How high over its place something still is that began to fall so long ago. */
    private static float over(DrinkParts.Piece piece, float since) {
        float share = Mth.clamp(since / seconds(piece), 0, 1);
        // A straw is let down, all else drops.
        return FALL * (piece.kind() == DrinkParts.Kind.STRAW ? 1 - ease(share) : 1 - share * share);
    }

    private static float seconds(DrinkParts.Piece piece) {
        return piece.kind() == DrinkParts.Kind.STRAW ? STRAW_SECONDS : FALL_SECONDS;
    }

    private static float ease(float share) {
        return share * share * (3 - 2 * share);
    }

    /** Of the drink as it runs. */
    private int color() {
        DrinkParts parts = DrinkParts.of(drink);
        int batter = FoodColors.batter(drink, ItemStack.EMPTY);
        return parts == null ? batter : parts.color(batter);
    }

    private boolean isRunning() {
        return stage == CupStation.SHAKEN && tip >= RUNS_FROM && filled < 1;
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
        if (waiting > 0 && (waiting -= 0.05F) <= 0 && stage == CupStation.MIXING) {
            // The server made nothing of it.
            shaken = 0;
        }
    }

    @Override
    public void frame(StationView view, float deltaSeconds) {
        book.frame(view, deltaSeconds);
        added.frame(view, deltaSeconds);
        time += deltaSeconds;
        serve(view, deltaSeconds);
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

    /** All that comes after the shaking. */
    private void serve(StationView view, float deltaSeconds) {
        int now = view.isInUse() ? CupStation.stage(view.state()) : CupStation.MIXING;
        if (now != stage) {
            stage = now;
            if (stage == CupStation.MIXING) {
                aside = 0;
                placed = 0;
                tip = 0;
                filled = 0;
                fallen = 0;
            } else if (stage == CupStation.POURED) {
                filled = 1;
                fallen = 0;
                falling = -FIRST_SECONDS;
            }
            pouredTold = false;
            doneTold = false;
            refresh(view);
        }
        NodeView pourer = view.node(POURER).orElse(null);
        if (stage == CupStation.MIXING) {
            if (pourer != null) {
                pourer.setEnabled(false);
            }
            view.loopSound(POURER, DiegesisSounds.POUR, 0, 1);
            return;
        }
        shaken = SHAKEN;
        aside = Math.min(1, aside + deltaSeconds / ASIDE_SECONDS);
        if (aside > PLACED_FROM) {
            if (placed == 0) {
                view.playSound(SoundEvents.GLASS_PLACE, "product", 0.5F, 1.3F);
            }
            placed = Math.min(1, placed + deltaSeconds / PLACED_SECONDS);
        }
        boolean held = pourer != null && pourer.isHeld();
        if (pourer != null) {
            pourer.setEnabled(stage == CupStation.SHAKEN && aside >= 1 && placed >= 1 && filled < 1 || held);
        }
        Vector3f at = held ? pourer.scenePosition() : null;
        boolean over = at != null && at.x > POUR_FROM_X && at.y > POUR_FROM_Y && stage == CupStation.SHAKEN && filled < 1;
        tip = Mth.clamp(tip + (over ? 1 : -1) * TIP_PER_SECOND * deltaSeconds, 0, 1);
        if (isRunning() && over) {
            filled = Math.min(1, filled + deltaSeconds / POUR_SECONDS);
        }
        view.loopSound(POURER, DiegesisSounds.POUR, isRunning() ? 0.6F : 0, 0.9F + 0.4F * filled);
        if (filled >= 1 && stage == CupStation.SHAKEN && !pouredTold && view.isFocused()) {
            pouredTold = true;
            view.sendAction(CupStation.ACTION_POURED, new CompoundTag());
        }
        if (stage == CupStation.POURED) {
            fall(view, deltaSeconds);
        } else if (stage == CupStation.DONE) {
            fallen = Integer.MAX_VALUE;
        }
    }

    /** One after the other, the straw last; then the drink is done. */
    private void fall(StationView view, float deltaSeconds) {
        DrinkParts parts = DrinkParts.of(drink);
        List<DrinkParts.Piece> pieces = parts == null ? List.of() : parts.pieces();
        DrinkParts.Piece last = fallen > 0 && fallen <= pieces.size() ? pieces.get(fallen - 1) : null;
        float before = falling;
        falling += deltaSeconds;
        if (last != null && before < seconds(last) && falling >= seconds(last)) {
            switch (last.kind()) {
                case ICE -> view.playSound(BakeriesSounds.PUT_ON_ICE.get(), "product", 0.8F, 1 + 0.1F * fallen);
                case CREAM -> view.playSound(SoundEvents.SNOW_PLACE, "product", 0.6F, 1.2F);
                default -> { }
            }
        }
        if (falling < (last == null ? 0 : seconds(last) + (fallen < pieces.size() ? NEXT_SECONDS - FALL_SECONDS / 2 : 0))) {
            return;
        }
        if (fallen < pieces.size()) {
            fallen++;
            falling = 0;
        } else if (!doneTold && view.isFocused()) {
            doneTold = true;
            view.sendAction(CupStation.ACTION_DONE, new CompoundTag());
        }
    }

    private void shake(StationView view, float way) {
        if (way == 0 || stage != CupStation.MIXING || made == null || waiting > 0) {
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
        if (!"glass".equals(node) || stage != CupStation.MIXING || !CupContents.accepts(view.level(), shown, stack)) {
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
        if (made != before && stage == CupStation.MIXING) {
            shaken = 0;
            waiting = 0;
        }
        boolean mixing = stage == CupStation.MIXING;
        if (made != null || mixing) {
            drink = made == null ? ItemStack.EMPTY : made.getResultItem(view.level().registryAccess());
        }
        List<ItemStack> in = shown;
        view.setInventoryFilter(stack -> mixing && CupContents.accepts(view.level(), in, stack));
        view.node(CUP).ifPresent(node -> node.setEnabled(mixing && made != null || node.isHeld()));
        view.node("glass").ifPresent(node -> node.setEnabled(mixing));
        String next = stage == CupStation.SHAKEN ? "pour" : stage != CupStation.MIXING ? "serve" : shown.isEmpty() ? "empty" : made == null ? "more" : "shake";
        Component told = drink.isEmpty() ? Component.translatable(LANG + next)
                : Component.translatable(LANG + next, drink.getHoverName());
        view.node(CUP).ifPresent(node -> node.setTooltip(told));
        view.node("glass").ifPresent(node -> node.setTooltip(told));
        view.node(POURER).ifPresent(node -> node.setTooltip(told));
        if (view.isFocused() && !told.getString().equals(hint)) {
            hint = told.getString();
            view.showCaption(told, CAPTION_SECONDS);
        }
    }
}
