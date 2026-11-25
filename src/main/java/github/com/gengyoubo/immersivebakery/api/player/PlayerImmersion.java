package github.com.gengyoubo.immersivebakery.api.player;

import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-side registry and life cycle for scenes attached to one player. */
@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID)
public final class PlayerImmersion {
    private static final Map<ResourceLocation, Function<ServerPlayer, Session>> TYPES = new ConcurrentHashMap<>();
    private static final Map<UUID, Active> ACTIVE = new HashMap<>();

    private PlayerImmersion() {
    }

    /** Register during mod setup. Return null from the factory to reject an open request. */
    public static void register(ResourceLocation id, Function<ServerPlayer, Session> factory) {
        if (TYPES.putIfAbsent(id, factory) != null) {
            throw new IllegalArgumentException("Duplicate player immersion: " + id);
        }
    }

    /** Open a registered scene for a player on the server thread. */
    public static boolean open(ServerPlayer player, ResourceLocation id) {
        Function<ServerPlayer, Session> factory = TYPES.get(id);
        if (factory == null || !canUseScene(player) || ACTIVE.containsKey(player.getUUID())) {
            return false;
        }
        Session session = factory.apply(player);
        if (session == null || !session.isValid(player)) {
            return false;
        }
        Active active = new Active(id, UUID.randomUUID(), player, player.serverLevel(), session);
        ACTIVE.put(player.getUUID(), active);
        active.send(PlayerImmersionNetwork.Phase.OPEN, session.state());
        active.show();
        return true;
    }

    private static boolean canUseScene(ServerPlayer player) {
        return !(player instanceof FakePlayer) && player.isAlive() && !player.isSpectator()
                && !player.isSleeping() && !player.isUsingItem() && player.containerMenu == player.inventoryMenu;
    }

    public static void close(ServerPlayer player) {
        Active active = ACTIVE.get(player.getUUID());
        if (active != null && active.player() == player) {
            close(active, true);
        }
    }

    static void action(ServerPlayer player, UUID token, String action) {
        Active active = ACTIVE.get(player.getUUID());
        if (active == null || active.player() != player || !active.token().equals(token)) {
            return;
        }
        if (!active.isValid()) {
            close(active, true);
        } else if (active.session().onAction(player, action)) {
            active.send(PlayerImmersionNetwork.Phase.UPDATE, active.session().state());
            active.show();
        }
    }

    static void close(ServerPlayer player, UUID token) {
        Active active = ACTIVE.get(player.getUUID());
        if (active != null && active.player() == player && active.token().equals(token)) {
            close(active, false);
        }
    }

    private static void close(Active active, boolean sync) {
        ACTIVE.remove(active.player().getUUID());
        active.session().onClose(active.player());
        PlayerImmersionNetwork.show(active.player(), new PlayerImmersionNetwork.Shown(active.player().getId(), null, 0));
        if (sync) {
            active.send(PlayerImmersionNetwork.Phase.CLOSE, null);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            Active active = ACTIVE.get(player.getUUID());
            if (active != null && active.player() == player && !active.isValid()) {
                close(active, true);
            }
        }
    }

    /** Who comes near sees what is going on. */
    @SubscribeEvent
    public static void track(PlayerEvent.StartTracking event) {
        Active active = ACTIVE.get(event.getTarget().getUUID());
        if (active != null && active.player() == event.getTarget() && event.getEntity() instanceof ServerPlayer watcher) {
            PlayerImmersionNetwork.send(watcher, active.shown());
        }
    }

    /** Before the inventory drops, so that what was taken from it drops with it. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void die(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            close(player);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Active active = ACTIVE.get(player.getUUID());
            if (active != null && active.player() == player) {
                close(active, false);
            }
        }
    }

    @SubscribeEvent
    public static void replacePlayer(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer player) {
            close(player);
        }
    }

    /** A private server session. Validate requests here and change inventory on the server thread. */
    public interface Session {
        boolean isValid(ServerPlayer player);

        CompoundTag state();

        /** Return true after changing state to send a fresh snapshot to the screen. */
        boolean onAction(ServerPlayer player, String action);

        default void onClose(ServerPlayer player) {
        }

        /** Where the hands are, as the pose others see understands it: from -1 to 1. Sent after every action. */
        default float pose() {
            return 0;
        }
    }

    private record Active(ResourceLocation id, UUID token, ServerPlayer player, ServerLevel level, Session session) {
        boolean isValid() {
            return player.serverLevel() == level && canUseScene(player) && session.isValid(player);
        }

        PlayerImmersionNetwork.Shown shown() {
            return new PlayerImmersionNetwork.Shown(player.getId(), id, session.pose());
        }

        void show() {
            PlayerImmersionNetwork.show(player, shown());
        }

        void send(PlayerImmersionNetwork.Phase phase, @Nullable CompoundTag state) {
            PlayerImmersionNetwork.send(player, new PlayerImmersionNetwork.Update(id, token, phase, state));
        }
    }
}
