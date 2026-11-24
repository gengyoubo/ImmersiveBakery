package github.com.gengyoubo.immersivebakery.client;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.DiegesisSounds;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;
import com.renyigesai.bakeries.block.blender.BlenderBlockEntity;
import com.renyigesai.bakeries.recipe.BlenderRecipe;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.blender.BlenderLiquids;
import github.com.gengyoubo.immersivebakery.blender.BlenderRecipes;
import github.com.gengyoubo.immersivebakery.blender.BlenderStation;
import github.com.gengyoubo.immersivebakery.client.recipebook.RecipeBook;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Vector3f;

/** What lies and turns in the bowl, for whoever works at the blender and everybody near. */
@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BlenderClient implements StationBehavior {
    private static final String LANG = "immersive_bakery.blender.";
    private static final String BOOK = "book_open";
    private static final String MIXING_GLANCE = "mixing";
    /** How far the glance turns the camera about its target, so that the book and the timer keep their place on the screen. */
    private static final float GLANCE_TURN = -26.7F;
    private static final float GLANCE_TIME = 0.35F;
    /** Bakeries raises the head by 25 degrees, which leaves the whisk between the camera and the bowl. */
    private static final float HEAD_UP = -45;
    private static final int SLOTS = BlenderStation.INGREDIENT_SLOTS;
    private static final SlotRef RESULT = SlotRef.of(BlenderStation.OUTPUT, 0);
    private static final Vector3f ITEM_MIDDLE = new Vector3f(8, 8, 8);
    /** The inside of the bowl: how high liquid stands with one bottle and with more, and batter that is ready. */
    private static final float BOWL_HALF_WIDTH = 1.99F;
    private static final float BOWL_FLOOR = 3;
    private static final float ONE_BOTTLE = 5.4F;
    private static final float PER_BOTTLE = 0.3F;
    private static final float FULL = 7.2F;
    private static final float BATTER = 6.6F;
    /** Liquid lower than this over the floor is not drawn. */
    private static final float DRAINED = 0.1F;
    /** Where solid ingredients lie while there is no liquid to float on. */
    private static final float HEAP = 4.6F;
    /** Where the scene puts the item nodes and the result. */
    private static final float ITEM_REST = 6.5F;
    private static final float RESULT_REST = 6.2F;
    private static final float LUMP_REST = 4.5F;
    /** A result in a container of its own stands beside the machine. */
    private static final Vector3f SERVED_OFFSET = new Vector3f(-6.2F, 0.1F - RESULT_REST, -1);
    /** Radians a second of the whisk, which Bakeries turns a tenth of a round each tick, and of what it stirs. */
    private static final float STIR_SPEED = 7;
    private static final float POUR_TIME = 1.1F;
    private static final float POUR_FROM = 0.3F;
    private static final float POUR_UNTIL = 0.85F;
    private static final float POUR_FALL = 0.12F;
    /** The vessel beside the whisk, and its lip over the bowl, where the scene puts the stream. */
    private static final Vector3f POUR_HAND = new Vector3f(5.4F, 11.4F, 2.4F);
    private static final float POUR_LIP = 11;

    private final BakingTimer timer = new BakingTimer();
    private final RecipeBook book = new RecipeBook(new BlenderBook());
    private final ItemStack[] shown = new ItemStack[SLOTS];
    private final Map<Item, Boolean> usable = new HashMap<>();
    /** Liquids still to be seen poured; the first one is being poured. */
    private final Deque<Pour> pours = new ArrayDeque<>();
    private float pourTime;
    @Nullable
    private BlenderRecipe recipe;
    private boolean powered;
    private boolean mixed;
    private boolean glancing;
    private boolean resultServed;
    /** The recipe ends as a lump in the bowl that takes the liquid up, not as batter to be scooped. */
    private boolean kneaded;
    private float progress;
    private float angle;
    private float speed;
    private int color = FoodColors.FALLBACK;
    private float surface = BOWL_FLOOR;

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DiegesisClient.registerBehavior(BlenderStation.ID, view -> new BlenderClient()));
    }

    private record Pour(ItemStack full, int bottles) {
    }

    public static float headAngle(BlenderBlockEntity blender, float degrees) {
        return DiegesisClient.station(blender.getBlockPos())
                .filter(view -> view.type().id().equals(BlenderStation.ID) && view.isInUse()).map(view -> HEAD_UP).orElse(degrees);
    }

    public BlenderClient() {
        Arrays.fill(shown, ItemStack.EMPTY);
    }

    @Override
    public void onOpen(StationView view) {
        usable.clear();
        view.setInventoryFilter(stack -> usable.computeIfAbsent(stack.getItem(), item -> BlenderRecipes.isIngredient(view.level(), stack)
                || BlenderRecipes.isContainer(view.level(), stack)));
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
        pours.clear();
        glancing = false;
        Arrays.fill(shown, ItemStack.EMPTY);
        view.node("power_label").ifPresent(node -> node.setPainter((pose, buffers, light) ->
                OvenPanel.drawLabel(pose, buffers, "⏻", 0.25F, 0)));
        view.node(BOOK).ifPresent(node -> node.setPainter(book));
        view.node("batter").ifPresent(node -> node.setPainter((pose, buffers, light) -> {
            if (surface > BOWL_FLOOR + DRAINED) {
                pose.pushPose();
                pose.translate(0, surface, 0);
                BatterPainter.drawSurface(pose, buffers, light, BOWL_HALF_WIDTH, color, angle, progress);
                pose.popPose();
            }
        }));
        view.node("stream").ifPresent(node -> node.setPainter((pose, buffers, light) -> {
            if (pourTime > POUR_FROM && !pours.isEmpty()) {
                float fall = (POUR_LIP - surface) / POUR_FALL;
                float top = POUR_LIP - Math.max(0, pourTime - POUR_UNTIL) * fall;
                float bottom = Math.max(surface, POUR_LIP - (pourTime - POUR_FROM) * fall);
                if (top > bottom) {
                    BatterPainter.drawStream(pose, buffers, light, top, bottom, FoodColors.liquid(pours.peek().full()));
                }
            }
        }));
        timer.setup(view);
        refresh(view, true);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        refresh(view, false);
    }

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (BOOK.equals(node)) {
            book.click(view, button);
            return true;
        }
        if ("scoop".equals(node) && mixed && recipe != null && recipe.getContainer().is(stack.getItem())) {
            view.sendAction(BlenderStation.ACTION_SCOOP, new CompoundTag());
            return true;
        }
        if ("bowl".equals(node) && Arrays.stream(shown).anyMatch(stored -> !BlenderLiquids.filled(stored, stack).isEmpty())) {
            view.sendAction(BlenderStation.ACTION_DRAW, new CompoundTag());
            return true;
        }
        return false;
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        var player = Minecraft.getInstance().player;
        if (event instanceof SceneEvent.Click click && BOOK.equals(click.node())) {
            book.click(view, click.button());
        } else if (event instanceof SceneEvent.Click click && player != null && player.inventoryMenu.getCarried().isEmpty()) {
            if ("scoop".equals(click.node())) {
                view.sendAction(BlenderStation.ACTION_SCOOP_FROM_INVENTORY, new CompoundTag());
            } else if ("bowl".equals(click.node())) {
                view.sendAction(BlenderStation.ACTION_DRAW_FROM_INVENTORY, new CompoundTag());
            }
        }
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        if (BlenderStation.EFFECT_SCOOPED.equals(effect)) {
            view.playSound(DiegesisSounds.SLOSH, "batter", 0.6F, 1);
        } else if (BlenderStation.EFFECT_POURED.equals(effect)) {
            pours.add(new Pour(ItemStack.of(data.getCompound(BlenderStation.POURED)), data.getInt(BlenderStation.BOTTLES)));
        }
    }

    @Override
    public void tick(StationView view) {
        boolean inUse = view.isInUse();
        if (view.isFocused()) {
            // The lowered head covers the bowl from above: look in under it.
            if (powered && view.currentGlance().isEmpty()) {
                view.glance(MIXING_GLANCE);
            } else if (!powered && view.currentGlance().isPresent()) {
                view.glanceBack();
            }
        }
        boolean nowGlancing = view.isFocused() && view.currentGlance().isPresent();
        if (nowGlancing != glancing) {
            glancing = nowGlancing;
            view.node("panel").ifPresent(node -> node.rotateTo(glancing ? GLANCE_TURN : 0, 0, 0, GLANCE_TIME));
        }
        view.node(BlenderStation.POWER).ifPresent(node -> node.setVisible(inUse));
        view.node("container").ifPresent(node -> node.setEnabled(inUse && !powered));
        timer.update(view, inUse && recipe != null, 1 - view.state().getControl(BlenderStation.PROGRESS));
    }

    @Override
    public void frame(StationView view, float deltaSeconds) {
        float target = mixed ? 1 : powered && !view.isInUse() ? 0.5F : view.state().getControl(BlenderStation.PROGRESS);
        // A new batch starts over with whole ingredients.
        progress = target < progress - 0.5F ? target : Mth.lerp(Math.min(1, deltaSeconds * 6), progress, target);
        speed = Mth.lerp(Math.min(1, deltaSeconds * 4), speed, powered ? STIR_SPEED : 0);
        angle = (angle + speed * deltaSeconds) % Mth.TWO_PI;
        pour(view, deltaSeconds);
        book.frame(view, deltaSeconds);

        int floating = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            floating += floats(slot) ? 1 : 0;
        }
        flow(deltaSeconds);
        float lying = Math.max(surface, HEAP);
        view.node("result").ifPresent(node -> {
            if (resultServed) {
                node.moveTo(SERVED_OFFSET.x, SERVED_OFFSET.y, SERVED_OFFSET.z, 0);
            } else {
                node.moveTo(0, lying - RESULT_REST, 0, 0);
            }
        });
        view.node("lump").ifPresent(node -> {
            node.setVisible(kneaded && progress > 0.1F);
            node.moveTo(0, lying - LUMP_REST + 0.1F, 0, 0);
            node.scaleTo(progress, 0);
            node.rotateTo(0, 0, -angle * Mth.RAD_TO_DEG, 0);
        });
        float size = (floating <= 2 ? 1.2F : floating <= 5 ? 1 : 0.8F) * (1 - 0.9F * progress);
        float radius = floating <= 1 ? 0 : floating <= 4 ? 0.95F : 1.25F;
        int place = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            NodeView node = view.node("item_" + slot).orElse(null);
            if (node == null) {
                continue;
            }
            boolean floats = floats(slot);
            node.setVisible(floats && !mixed);
            node.setEnabled(floats && !mixed && !powered);
            if (floats) {
                float at = angle + place * Mth.TWO_PI / floating;
                float height = lying - ITEM_REST + 0.1F + 0.06F * place;
                node.moveTo(radius * Mth.cos(at), height, radius * Mth.sin(at), 0);
                node.rotateTo(0, -at * Mth.RAD_TO_DEG, 0, 0);
                node.scaleTo(size, 0);
                place++;
            }
        }
    }

    private void refresh(StationView view, boolean quiet) {
        StationState state = view.state();
        powered = state.getControl(BlenderStation.POWERED) > 0;
        mixed = state.getControl(BlenderStation.MIXED) > 0;
        ItemStack[] items = state.getItems(BlenderStation.INGREDIENTS);
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack before = shown[slot];
            ItemStack now = items[slot].copy();
            shown[slot] = now;
            if (!quiet && !now.isEmpty() && !BlenderLiquids.isLiquid(now) && !ItemStack.isSameItem(before, now)) {
                view.playSound(SoundEvents.SLIME_BLOCK_STEP, "batter", 0.3F, 1.3F);
            }
        }

        recipe = BlenderRecipes.find(view.level(), List.of(items));
        if (recipe != null) {
            timer.setIcon(view, recipe.getResultItem(null).getItem());
        }
        ItemStack result = state.getItem(RESULT);
        boolean served = !result.isEmpty() && BlenderRecipes.isServedInContainer(view.level(), result);
        resultServed = served;
        view.node("result").ifPresent(node -> node.setEnabled(!result.isEmpty() && !powered));
        kneaded = recipe != null && recipe.getContainer().isEmpty();
        if (kneaded) {
            view.node("lump").ifPresent(node -> node.setVisual(Visual.ofItem(recipe.getResultItem(null).getItem()).withAnchor(ITEM_MIDDLE)));
        }
        view.node("mouth").ifPresent(node -> node.setEnabled(!powered && !mixed));
        view.node("container").ifPresent(node -> node.setGhostItems(
                recipe == null || recipe.getContainer().isEmpty() ? List.of() : List.of(recipe.getContainer())));
        Component scoop = mixed && recipe != null ? Component.translatable(LANG + "scoop", recipe.getContainer().getHoverName()) : null;
        view.node("scoop").ifPresent(node -> {
            node.setEnabled(mixed);
            node.setTooltip(scoop);
        });
        ItemStack liquid = lastLiquid();
        view.node("bowl").ifPresent(node -> {
            node.setEnabled(!powered && !mixed && !liquid.isEmpty());
            node.setTooltip(liquid.isEmpty() ? null : Component.translatable(LANG + "draw", BlenderLiquids.name(liquid),
                    new ItemStack(BlenderLiquids.filled(liquid, new ItemStack(Items.GLASS_BOTTLE)).isEmpty()
                            ? Items.BUCKET : Items.GLASS_BOTTLE).getHoverName()));
        });
        view.node(BlenderStation.POWER).ifPresent(node -> {
            node.setTint(powered ? 0xFF67C99D : 0xFFBF664F);
            node.setTooltip(powered ? Component.translatable(LANG + "power_off")
                    : scoop != null ? scoop : Component.translatable(LANG + (recipe == null ? "no_recipe" : "power_on")));
        });
    }

    /** A bucket or bottle comes over the bowl and is emptied into it. */
    private void pour(StationView view, float deltaSeconds) {
        NodeView hand = view.node("hand").orElse(null);
        if (hand == null) {
            return;
        }
        if (pours.isEmpty()) {
            hand.setVisible(false);
            pourTime = 0;
            return;
        }
        if (pourTime == 0) {
            hand.setVisual(Visual.ofItem(pours.peek().full().getItem()).withAnchor(ITEM_MIDDLE));
            hand.moveTo(POUR_HAND.x, POUR_HAND.y, POUR_HAND.z, 0);
        }
        pourTime += deltaSeconds;
        float tip = Mth.clamp(pourTime / POUR_FROM, 0, 1) * Mth.clamp((POUR_TIME - pourTime) / (POUR_TIME - POUR_UNTIL), 0, 1);
        hand.setVisible(true);
        hand.rotateTo(0, 0, -100 * tip, 0);
        if (pourTime > POUR_FROM && pourTime - deltaSeconds <= POUR_FROM) {
            view.playSound(DiegesisSounds.POUR, "batter", 0.5F, 1);
        }
        if (pourTime >= POUR_TIME) {
            pours.poll();
            pourTime = 0;
        }
    }

    private boolean floats(int slot) {
        return !shown[slot].isEmpty() && !BlenderLiquids.isLiquid(shown[slot]);
    }

    private ItemStack lastLiquid() {
        for (int slot = SLOTS - 1; slot >= 0; slot--) {
            if (BlenderLiquids.isStored(shown[slot])) {
                return shown[slot];
            }
        }
        return ItemStack.EMPTY;
    }

    /** The liquid rises and drains with what is in the bowl; mixing turns it into batter, or a lump takes it up. */
    private void flow(float deltaSeconds) {
        int bottles = 0;
        int afterSet = 0;
        int red = 0;
        int green = 0;
        int blue = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = shown[slot];
            int count = BlenderLiquids.isStored(stack) ? stack.getCount() - stillToFlow(stack) : 0;
            if (count > 0) {
                int own = FoodColors.liquid(stack);
                red += count * (own >> 16 & 0xFF);
                green += count * (own >> 8 & 0xFF);
                blue += count * (own & 0xFF);
                bottles += count;
                afterSet += count - 1;
            }
        }
        boolean mixing = recipe != null && progress > 0.01F;
        float level = levelOf(bottles);
        if (mixing) {
            level = Mth.lerp(progress, level, kneaded ? levelOf(afterSet) : BATTER);
        }
        boolean any = bottles > 0 || mixing && !kneaded;
        if (any) {
            int done = recipe == null ? color : FoodColors.batter(recipe.getResultItem(null), recipe.getContainer());
            int raw = bottles == 0 ? done : red / bottles << 16 | green / bottles << 8 | blue / bottles;
            int target = mixing ? FastColor.ARGB32.lerp(progress, raw, done) : raw;
            // The first liquid in an empty bowl has its own colour from the start; only a mixture changes.
            color = surface <= BOWL_FLOOR + DRAINED ? target : FastColor.ARGB32.lerp(Math.min(1, deltaSeconds * 5), color, target);
        }
        surface = Mth.lerp(Math.min(1, deltaSeconds * 4), surface, level);
    }

    private static float levelOf(int bottles) {
        return bottles <= 0 ? BOWL_FLOOR : Math.min(FULL, ONE_BOTTLE + PER_BOTTLE * (bottles - 1));
    }

    /** How many bottles of a stored liquid the player has yet to see flow into the bowl. */
    private int stillToFlow(ItemStack stored) {
        int bottles = 0;
        boolean first = true;
        for (Pour pour : pours) {
            boolean flowed = first && pourTime > POUR_FROM + POUR_FALL;
            first = false;
            if (!flowed && ItemStack.isSameItemSameTags(BlenderLiquids.store(pour.full()), stored)) {
                bottles += pour.bottles();
            }
        }
        return bottles;
    }
}
