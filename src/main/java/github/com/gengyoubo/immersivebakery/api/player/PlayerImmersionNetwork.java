package github.com.gengyoubo.immersivebakery.api.player;

import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Wire protocol shared by the client and server API. */
public final class PlayerImmersionNetwork {
    private static final int ACTION_LIMIT = 64;
    private static final String VERSION = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, "player_immersion"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private PlayerImmersionNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(Open.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((msg, buf) -> buf.writeResourceLocation(msg.id()))
                .decoder(buf -> new Open(buf.readResourceLocation()))
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    if (player != null) {
                        PlayerImmersion.open(player, msg.id());
                    }
                }).add();
        CHANNEL.messageBuilder(Action.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((msg, buf) -> buf.writeUUID(msg.token()).writeUtf(msg.action(), ACTION_LIMIT))
                .decoder(buf -> new Action(buf.readUUID(), buf.readUtf(ACTION_LIMIT)))
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    if (player != null) {
                        PlayerImmersion.action(player, msg.token(), msg.action());
                    }
                }).add();
        CHANNEL.messageBuilder(Close.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder((msg, buf) -> buf.writeUUID(msg.token()))
                .decoder(buf -> new Close(buf.readUUID()))
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    if (player != null) {
                        PlayerImmersion.close(player, msg.token());
                    }
                }).add();
        CHANNEL.messageBuilder(Update.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> buf.writeResourceLocation(msg.id()).writeUUID(msg.token())
                        .writeEnum(msg.phase()).writeNbt(msg.state()))
                .decoder(buf -> new Update(buf.readResourceLocation(), buf.readUUID(), buf.readEnum(Phase.class), buf.readNbt()))
                .consumerMainThread((msg, ctx) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PlayerImmersionClient.receive(msg)))
                .add();
        CHANNEL.messageBuilder(Shown.class, 4, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> {
                    buf.writeVarInt(msg.player());
                    buf.writeNullable(msg.id(), (to, id) -> to.writeResourceLocation(id));
                    buf.writeFloat(msg.pose());
                })
                .decoder(buf -> new Shown(buf.readVarInt(), buf.readNullable(from -> from.readResourceLocation()), buf.readFloat()))
                .consumerMainThread((msg, ctx) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PlayerImmersionPoses.receive(msg)))
                .add();
    }

    static void requestOpen(ResourceLocation id) {
        CHANNEL.sendToServer(new Open(id));
    }

    static void action(UUID token, String action) {
        CHANNEL.sendToServer(new Action(token, action));
    }

    static void requestClose(UUID token) {
        CHANNEL.sendToServer(new Close(token));
    }

    static void send(ServerPlayer player, Update update) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), update);
    }

    static void send(ServerPlayer watcher, Shown shown) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> watcher), shown);
    }

    /** To everyone who sees the player. */
    static void show(ServerPlayer player, Shown shown) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), shown);
    }

    private record Open(ResourceLocation id) {
    }

    private record Action(UUID token, String action) {
    }

    private record Close(UUID token) {
    }

    enum Phase { OPEN, UPDATE, CLOSE }

    /** What a player is at, for those who see them; nothing when they have stopped. */
    record Shown(int player, @Nullable ResourceLocation id, float pose) {
    }

    record Update(ResourceLocation id, UUID token, Phase phase, @Nullable CompoundTag state) {
    }
}
