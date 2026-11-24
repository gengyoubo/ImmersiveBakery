package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;
import com.renyigesai.bakeries.recipe.DoughCraftingRecipe;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.dough.DoughShaping;
import github.com.gengyoubo.immersivebakery.dough.DoughStation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Vector3f;

/** Dough on the board, what it can become over it, and the rolling pin. */
@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DoughClient implements StationBehavior {
    private static final String LANG = "immersive_bakery.dough_crafting_table.";
    private static final String CHOICE = "choice_";
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    private static final SlotRef DOUGH = SlotRef.of(DoughStation.DOUGH, 0);
    private static final float CAPTION_SECONDS = 4;
    /** The choices, scene pixels. */
    private static final int COLUMNS = 4;
    private static final int CHOICES = 8;
    private static final float CHOICE_PITCH = 4.4F;
    private static final float CHOICE_HALF = 2;
    private static final float CHOICE_ITEM = 3.2F;
    private static final float CHOICE_DEPTH = 0.4F;
    private static final int PLATE = 0xA01E140C;
    private static final int PLATE_PICKED = 0xE0D9962E;
    /** How far the pin rolls over the dough until it is a flat sheet, and between two sounds. */
    private static final float ROLLED_OUT = 36;
    private static final float ROLL_SOUND = 7;
    /** How long the dough is pressed to shape one piece, and all of them. */
    private static final float PRESS_PER_PIECE = 0.3F;
    private static final float PRESS_LEAST = 0.9F;
    private static final float PRESS_MOST = 2.4F;
    /** How long it was pressed when it is divided. */
    private static final float DIVIDED = 0.35F;
    /** A shorter press is a click, which takes the dough away. */
    private static final float CLICK = 0.2F;
    private static final float PRESS_SOUND = 0.3F;
    /** Faster than this, per frame, does not count. */
    private static final float FASTEST = 4;
    private static final float ANSWER_SECONDS = 1;
    /** What lies on the board is shown piece by piece, up to so many. */
    private static final int MOST_PIECES = 9;
    private static final float PIECES_WIDE = 11;
    private static final float PIECES_DEEP = 9.6F;
    private static final float MOST_PIECE = 6;
    private static final float PIECE_GAP = 0.88F;
    private static final float MIDDLE_Z = 8;
    /** The pin: where the scene puts and carries it, how it lies on the board in Bakeries' model, and where it lies on the table. */
    private static final Vector3f PIN_HOME = new Vector3f(7, 17, 0.8F);
    private static final Vector3f PIN_ON_BOARD = new Vector3f(7.95F, 18, 7.64F);
    private static final float PIN_ON_BOARD_TURN = 45;
    private static final float PIN_RADIUS = 1;
    private static final float TABLE_TOP = 16;
    private static final float BOARD_TOP = 17;
    private static final float BOARD_FROM = 1.2F;
    private static final float PIN_SECONDS = 0.15F;

    private final Map<Item, Boolean> usable = new HashMap<>();
    private List<DoughCraftingRecipe> recipes = List.of();
    private ItemStack dough = ItemStack.EMPTY;
    /** What one piece of the dough becomes. */
    private ItemStack product = ItemStack.EMPTY;
    @Nullable
    private ResourceLocation picked;
    private int firstRow;
    private boolean cleared = true;
    private float rolled;
    private float pressed;
    private float pressedNow;
    private float sinceSound;
    private float waiting;
    private float lastZ = Float.NaN;
    private ItemStack[] products = new ItemStack[0];
    private float roll;
    private boolean pinPlaced;
    private boolean pinInUse;
    private float pinHeight = Float.NaN;
    private boolean wasInUse;
    private String hint = "";

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DiegesisClient.registerBehavior(DoughStation.ID, view -> new DoughClient()));
    }

    @Override
    public void onOpen(StationView view) {
        usable.clear();
        view.setInventoryFilter(stack -> usable.computeIfAbsent(stack.getItem(), item -> !DoughShaping.recipes(view.level(), stack).isEmpty()));
        hint = "";
        refresh(view);
    }

    @Override
    public void onSceneReady(StationView view) {
        pinPlaced = false;
        view.node("work").ifPresent(node -> node.setPainter((pose, buffers, light) -> {
            if (!cleared) {
                drawProducts(pose, buffers, light);
            } else if (!dough.isEmpty()) {
                drawDough(pose, buffers, light);
            }
        }));
        for (int i = 0; i < CHOICES; i++) {
            int place = i;
            view.node(CHOICE + i).ifPresent(node -> node.setPainter((pose, buffers, light) -> {
                DoughCraftingRecipe recipe = choice(place);
                if (recipe != null) {
                    drawChoice(view, pose, buffers, recipe);
                }
            }));
        }
        refresh(view);
    }

    @Override
    public void onClose(StationView view) {
        refresh(view);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        refresh(view);
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && click.node() != null && click.node().startsWith(CHOICE)) {
            pick(view, click.node());
        } else if (event instanceof SceneEvent.Release release && "hand".equals(release.node())) {
            if (pressedNow < CLICK && pressed == 0 && mayPress()) {
                view.sendAction(DoughStation.ACTION_TAKE, new CompoundTag());
            }
            pressedNow = 0;
            refresh(view);
        }
    }

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (!node.startsWith(CHOICE)) {
            return false;
        }
        pick(view, node);
        return true;
    }

    @Override
    public boolean onScroll(StationView view, String node, double amount) {
        int rows = (recipes.size() + COLUMNS - 1) / COLUMNS;
        if (node == null || !node.startsWith(CHOICE) || rows <= CHOICES / COLUMNS) {
            return false;
        }
        firstRow = Mth.clamp(firstRow - (int) Math.signum(amount), 0, rows - CHOICES / COLUMNS);
        refresh(view);
        return true;
    }

    @Override
    public void tick(StationView view) {
        if (waiting > 0 && (waiting -= 0.05F) <= 0) {
            // The server made nothing of it.
            pressed = 0;
        }
        if (view.isInUse() != wasInUse) {
            wasInUse = view.isInUse();
            refresh(view);
        }
    }

    @Override
    public void frame(StationView view, float deltaSeconds) {
        press(view, deltaSeconds);
        NodeView pin = view.node("pin").orElse(null);
        if (pin == null) {
            return;
        }
        boolean inUse = view.isInUse();
        boolean held = pin.isHeld();
        Vector3f at = pin.scenePosition();
        boolean over = inUse && held && !dough.isEmpty() && cleared && Math.abs(at.z - MIDDLE_Z) < DoughPainter.radius(flatness()) + PIN_RADIUS
                && pressed == 0;
        if (held) {
            if (!Float.isNaN(lastZ)) {
                float way = at.z - lastZ;
                roll = (roll + way / PIN_RADIUS * Mth.RAD_TO_DEG) % 360;
                if (over && view.isFocused()) {
                    rollOver(view, Math.min(Math.abs(way), FASTEST));
                }
            }
            lastZ = at.z;
        } else {
            lastZ = Float.NaN;
        }
        float height = !inUse ? PIN_ON_BOARD.y
                : (held && at.z > BOARD_FROM ? BOARD_TOP : TABLE_TOP) + PIN_RADIUS + (over ? DoughPainter.height(flatness()) : 0);
        boolean moved = !pinPlaced || inUse != pinInUse;
        // Only on a change: a motion begun anew every frame never arrives. Carried, the pin's height follows the hand.
        if (moved || held || height != pinHeight) {
            float seconds = !pinPlaced ? 0 : PIN_SECONDS;
            float y = height - at.y;
            view.node("pin_turn").ifPresent(node -> {
                if (inUse) {
                    node.moveTo(0, held ? y : height - PIN_HOME.y, 0, held ? 0 : seconds);
                } else {
                    node.moveTo(PIN_ON_BOARD.x - PIN_HOME.x, PIN_ON_BOARD.y - PIN_HOME.y, PIN_ON_BOARD.z - PIN_HOME.z, seconds);
                }
                if (moved) {
                    node.rotateTo(0, inUse ? 0 : PIN_ON_BOARD_TURN, 0, seconds);
                }
            });
            pinPlaced = true;
            pinInUse = inUse;
            pinHeight = held ? Float.NaN : height;
        }
        view.node("pin_roll").ifPresent(node -> node.rotateTo(roll, 0, 0, 0));
    }

    private void rollOver(StationView view, float way) {
        if (isRolledOut()) {
            return;
        }
        rolled = Math.min(ROLLED_OUT, rolled + way);
        sound(view, way, 0.8F + 0.4F * flatness());
        if (isRolledOut()) {
            refresh(view);
        }
    }

    /** The dough is pressed and held: it is divided, and one piece after the other gets its shape. */
    private void press(StationView view, float deltaSeconds) {
        NodeView hand = view.node("hand").orElse(null);
        if (hand == null || !hand.isHeld() || !mayPress()) {
            return;
        }
        int before = shapedPieces();
        pressedNow += deltaSeconds;
        if (pressedNow < CLICK) {
            return;
        }
        pressed = Math.min(pressLength(), pressed + deltaSeconds);
        sinceSound += deltaSeconds;
        if (sinceSound > PRESS_SOUND) {
            sinceSound = 0;
            view.playSound(SoundEvents.SLIME_BLOCK_STEP, "work", 0.35F, 1.1F);
        }
        if (shapedPieces() > before) {
            view.playSound(SoundEvents.SLIME_BLOCK_PLACE, "work", 0.3F, 1.5F);
        }
        if (pressed >= pressLength()) {
            waiting = ANSWER_SECONDS;
            CompoundTag data = new CompoundTag();
            data.putString(DoughStation.RECIPE, picked.toString());
            view.sendAction(DoughStation.ACTION_SHAPE, data);
        }
    }

    private void sound(StationView view, float way, float pitch) {
        sinceSound += way;
        if (sinceSound > ROLL_SOUND) {
            sinceSound = 0;
            view.playSound(SoundEvents.SLIME_BLOCK_STEP, "work", 0.35F, pitch);
        }
    }

    /** What is flat is rolled out before it is shaped, all else is shaped from the lump. */
    private boolean mayPress() {
        return cleared && !dough.isEmpty() && picked != null && (isRolledOut() || !isFlat()) && waiting <= 0;
    }

    private boolean isRolledOut() {
        return rolled >= ROLLED_OUT;
    }

    private float flatness() {
        return rolled / ROLLED_OUT;
    }

    private boolean isFlat() {
        return product.is(DoughShaping.ROLLED_FLAT);
    }

    private int pieces() {
        return Math.min(MOST_PIECES, Math.max(1, dough.getCount() * product.getCount()));
    }

    private float pressLength() {
        return Mth.clamp(pieces() * PRESS_PER_PIECE, PRESS_LEAST, PRESS_MOST);
    }

    private int shapedPieces() {
        return (int) (pieces() * Mth.clamp((pressed - DIVIDED) / (pressLength() - DIVIDED), 0, 1));
    }

    private void pick(StationView view, String node) {
        DoughCraftingRecipe recipe = choice(Integer.parseInt(node.substring(CHOICE.length())));
        if (recipe != null && waiting <= 0) {
            if (!recipe.getId().equals(picked)) {
                // Divided otherwise.
                pressed = 0;
            }
            picked = recipe.getId();
            view.playSound(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, "work", 0.6F, 1);
            refresh(view);
        }
    }

    @Nullable
    private DoughCraftingRecipe choice(int place) {
        int index = firstRow * COLUMNS + place;
        return cleared && index < recipes.size() ? recipes.get(index) : null;
    }

    private void drawDough(PoseStack pose, MultiBufferSource buffers, int light) {
        int color = FoodColors.batter(dough, ItemStack.EMPTY);
        if (pressed <= 0) {
            DoughPainter.draw(pose, buffers, light, color, flatness(), 1);
            return;
        }
        Grid grid = new Grid(pieces(), PIECES_WIDE);
        float divided = Mth.clamp(pressed / DIVIDED, 0, 1);
        int shaped = shapedPieces();
        float portion = Math.min(1, grid.size / 2 / DoughPainter.radius(flatness()));
        float size = Mth.lerp(Math.min(1, 2 * divided), 1, portion);
        for (int i = 0; i < pieces(); i++) {
            pose.pushPose();
            pose.translate(grid.x(i) * divided, 0, grid.z(i) * divided);
            if (i < shaped) {
                drawPiece(product, grid.size, pose, buffers, light);
            } else {
                pose.scale(1, Mth.lerp(flatness(), size, 1), 1);
                DoughPainter.draw(pose, buffers, light, color, flatness(), size);
            }
            pose.popPose();
        }
    }

    /** Each stack of them in an area of its own, side by side. */
    private void drawProducts(PoseStack pose, MultiBufferSource buffers, int light) {
        int stacks = made();
        int place = 0;
        for (ItemStack stack : products) {
            if (stack.isEmpty()) {
                continue;
            }
            Grid grid = new Grid(Math.min(MOST_PIECES, stack.getCount()), PIECES_WIDE / stacks);
            for (int i = 0; i < grid.pieces; i++) {
                pose.pushPose();
                pose.translate(stackX(place, stacks) + grid.x(i), 0, grid.z(i));
                drawPiece(stack, grid.size, pose, buffers, light);
                pose.popPose();
            }
            place++;
        }
    }

    private static void drawPiece(ItemStack stack, float size, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.scale(16, 16, 16);
        PeelRenderer.drawLying(stack, size * PIECE_GAP / 16, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** From left to right, seen from the front. */
    private static float stackX(int place, int stacks) {
        return ((stacks - 1) / 2.0F - place) * PIECES_WIDE / stacks;
    }

    private int made() {
        int made = 0;
        for (ItemStack stack : products) {
            made += stack.isEmpty() ? 0 : 1;
        }
        return made;
    }

    /** So many pieces in rows about the middle of an area, as large as they can be. */
    private static final class Grid {
        private final int pieces;
        private final int columns;
        private final int rows;
        private final float size;

        private Grid(int pieces, float wide) {
            this.pieces = pieces;
            int best = 1;
            for (int columns = 2; columns <= pieces; columns++) {
                if (size(pieces, columns, wide) > size(pieces, best, wide)) {
                    best = columns;
                }
            }
            columns = best;
            rows = Mth.positiveCeilDiv(pieces, columns);
            size = size(pieces, columns, wide);
        }

        private static float size(int pieces, int columns, float wide) {
            return Math.min(MOST_PIECE, Math.min(wide / columns, PIECES_DEEP / Mth.positiveCeilDiv(pieces, columns)));
        }

        private float x(int piece) {
            int row = piece / columns;
            int inRow = row == rows - 1 ? pieces - row * columns : columns;
            return ((inRow - 1) / 2.0F - piece % columns) * size;
        }

        /** The first row is the farthest. */
        private float z(int piece) {
            return ((rows - 1) / 2.0F - piece / columns) * size;
        }
    }

    private void drawChoice(StationView view, PoseStack pose, MultiBufferSource buffers, DoughCraftingRecipe recipe) {
        ItemStack product = DoughShaping.product(view.level(), dough, recipe);
        boolean isPicked = recipe.getId().equals(picked);
        pose.pushPose();
        // What is drawn here faces up from the node.
        pose.mulPose(Axis.XP.rotationDegrees(90));
        plate(pose.last(), buffers.getBuffer(RenderType.entityTranslucent(WHITE)), isPicked ? PLATE_PICKED : PLATE);
        pose.pushPose();
        pose.translate(0, 0, -0.5F);
        // As in the inventory, only flatter. Its front is +z.
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(CHOICE_ITEM, CHOICE_ITEM, CHOICE_DEPTH);
        Minecraft.getInstance().getItemRenderer().renderStatic(product, ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, pose, buffers, view.level(), 0);
        pose.popPose();
        if (product.getCount() > 1) {
            pose.pushPose();
            pose.translate(-1.3F, -1.35F, -1.4F);
            OvenPanel.drawLabel(pose, buffers, Integer.toString(product.getCount()), 0.16F, 0);
            pose.popPose();
        }
        pose.popPose();
    }

    private static void plate(PoseStack.Pose pose, VertexConsumer vertices, int color) {
        corner(pose, vertices, color, -CHOICE_HALF, -CHOICE_HALF);
        corner(pose, vertices, color, -CHOICE_HALF, CHOICE_HALF);
        corner(pose, vertices, color, CHOICE_HALF, CHOICE_HALF);
        corner(pose, vertices, color, CHOICE_HALF, -CHOICE_HALF);
    }

    private static void corner(PoseStack.Pose pose, VertexConsumer vertices, int color, float x, float y) {
        vertices.vertex(pose.pose(), x, y, 0).color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >>> 24)
                .uv(x < 0 ? 0 : 1, y < 0 ? 0 : 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), 0, 0, -1).endVertex();
    }

    private void refresh(StationView view) {
        StationState state = view.state();
        boolean inUse = view.isInUse();
        ItemStack now = inUse ? state.getItem(DOUGH) : ItemStack.EMPTY;
        boolean wasCleared = cleared;
        products = inUse ? state.getItems(DoughStation.PRODUCTS) : new ItemStack[0];
        int made = made();
        cleared = made == 0;
        if (!ItemStack.isSameItemSameTags(now, dough) || wasCleared && !cleared) {
            rolled = 0;
            pressed = 0;
            waiting = 0;
        } else if (now.getCount() != dough.getCount()) {
            // What was added is not rolled yet; what was divided lies there whole again.
            rolled *= (float) Math.min(now.getCount(), dough.getCount()) / now.getCount();
            pressed = 0;
        }
        if (!now.is(dough.getItem())) {
            recipes = DoughShaping.recipes(view.level(), now);
            firstRow = 0;
            if (recipes.stream().noneMatch(recipe -> recipe.getId().equals(picked))) {
                picked = recipes.size() == 1 ? recipes.get(0).getId() : null;
            }
        }
        dough = now.copy();
        var recipe = DoughShaping.recipe(view.level(), dough, picked);
        product = recipe == null ? ItemStack.EMPTY : DoughShaping.product(view.level(), dough, recipe);

        view.node("dough").ifPresent(node -> node.setEnabled(cleared && !mayPress()));
        view.node("hand").ifPresent(node -> node.setEnabled(mayPress() || node.isHeld()));
        view.node("board").ifPresent(node -> node.setEnabled(cleared));
        int place = 0;
        for (int i = 0; i < DoughStation.PRODUCT_SLOTS; i++) {
            boolean lies = i < products.length && !products[i].isEmpty();
            // A single stack lies all over the board.
            boolean alone = lies && made == 1;
            view.node("only_product_" + i).ifPresent(node -> node.setEnabled(alone));
            NodeView node = view.node("product_" + i).orElse(null);
            if (node != null) {
                node.setEnabled(lies && !alone);
                if (lies && !alone) {
                    node.moveTo(stackX(place, made), 0, 0, 0);
                }
            }
            place += lies ? 1 : 0;
        }
        layOutChoices(view);
        tell(view);
        var player = Minecraft.getInstance().player;
        if (view.isFocused() && player != null && dough.getCount() >= DoughStation.MOST_DOUGH
                && ItemStack.isSameItemSameTags(player.inventoryMenu.getCarried(), dough)) {
            // What the board does not take would be in the way of the pin.
            view.putAwayCarried();
        }
    }

    private void layOutChoices(StationView view) {
        int shown = Math.min(CHOICES, recipes.size() - firstRow * COLUMNS);
        for (int i = 0; i < CHOICES; i++) {
            NodeView node = view.node(CHOICE + i).orElse(null);
            if (node == null) {
                continue;
            }
            DoughCraftingRecipe recipe = view.isInUse() ? choice(i) : null;
            node.setVisible(recipe != null);
            node.setEnabled(recipe != null);
            if (recipe == null) {
                continue;
            }
            int row = i / COLUMNS;
            int inRow = Math.min(COLUMNS, shown - row * COLUMNS);
            int rows = (shown + COLUMNS - 1) / COLUMNS;
            node.moveTo(((inRow - 1) / 2.0F - i % COLUMNS) * CHOICE_PITCH, 0, (rows - 1 - row) * CHOICE_PITCH, 0);
            ItemStack product = DoughShaping.product(view.level(), dough, recipe);
            node.setTooltip(Component.translatable(LANG + "choice", product.getHoverName(), product.getCount()));
        }
    }

    private void tell(StationView view) {
        String next = !cleared ? "take" : dough.isEmpty() ? "insert" : picked == null ? "pick" : isFlat() && !isRolledOut() ? "roll" : "shape";
        view.node("pin").ifPresent(node -> node.setTooltip(Component.translatable(LANG + next)));
        view.node("hand").ifPresent(node -> node.setTooltip(Component.translatable(LANG + next)));
        if (view.isFocused() && !next.equals(hint)) {
            hint = next;
            view.showCaption(Component.translatable(LANG + next), CAPTION_SECONDS);
        }
    }
}
