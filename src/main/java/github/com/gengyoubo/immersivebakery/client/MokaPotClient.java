package github.com.gengyoubo.immersivebakery.client;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.StationState;
import com.renyigesai.bakeries.block.moka_pot.MokaPotBlockEntity;
import com.renyigesai.bakeries.init.BakeriesItems;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.moka.MokaPotStation;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MokaPotClient implements StationBehavior {
    private static final String LANG = "immersive_bakery.moka_pot.";
    private static final int RIGHT_BUTTON = 1;
    private static final float LID_PER_SECOND = 4;
    private static final Map<MokaPotBlockEntity, Lid> LIDS = new WeakHashMap<>();
    /** The opening under the lid, which Bakeries' model has none of. */
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("diegesis", "block/white");
    private static final float OPENING_HALF_WIDTH = 1.4F;
    private static final MeshShape OPENING = MeshShape.builder()
            .corner(OPENING_HALF_WIDTH, 0, OPENING_HALF_WIDTH, 0, 1, 0, 1, 0)
            .corner(OPENING_HALF_WIDTH, 0, -OPENING_HALF_WIDTH, 0, 0, 0, 1, 0)
            .corner(-OPENING_HALF_WIDTH, 0, -OPENING_HALF_WIDTH, 1, 0, 0, 1, 0)
            .corner(-OPENING_HALF_WIDTH, 0, OPENING_HALF_WIDTH, 1, 1, 0, 1, 0)
            .build();
    private final BakingTimer timer = new BakingTimer();

    /** How far the lid is open, 0 to 1, and when that was. */
    private record Lid(float amount, long time) {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DiegesisClient.registerBehavior(MokaPotStation.ID, view -> new MokaPotClient()));
    }

    /** The lid is open while someone is at the pot and it waits for coffee grounds. */
    public static float lidOpen(MokaPotBlockEntity pot) {
        boolean open = !pot.getFill() && pot.isInventoryFull() && DiegesisClient.station(pot.getBlockPos())
                .filter(view -> view.type().id().equals(MokaPotStation.ID) && view.isInUse()).isPresent();
        Lid before = LIDS.get(pot);
        if (!open && before == null) {
            return 0;
        }
        long now = Util.getMillis();
        float passed = before == null ? 0 : (now - before.time()) / 1000.0F * LID_PER_SECOND;
        float amount = Mth.clamp((before == null ? 0 : before.amount()) + (open ? passed : -passed), 0, 1);
        if (!open && amount == 0) {
            LIDS.remove(pot);
        } else {
            LIDS.put(pot, new Lid(amount, now));
        }
        return amount * amount * (3 - 2 * amount);
    }

    @Override
    public void onOpen(StationView view) {
        view.setInventoryFilter(stack -> stack.is(MokaPotStation.COFFEE_GROUNDS));
    }

    @Override
    public void onSceneReady(StationView view) {
        timer.setup(view);
        view.node("opening").ifPresent(node -> {
            node.setShape(OPENING, WHITE);
            node.setTint(0xFF1C1A1A);
        });
        timer.setIcon(view, BakeriesItems.MOKA_POT_FILL.get());
        refresh(view);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        refresh(view);
    }

    /** Under the closed lid nothing of these may show: they do not shake with the brewing pot. */
    @Override
    public void frame(StationView view, float deltaSeconds) {
        boolean open = view.level().getBlockEntity(view.pos()) instanceof MokaPotBlockEntity pot && lidOpen(pot) > 0;
        view.node("opening").ifPresent(node -> node.setVisible(open));
        view.node("grounds").ifPresent(node -> node.setVisible(open));
    }

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        return click(view, node, button);
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click) {
            click(view, click.node(), click.button());
        }
    }

    private static boolean click(StationView view, String node, int button) {
        if (button != RIGHT_BUTTON || !("pot".equals(node) || "room".equals(node)) || !isFilled(view)) {
            return false;
        }
        view.sendAction(MokaPotStation.ACTION_TAKE, new CompoundTag());
        return true;
    }

    private void refresh(StationView view) {
        boolean filled = isFilled(view);
        boolean heated = view.state().getControl(MokaPotStation.HEATED) > 0;
        float progress = view.state().getControl(MokaPotStation.PROGRESS);
        boolean brewing = !filled && view.level().getBlockEntity(view.pos()) instanceof MokaPotBlockEntity pot && !pot.isInventoryFull();
        timer.update(view, filled || brewing || progress > 0, filled ? 0 : 1 - progress);
        view.node("mouth").ifPresent(node -> node.setEnabled(!filled));
        view.node("grounds").ifPresent(node -> node.setEnabled(false));
        view.node("room").ifPresent(node -> node.setEnabled(filled));
        view.node("steam").ifPresent(node -> node.setEmitting(filled));
        view.node("pot").ifPresent(node -> node.setTooltip(filled ? Component.translatable(LANG + "take")
                : !heated ? Component.translatable(LANG + "no_heat")
                : brewing || progress > 0 ? Component.translatable(LANG + "brewing") : Component.translatable(LANG + "insert")));
    }

    private static boolean isFilled(StationView view) {
        return view.state().getControl(MokaPotStation.FILLED) > 0;
    }
}
