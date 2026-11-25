package github.com.gengyoubo.immersivebakery.client;

import com.mojang.math.Axis;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.api.player.PlayerImmersionBook;
import github.com.gengyoubo.immersivebakery.api.player.PlayerImmersionClient;
import github.com.gengyoubo.immersivebakery.api.player.PlayerImmersionPoses;
import github.com.gengyoubo.immersivebakery.api.player.PlayerImmersionScreen;
import github.com.gengyoubo.immersivebakery.client.recipebook.RecipeBook;
import github.com.gengyoubo.immersivebakery.sieve.FlourSieve;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Vector3f;

/** The sieve before the player: things are put on it and shaken through. */
@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FlourSieveClient extends PlayerImmersionScreen {
    private static final String LANG = "immersive_bakery.flour_sieve.";
    private static final Vector3f ORIGIN = new Vector3f(0.12F, -0.06F, -0.8F);
    private static final float STROKE_EDGE = 0.78F;
    /** How far the sieve goes to each side, blocks, and how it tilts and lags behind there. */
    private static final float SWAY = 0.17F;
    private static final float TILT = 6;
    private static final float TURN = 8;
    private static final float BACK_SECONDS = 0.09F;
    /** Where the handle points, degrees from towards the player, and how long it is; from there what is meant for the sieve. */
    private static final float HANDLE = 35;
    private static final float HANDLE_LONG = 0.62F;
    private static final float NEAR = 0.12F;
    private static final float ROUND = 0.26F;
    /** A shorter press that moves less is a click, which takes what is on the sieve. */
    private static final int CLICK_MS = 250;
    private static final float CLICK_WAY = 0.15F;
    /** Powder: how much falls with a stroke and with the way moved, and how fast the sieve counts as at most. */
    private static final int STROKE_GRAINS = 90;
    private static final float TRICKLE = 60;
    private static final float FASTEST = 1;
    private static final float LONGEST_FRAME = 0.1F;
    private static final float MADE_SECONDS = 0.7F;
    private static final int SLOT = 18;
    private static final int COLUMNS = 9;
    private static final int SLOT_COLOR = 0x8C1E140C;
    private static final int PICKED_COLOR = 0xB0D9962E;

    private static final Vector3f BOOK = new Vector3f(-0.75F, 0.08F, -0.1F);
    private static final float BOOK_TURN = -52;
    private static final float BOOK_SIZE = 0.82F;

    private final FlourSievePainter painter = new FlourSievePainter();
    private final PlayerImmersionBook book;
    private ItemStack tool = ItemStack.EMPTY;
    private ItemStack input = ItemStack.EMPTY;
    private ItemStack output = ItemStack.EMPTY;
    private ItemStack made = ItemStack.EMPTY;
    private int progress;
    private int batches;
    /** The slot of the inventory whose stack is on the cursor, -1 for none. */
    private int picked = -1;
    private boolean held;
    private float grabX;
    private float grabOffset;
    private long grabTime;
    private boolean dragged;
    private float offset;
    private float lastOffset;
    private int side;
    private float shownProgress;
    private float arrived;
    private float trickle;
    private float madeAge = MADE_SECONDS;
    private float madeX;
    private long previousFrame = Util.getMillis();

    private FlourSieveClient(CompoundTag state) {
        super(Component.translatable(LANG + "title"));
        readState(state);
        book = new PlayerImmersionBook(this, new FlourSieveBook(() -> input, this::put), BOOK, BOOK_TURN, BOOK_SIZE);
    }

    /** What a page of the book asks for goes onto the sieve. */
    private void put(RecipeBook.Page page) {
        for (int slot : usable()) {
            if (minecraft != null && minecraft.player != null && page.ingredients().get(0).test(minecraft.player.getInventory().getItem(slot))) {
                sendAction(FlourSieve.PUT + slot);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            PlayerImmersionClient.register(FlourSieve.ID, FlourSieveClient::new);
            PlayerImmersionPoses.register(FlourSieve.ID, FlourSieveClient::pose);
        });
    }

    /** As others see it: both hands before the chest, from side to side. */
    private static void pose(HumanoidModel<?> model, float raised, float pose) {
        model.rightArm.xRot = Mth.lerp(raised, model.rightArm.xRot, -1.1F);
        model.leftArm.xRot = Mth.lerp(raised, model.leftArm.xRot, -1.1F);
        model.rightArm.yRot = Mth.lerp(raised, model.rightArm.yRot, -0.4F + 0.3F * pose);
        model.leftArm.yRot = Mth.lerp(raised, model.leftArm.yRot, 0.4F + 0.3F * pose);
        model.rightArm.zRot = Mth.lerp(raised, model.rightArm.zRot, 0);
        model.leftArm.zRot = Mth.lerp(raised, model.leftArm.zRot, 0);
    }

    @Override
    protected Vector3f origin() {
        return ORIGIN;
    }

    @Override
    protected void onState(CompoundTag state) {
        ItemStack oldInput = input;
        int oldProgress = progress;
        int oldBatches = batches;
        readState(state);
        if (batches > oldBatches) {
            madeAge = 0;
            madeX = offset * SWAY;
            sound(SoundEvents.ITEM_PICKUP, 0.4F, 1.4F);
        }
        if (progress > oldProgress || batches > oldBatches) {
            painter.fall(STROKE_GRAINS, offset * SWAY, 0, 0, side * SWAY * 4);
            sound(SoundEvents.SAND_BREAK, 0.45F, 0.85F + 0.05F * progress);
        }
        if (batches > oldBatches) {
            shownProgress = 0;
            arrived = 0;
        } else if (!input.isEmpty() && (input.getCount() > oldInput.getCount() || !ItemStack.isSameItemSameTags(input, oldInput))) {
            arrived = oldInput.isEmpty() ? 0 : arrived;
            sound(SoundEvents.SAND_PLACE, 0.4F, 1.3F);
        }
    }

    private void sound(SoundEvent sound, float volume, float pitch) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(sound, volume, pitch);
        }
    }

    private void readState(CompoundTag state) {
        tool = ItemStack.of(state.getCompound("tool"));
        input = ItemStack.of(state.getCompound("input"));
        made = ItemStack.of(state.getCompound("made"));
        progress = state.getInt("progress");
        batches = state.getInt("batches");
        var level = Minecraft.getInstance().level;
        output = level == null ? ItemStack.EMPTY : FlourSieve.result(input, level);
    }

    // ---- The inventory

    /** The slots with something that can be sifted. */
    private List<Integer> usable() {
        List<Integer> slots = new ArrayList<>();
        if (minecraft != null && minecraft.player != null && minecraft.level != null) {
            Inventory inventory = minecraft.player.getInventory();
            for (int slot = 0; slot < inventory.items.size(); slot++) {
                if (!FlourSieve.result(inventory.getItem(slot), minecraft.level).isEmpty()) {
                    slots.add(slot);
                }
            }
            if (!FlourSieve.result(inventory.getItem(Inventory.SLOT_OFFHAND), minecraft.level).isEmpty()) {
                slots.add(Inventory.SLOT_OFFHAND);
            }
        }
        return slots;
    }

    private int slotX(int index, int count) {
        int inRow = Math.min(COLUMNS, count - index / COLUMNS * COLUMNS);
        return width / 2 - inRow * SLOT / 2 + index % COLUMNS * SLOT;
    }

    private int slotY(int index, int count) {
        int rows = (count + COLUMNS - 1) / COLUMNS;
        return height - 8 - (rows - index / COLUMNS) * SLOT;
    }

    private int slotAt(double mouseX, double mouseY) {
        List<Integer> slots = usable();
        for (int i = 0; i < slots.size(); i++) {
            int x = slotX(i, slots.size());
            int y = slotY(i, slots.size());
            if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) {
                return slots.get(i);
            }
        }
        return -1;
    }

    private ItemStack pickedStack() {
        if (picked < 0 || minecraft == null || minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = minecraft.player.getInventory().getItem(picked);
        if (stack.isEmpty() || minecraft.level == null || FlourSieve.result(stack, minecraft.level).isEmpty()) {
            picked = -1;
            return ItemStack.EMPTY;
        }
        return stack;
    }

    // ---- The mouse

    private boolean isOnSieve(Vector3f at) {
        Vector3f from = new Vector3f(at).sub(offset * SWAY, 0, 0);
        float turn = HANDLE * Mth.DEG_TO_RAD;
        Vector3f way = new Vector3f(Mth.sin(turn), 0, Mth.cos(turn));
        return from.length() < ROUND || from.distance(way.mul(Mth.clamp(from.dot(way), 0, HANDLE_LONG))) < NEAR;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button > 1) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (pickedStack().isEmpty() && book.mouseClicked(button)) {
            return true;
        }
        int slot = slotAt(mouseX, mouseY);
        if (slot >= 0) {
            if (hasShiftDown()) {
                sendAction(FlourSieve.PUT + slot);
            } else {
                picked = picked == slot ? -1 : slot;
            }
            return true;
        }
        Vector3f at = pointOn(mouseX, mouseY, 0).orElse(null);
        boolean onSieve = at != null && isOnSieve(at);
        if (!pickedStack().isEmpty()) {
            if (onSieve) {
                boolean one = button == 1;
                sendAction((one ? FlourSieve.PUT_ONE : FlourSieve.PUT) + picked);
                if (!one) {
                    picked = -1;
                }
            } else {
                picked = -1;
            }
            return true;
        }
        if (onSieve && button == 0) {
            held = true;
            dragged = false;
            grabX = at.x;
            grabOffset = offset;
            grabTime = Util.getMillis();
            side = 0;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button != 0 || !held) {
            return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        }
        pointOn(mouseX, mouseY, 0).ifPresent(at -> {
            offset = Mth.clamp(grabOffset + (at.x - grabX) / SWAY, -1, 1);
            dragged |= Math.abs(offset - grabOffset) > CLICK_WAY;
            int end = offset > STROKE_EDGE ? 1 : offset < -STROKE_EDGE ? -1 : 0;
            if (end != 0 && end != side && (side != 0 || Math.abs(offset - grabOffset) > STROKE_EDGE * 0.7F)) {
                side = end;
                sendAction(end > 0 ? FlourSieve.RIGHT : FlourSieve.LEFT);
            }
        });
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && held) {
            held = false;
            if (!dragged && Util.getMillis() - grabTime < CLICK_MS && !input.isEmpty()) {
                sendAction(FlourSieve.TAKE);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return book.mouseScrolled(amount) || super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return book.keyPressed(keyCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return book.charTyped(character) || super.charTyped(character, modifiers);
    }

    // ---- What is seen

    @Override
    protected void renderScene(Scene scene) {
        long now = Util.getMillis();
        float seconds = Math.min(LONGEST_FRAME, (now - previousFrame) / 1000.0F);
        previousFrame = now;
        if (!held) {
            offset *= (float) Math.exp(-seconds / BACK_SECONDS);
        }
        float done = (float) progress / FlourSieve.STROKES;
        shownProgress = done < shownProgress ? done : Math.min(done, shownProgress + 2 * seconds);
        arrived = input.isEmpty() ? 0 : Math.min(1, arrived + 5 * seconds);
        float moved = offset - lastOffset;
        lastOffset = offset;
        int from = FoodColors.batter(input, ItemStack.EMPTY);
        int powder = FoodColors.batter(output.isEmpty() ? made : output, ItemStack.EMPTY);
        if (!output.isEmpty()) {
            trickle += Math.abs(moved) * TRICKLE;
            painter.fall((int) trickle, offset * SWAY, 0, 0, seconds > 0 ? Mth.clamp(moved * SWAY / seconds, -FASTEST, FASTEST) : 0);
            trickle -= (int) trickle;
        }
        var pose = scene.pose();
        pose.pushPose();
        pose.translate(offset * SWAY, 0, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-offset * TILT));
        pose.mulPose(Axis.YP.rotationDegrees(-offset * TURN));
        FlourSievePainter.drawSieve(scene, tool, HANDLE);
        if (!output.isEmpty()) {
            // The thing itself goes to powder, and that through the mesh.
            FlourSievePainter.drawHeap(scene, FastColor.ARGB32.lerp(shownProgress, 0xFF000000 | from, 0xFF000000 | powder),
                    Math.min(1, 3 * shownProgress) * (1 - 0.55F * shownProgress) * arrived, offset);
            FlourSievePainter.drawThing(scene, input, (1 - shownProgress) * arrived, input.getCount(), offset);
        }
        pose.popPose();
        painter.drawFalling(scene, powder, seconds);
        madeAge += seconds;
        if (madeAge < MADE_SECONDS && !made.isEmpty()) {
            FlourSievePainter.drawMade(scene, made, madeX, madeAge / MADE_SECONDS);
        }
        book.render(scene);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int center = width / 2;
        graphics.fillGradient(0, 0, width, 64, 0xBD191613, 0);
        List<Integer> slots = usable();
        String hint = !input.isEmpty() ? "shake" : slots.isEmpty() ? "nothing" : "put";
        graphics.drawCenteredString(font, Component.translatable(LANG + hint), center, 10, 0xFFF5E6);
        if (!output.isEmpty()) {
            graphics.renderItem(input, center - 31, 24);
            graphics.drawCenteredString(font, "→", center, 28, 0xE4C8A1);
            graphics.renderItem(output, center + 15, 24);
            graphics.renderItemDecorations(font, input, center - 31, 24);
            graphics.renderItemDecorations(font, output, center + 15, 24);
            for (int i = 0; i < FlourSieve.STROKES; i++) {
                int start = center - 45 + i * 15;
                graphics.fill(start, 44, start + 12, 47, i < progress ? 0xFFE1B575 : 0x707A6F65);
            }
        }
        if (batches > 0) {
            graphics.drawCenteredString(font, Component.translatable(LANG + "produced", batches * made.getCount(), made.getHoverName()), center, 52,
                    0xFFE1B5);
        }
        renderInventory(graphics, slots, mouseX, mouseY);
        ItemStack carried = pickedStack();
        if (carried.isEmpty() && !held) {
            book.renderTooltip(graphics, mouseX, mouseY);
        }
        if (!carried.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 250);
            graphics.renderItem(carried, mouseX - 8, mouseY - 8);
            graphics.renderItemDecorations(font, carried, mouseX - 8, mouseY - 8);
            graphics.pose().popPose();
        }
    }

    private void renderInventory(GuiGraphics graphics, List<Integer> slots, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < slots.size(); i++) {
            int x = slotX(i, slots.size());
            int y = slotY(i, slots.size());
            ItemStack stack = minecraft.player.getInventory().getItem(slots.get(i));
            graphics.fill(x, y, x + SLOT - 1, y + SLOT - 1, slots.get(i).equals(picked) ? PICKED_COLOR : SLOT_COLOR);
            if (!slots.get(i).equals(picked)) {
                graphics.renderItem(stack, x + 1, y + 1);
                graphics.renderItemDecorations(font, stack, x + 1, y + 1);
                if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) {
                    hovered = stack;
                }
            }
        }
        if (!hovered.isEmpty() && pickedStack().isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }
}
