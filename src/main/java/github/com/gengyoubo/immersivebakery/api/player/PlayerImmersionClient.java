package github.com.gengyoubo.immersivebakery.api.player;

import com.pockyl.diegesis.api.client.DiegesisClient;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client registry and entry point for player-attached scenes. */
@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, value = Dist.CLIENT)
public final class PlayerImmersionClient {
    private static final Map<ResourceLocation, Function<CompoundTag, PlayerImmersionScreen>> SCREENS = new HashMap<>();

    private static final float LOOK_SECONDS = 0.35F;
    private static final float LONGEST_FRAME = 0.1F;
    private static final Set<ResourceLocation> HIDDEN = Stream.of(VanillaGuiOverlay.HOTBAR, VanillaGuiOverlay.CROSSHAIR, VanillaGuiOverlay.ITEM_NAME,
            VanillaGuiOverlay.PLAYER_HEALTH, VanillaGuiOverlay.ARMOR_LEVEL, VanillaGuiOverlay.FOOD_LEVEL, VanillaGuiOverlay.EXPERIENCE_BAR,
            VanillaGuiOverlay.JUMP_BAR, VanillaGuiOverlay.MOUNT_HEALTH).map(VanillaGuiOverlay::id).collect(Collectors.toUnmodifiableSet());
    private static float looking;
    private static float pitch;
    private static long lastFrame;
    /** As it was before a scene was shown, which is in first person. */
    @Nullable
    private static CameraType cameraBefore;

    private PlayerImmersionClient() {
    }

    public static void register(ResourceLocation id, Function<CompoundTag, PlayerImmersionScreen> factory) {
        if (SCREENS.putIfAbsent(id, factory) != null) {
            throw new IllegalArgumentException("Duplicate player immersion screen: " + id);
        }
    }

    /** Request a scene. The server may refuse it if the player no longer meets its requirements. */
    public static boolean requestOpen(ResourceLocation id) {
        if (canShow(Minecraft.getInstance()) && SCREENS.containsKey(id)) {
            PlayerImmersionNetwork.requestOpen(id);
            return true;
        }
        return false;
    }

    private static boolean canShow(Minecraft minecraft) {
        return minecraft.player != null && minecraft.getConnection() != null && minecraft.screen == null
                && !DiegesisClient.isFocused();
    }

    static void receive(PlayerImmersionNetwork.Update update) {
        Minecraft minecraft = Minecraft.getInstance();
        if (update.phase() == PlayerImmersionNetwork.Phase.OPEN) {
            Function<CompoundTag, PlayerImmersionScreen> factory = SCREENS.get(update.id());
            if (factory == null || update.state() == null || !canShow(minecraft)) {
                if (minecraft.getConnection() != null) {
                    PlayerImmersionNetwork.requestClose(update.token());
                }
                return;
            }
            PlayerImmersionScreen screen = factory.apply(update.state());
            screen.attach(update.token());
            minecraft.setScreen(screen);
            if (cameraBefore == null) {
                cameraBefore = minecraft.options.getCameraType();
            }
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        } else if (minecraft.screen instanceof PlayerImmersionScreen screen && screen.hasToken(update.token())) {
            if (update.phase() == PlayerImmersionNetwork.Phase.CLOSE) {
                screen.detach();
                minecraft.setScreen(null);
            } else if (update.state() != null) {
                screen.onState(update.state());
            }
        }
    }

    /** Looks down at the work, and up again when it is done. Only the camera: the player keeps looking where they did. */
    @SubscribeEvent
    public static void turnCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = Util.getMillis();
        float seconds = Math.min(LONGEST_FRAME, (now - lastFrame) / 1000.0F);
        lastFrame = now;
        PlayerImmersionScreen screen = minecraft.screen instanceof PlayerImmersionScreen open ? open : null;
        if (screen == null && cameraBefore != null) {
            minecraft.options.setCameraType(cameraBefore);
            cameraBefore = null;
        }
        if (screen != null) {
            pitch = screen.viewPitch();
        }
        looking = Mth.clamp(looking + (screen != null ? seconds : -seconds) / LOOK_SECONDS, 0, 1);
        if (looking > 0 && minecraft.getCameraEntity() == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
            event.setPitch(Mth.lerp(looking * looking * (3 - 2 * looking), event.getPitch(), pitch));
        }
    }

    /** In place of the hands: the scene, drawn once for both. */
    @SubscribeEvent
    public static void renderHands(RenderHandEvent event) {
        if (Minecraft.getInstance().screen instanceof PlayerImmersionScreen screen) {
            event.setCanceled(true);
            if (event.getHand() == InteractionHand.MAIN_HAND) {
                screen.renderHands(event, looking * looking * (3 - 2 * looking));
            }
        }
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiOverlayEvent.Pre event) {
        ResourceLocation overlay = event.getOverlay().id();
        if (Minecraft.getInstance().screen instanceof PlayerImmersionScreen && HIDDEN.contains(overlay)) {
            event.setCanceled(true);
        }
    }
}
