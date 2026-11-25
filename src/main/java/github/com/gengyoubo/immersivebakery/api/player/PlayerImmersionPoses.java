package github.com.gengyoubo.immersivebakery.api.player;

import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** How players look to others while they work. */
@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, value = Dist.CLIENT)
public final class PlayerImmersionPoses {
    private static final float RAISE_SECONDS = 0.25F;
    private static final float MOVE_SECONDS = 0.18F;
    private static final float LONGEST_FRAME = 0.1F;
    private static final Map<ResourceLocation, Pose> POSES = new HashMap<>();
    private static final Map<Integer, Worker> WORKERS = new HashMap<>();

    private PlayerImmersionPoses() {
    }

    /** Turns the arms of a player at work. */
    @FunctionalInterface
    public interface Pose {
        /**
         * @param raised from 0, as the arms were, to 1
         * @param pose   what the server's session says, -1 to 1, on its way there
         */
        void apply(HumanoidModel<?> model, float raised, float pose);
    }

    private static final class Worker {
        private ResourceLocation id;
        private boolean working;
        private float wanted;
        private float raised;
        private float pose;
        private long lastFrame = Util.getMillis();
    }

    public static void register(ResourceLocation id, Pose pose) {
        if (POSES.putIfAbsent(id, pose) != null) {
            throw new IllegalArgumentException("Duplicate player immersion pose: " + id);
        }
    }

    static void receive(PlayerImmersionNetwork.Shown shown) {
        Worker worker = WORKERS.get(shown.player());
        if (shown.id() != null) {
            if (worker == null) {
                worker = new Worker();
                WORKERS.put(shown.player(), worker);
            }
            worker.id = shown.id();
            worker.working = true;
            worker.wanted = Mth.clamp(shown.pose(), -1, 1);
        } else if (worker != null) {
            worker.working = false;
        }
    }

    /** After a model's arms were posed as usual. */
    public static void apply(HumanoidModel<?> model, @Nullable LivingEntity entity) {
        Worker worker = entity instanceof AbstractClientPlayer ? WORKERS.get(entity.getId()) : null;
        if (worker == null) {
            return;
        }
        long now = Util.getMillis();
        float seconds = Math.min(LONGEST_FRAME, (now - worker.lastFrame) / 1000.0F);
        worker.lastFrame = now;
        worker.raised = Mth.clamp(worker.raised + (worker.working ? seconds : -seconds) / RAISE_SECONDS, 0, 1);
        worker.pose = Mth.clamp(worker.wanted, worker.pose - seconds / MOVE_SECONDS, worker.pose + seconds / MOVE_SECONDS);
        Pose pose = POSES.get(worker.id);
        if (pose != null && worker.raised > 0) {
            pose.apply(model, worker.raised * worker.raised * (3 - 2 * worker.raised), worker.pose);
        }
        if (!worker.working && worker.raised == 0) {
            WORKERS.remove(entity.getId());
        }
    }

    @SubscribeEvent
    public static void leave(ClientPlayerNetworkEvent.LoggingOut event) {
        WORKERS.clear();
    }
}
