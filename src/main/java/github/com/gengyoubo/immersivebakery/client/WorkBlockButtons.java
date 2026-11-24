package github.com.gengyoubo.immersivebakery.client;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.world.level.block.entity.BlockEntity;

final class WorkBlockButtons {
    private WeakReference<BlockEntity> target = new WeakReference<>(null);
    private final Map<String, Long> pressedAt = new HashMap<>();

    void press(BlockEntity block, String control) {
        if (target.get() != block) {
            target = new WeakReference<>(block);
            pressedAt.clear();
        }
        pressedAt.put(control, Util.getMillis());
    }

    float travel(BlockEntity block, String control) {
        if (target.get() != block) {
            return 0;
        }
        Long time = pressedAt.get(control);
        if (time == null) {
            return 0;
        }
        long elapsed = Util.getMillis() - time;
        return 0.78F * Math.max(0, 1 - Math.max(0, elapsed - 55) / 120.0F);
    }
}
