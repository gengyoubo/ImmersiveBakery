package github.com.gengyoubo.immersivebakery.client;

import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.api.client.StationView;
import com.renyigesai.bakeries.block.toaster.ToasterBlock.State;
import github.com.gengyoubo.immersivebakery.toaster.ToasterStation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

final class ToasterTimer {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("diegesis", "block/white");
    private static final int BAR_STEPS = 32;
    private static final float BAR_HALF_WIDTH = 1.3F;
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

    void update(StationView view, State state, boolean empty, int remaining) {
        view.node("timer").ifPresent(node -> node.setVisible(!empty));
        int total = view.state().data().getInt(ToasterStation.TOTAL_TICKS);
        int steps = switch (state) {
            case IDLE -> BAR_STEPS;
            case LIT -> total > 0
                    ? Mth.clamp(Mth.ceil((float) remaining / total * BAR_STEPS), 0, BAR_STEPS)
                    : BAR_STEPS;
            case FINISH -> 0;
        };
        if (steps != drawnSteps) {
            drawnSteps = steps;
            view.node("timer_fill").ifPresent(node -> node.setShape(steps == 0 ? null : bar((float) steps / BAR_STEPS), WHITE, true));
        }
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
