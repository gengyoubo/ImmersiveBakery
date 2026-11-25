package github.com.gengyoubo.immersivebakery.client;

import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import github.com.gengyoubo.immersivebakery.storage.Drawers;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The drawers of a cupboard: they slide, what lies in them with them, and the camera goes to the one pulled out last. */
final class DrawerView {
    private static final String LANG = "immersive_bakery.drawers.";
    private static final float HINT_SECONDS = 5;
    private static final float CAPTION_SECONDS = 2.5F;
    private static final String[] TRAYS = {"tray", "tray_lower"};
    /** How far they slide, as the scene's surfaces are written, pixels. */
    private static final float PULL = 11;
    private static final float OPEN_SECONDS = 0.45F;
    private static final float CLOSE_SECONDS = 0.35F;
    private static final float LEAST_TIME = 0.35F;
    /** A little past the stop and back. */
    private static final float RECOIL = 1.1F;
    /** Where the camera goes for each. */
    private static final Vector3f[] FOLLOW = {new Vector3f(0, 1, -4.5F), new Vector3f(0, -5, -4.5F)};
    /** As the scene has them. */
    private static final float ITEM_SIZE = 3.3F;
    private static final float MIDDLE = 8;
    private static final float QUARTER = 90;
    private static final String GLANCE = "side_";

    private final Slide[] slides = {new Slide(), new Slide()};
    private final PlacedItemsView[] items = {items(0), items(1)};
    private final Component pull;
    private boolean looking;
    private int last;
    private int side;
    private boolean settled;
    private boolean hinted;

    DrawerView(String pull) {
        this.pull = Component.translatable(LANG + pull);
    }

    private static PlacedItemsView items(int drawer) {
        CompoundTag which = new CompoundTag();
        which.putInt(Drawers.DRAWER, drawer);
        return new PlacedItemsView(state -> Drawers.items(state, drawer), ITEM_SIZE, 0,
                new PlacedItemsView.Area(3, -10, 13, 1.5F, Drawers.FLOOR[drawer], Drawers.FLOOR[drawer] + Drawers.HEIGHT), which);
    }

    /** Where a cupboard was used last: for the server does not know on which side. */
    private static BlockPos usedAt = BlockPos.ZERO;
    private static Direction usedOn = Direction.UP;

    /** Before anything is used. */
    static void listen() {
        MinecraftForge.EVENT_BUS.addListener(DrawerView::onUse);
    }

