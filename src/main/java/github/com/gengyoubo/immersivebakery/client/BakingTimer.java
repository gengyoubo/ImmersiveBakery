package github.com.gengyoubo.immersivebakery.client;

import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.scene.Visual;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import org.joml.Vector3f;

final class BakingTimer {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("diegesis", "block/white");
    private static final int BAR_STEPS = 32;
    private static final float BAR_HALF_WIDTH = 1.3F;
    private static final Vector3f ICON_MIDDLE = new Vector3f(8, 8, 8);
    private int drawnSteps = -1;

    void setup(StationView view) {
        drawnSteps = -1;
        view.node("timer").ifPresent(node -> {
            node.setShape(rectangle(1.65F, -1.65F, -2.4F, 2.4F), WHITE, true);
            node.setTint(0xA02B201A);
            node.setOnTop(true);
        });
        view.node("timer_track").ifPresent(node -> {
            node.setShape(bar(1), WHITE, true);
            node.setTint(0xFF6E5140);
            node.setOnTop(true);
        });
        view.node("timer_fill").ifPresent(node -> {
            node.setTint(0xFFFFBE67);
            node.setOnTop(true);
        });
        view.node("timer_icon").ifPresent(node -> node.setOnTop(true));
    }

    void update(StationView view, boolean visible, float remainingFraction) {
        view.node("timer").ifPresent(node -> node.setVisible(visible));
        int steps = Mth.clamp(Mth.ceil(remainingFraction * BAR_STEPS), 0, BAR_STEPS);
        if (steps != drawnSteps) {
            drawnSteps = steps;
            view.node("timer_fill").ifPresent(node -> node.setShape(steps == 0 ? null : bar((float) steps / BAR_STEPS), WHITE, true));
        }
    }

    /** Shows what is being made instead of the clock. */
    void setIcon(StationView view, Item item) {
        view.node("timer_icon").ifPresent(node -> node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE)));
    }

    private static MeshShape bar(float fraction) {
        return rectangle(BAR_HALF_WIDTH, BAR_HALF_WIDTH * (1 - 2 * fraction), -1.88F, -1.63F);
    }

    private static MeshShape rectangle(float left, float right, float bottom, float top) {
        return MeshShape.builder()
                .corner(right, 0, top, 0, 1, 0, 1, 0)
                .corner(left, 0, top, 1, 1, 0, 1, 0)
                .corner(left, 0, bottom, 1, 0, 0, 1, 0)
                .corner(right, 0, bottom, 0, 0, 0, 1, 0)
                .build();
    }
}