    private static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            usedAt = event.getPos().immutable();
            usedOn = event.getHitVec().getDirection();
        }
    }

    /** Whether the player used the station's block from above. */
    static boolean isUsedFromAbove(StationView view) {
        return usedAt.equals(view.pos()) && usedOn == Direction.UP;
    }

    /** The side the player used, or the nearest one: so many sides clockwise from the front. */
    private static int usedSide(StationView view) {
        Direction side = usedOn;
        if (!usedAt.equals(view.pos()) || side.getAxis().isVertical()) {
            Vec3 to = Minecraft.getInstance().player.position().subtract(view.pos().getCenter());
            side = Direction.getNearest(to.x, 0, to.z);
            side = side.getAxis().isVertical() ? view.facing() : side;
        }
        return Math.floorMod(side.get2DDataValue() - view.facing().get2DDataValue(), Drawers.SIDES);
    }

    private static float turn(StationView view) {
        return -QUARTER * Drawers.side(view.state());
    }

    /** Where one is, 0 pushed in to 1 pulled out. */
    private static final class Slide {
        private boolean open;
        private float from;
        private float at;
        private float gone = 1;
        private float seconds = 1;

        private void set(boolean open) {
            this.open = open;
            at = open ? 1 : 0;
            gone = seconds;
        }

        private void start(boolean open) {
            this.open = open;
            from = at;
            gone = 0;
            float way = Math.abs((open ? 1 : 0) - at);
            seconds = (open ? OPEN_SECONDS : CLOSE_SECONDS) * Mth.clamp(way, LEAST_TIME, 1);
        }

        private void advance(float deltaSeconds) {
            if (gone >= seconds) {
                return;
            }
            gone = Math.min(seconds, gone + deltaSeconds);
            float share = gone / seconds;
            float back = share - 1;
            float eased = open ? 1 + (RECOIL + 1) * back * back * back + RECOIL * back * back : share * share;
            at = gone >= seconds ? open ? 1 : 0 : Mth.lerp(eased, from, open ? 1 : 0);
        }

        private boolean showsInside() {
            return open || gone < seconds;
        }
    }

    void sceneReady(StationView view) {
        settled = false;
        view.node("rest").ifPresent(node -> node.setPainter((pose, buffers, light) -> {
            if (view.isBlockHidden()) {
                CupboardPainter.draw(view, Direction.from2DDataValue((view.facing().get2DDataValue() + Drawers.side(view.state())) % Drawers.SIDES), pose, buffers);
            }
        }));
        for (String tray : TRAYS) {
            view.node(tray).ifPresent(node -> node.setOutlineSeenOnly(true));
        }
    }

    void open(StationView view, boolean looking) {
        this.looking = looking;
        hinted = false;
        side = usedSide(view);
        CompoundTag data = new CompoundTag();
        data.putInt(Drawers.ON_SIDE, side);
        view.sendAction(Drawers.ACTION_SIDE, data);
        moveCamera(view);
        if (looking) {
            hint(view);
        }
    }

    boolean isLooking() {
        return looking;
    }

    void look(StationView view, boolean looking) {
        if (this.looking != looking) {
            this.looking = looking;
            moveCamera(view);
        }
    }

    private void hint(StationView view) {
        hinted = isOpen(view, 0) || isOpen(view, 1);
        view.showCaption(hinted ? Component.translatable("immersive_bakery.placed_items.hint") : pull, HINT_SECONDS);
    }

    private void moveCamera(StationView view) {
        Vector3f to = looking && isOpen(view, last) ? new Vector3f(FOLLOW[last]).rotateY(-QUARTER * side * Mth.DEG_TO_RAD) : new Vector3f();
        view.moveCamera(to.x, to.y, to.z);
    }

    /** To the side that was used, where the scene does not look at it already; only once the camera has arrived. */
    private void aim(StationView view) {
        String wanted = GLANCE + side;
        String current = view.currentGlance().orElse("");
        if (looking ? current.equals(wanted) || view.glance(wanted) : current.isEmpty()) {
            return;
        }
        if (!current.isEmpty()) {
            view.glanceBack();
        }
    }

    /** They are shut when nobody is here. */
    private static boolean isOpen(StationView view, int drawer) {
        return view.isInUse() && Drawers.isOpen(view.state(), drawer);
    }

    /** Whether it was a click on a drawer. */
    boolean clicked(StationView view, SceneEvent event) {
        return event instanceof SceneEvent.Click click && click.button() == 0 && pull(view, click.node());
    }

    boolean pull(StationView view, String node) {
        for (int i = 0; i < Drawers.COUNT; i++) {
            if (TRAYS[i].equals(node)) {
                CompoundTag data = new CompoundTag();
                data.putInt(Drawers.DRAWER, i);
                view.sendAction(Drawers.ACTION_DRAWER, data);
                return true;
            }
        }
        return false;
    }

    boolean holdsBlock(StationView view) {
        return isOpen(view, 0) || isOpen(view, 1) || slides[0].showsInside() || slides[1].showsInside();
    }

    void store(StationView view, int slot, ItemStack stack) {
        boolean anyOpen = false;
        // Into the one pulled out last first.
        for (int drawer : new int[] {last, 1 - last}) {
            if (isOpen(view, drawer)) {
                anyOpen = true;
                if (items[drawer].store(view, slot, stack)) {
                    return;
                }
            }
        }
        view.showCaption(anyOpen ? Component.translatable(LANG + "full") : pull, CAPTION_SECONDS);
    }

    void frame(StationView view, float deltaSeconds) {
        if (view.isFocused()) {
            aim(view);
        }
        boolean stands = view.isBlockHidden();
        float turn = turn(view);
        view.node("turn").ifPresent(node -> node.rotateTo(0, turn, 0, 0));
        view.node("front").ifPresent(node -> node.setVisible(stands));
        for (int i = 0; i < Drawers.COUNT; i++) {
            Slide slide = slides[i];
            boolean open = isOpen(view, i);
            if (!settled) {
                slide.set(open);
            } else if (open != slide.open) {
                slide.start(open);
                if (open) {
                    last = i;
                    looking = true;
                }
                if (view.isFocused()) {
                    moveCamera(view);
                    if (open && !hinted) {
                        hint(view);
                    }
                }
            }
            slide.advance(deltaSeconds);
            NodeView tray = view.node(TRAYS[i]).orElse(null);
            if (tray != null) {
                tray.setVisible(stands);
                tray.moveTo(0, 0, -PULL * slide.at, 0);
            }
            view.setSurfaceTransform(i, new Matrix4f().translation(MIDDLE, 0, MIDDLE).rotateY(turn * Mth.DEG_TO_RAD).translate(-MIDDLE, 0, PULL * (1 - slide.at) - MIDDLE));
            view.setSurfaceVisible(i, slide.showsInside());
            items[i].arrange(view, deltaSeconds);
        }
        settled = true;
        boolean anyInside = slides[0].showsInside() || slides[1].showsInside();
        view.setPlacingVisible(anyInside);
        view.setPlacingReachable(anyInside);
    }
}
