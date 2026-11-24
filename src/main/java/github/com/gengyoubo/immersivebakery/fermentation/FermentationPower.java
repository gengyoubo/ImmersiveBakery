package github.com.gengyoubo.immersivebakery.fermentation;

import com.renyigesai.bakeries.block.fermentation_box.FermentationBoxBlock;
import com.renyigesai.bakeries.block.fermentation_box.FermentationBoxBlockEntity;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import net.minecraft.util.Mth;

public final class FermentationPower {
    private static final String POWER = ImmersiveBakery.MOD_ID + ":fermentation_power";

    private FermentationPower() {
    }

    static void useManualPower(FermentationBoxBlockEntity box) {
        if (box.getPersistentData().contains(POWER)) {
            limitDuration(box);
            return;
        }
        boolean wasRunning = false;
        for (int progress : box.getFermentationTime()) {
            wasRunning |= progress > 0;
        }
        boolean canResume = box.getFermentationMaxTime() >= FermentationStation.MIN_DURATION;
        limitDuration(box);
        setPowered(box, canResume && wasRunning && hasInput(box));
    }

    public static boolean isPowered(FermentationBoxBlockEntity box) {
        return box.getPersistentData().getBoolean(POWER);
    }

    public static boolean canFerment(FermentationBoxBlockEntity box) {
        return !box.getPersistentData().contains(POWER) || isPowered(box);
    }

    static void toggle(FermentationBoxBlockEntity box) {
        if (isPowered(box)) {
            setPowered(box, false);
        } else if (hasInput(box)) {
            limitDuration(box);
            setPowered(box, true);
        }
    }

    public static void stopAfterBatch(FermentationBoxBlockEntity box) {
        if (isPowered(box) && !hasInput(box)) {
            setPowered(box, false);
        }
    }

    // Bakeries' own screen can select durations at which the box never starts.
    private static void limitDuration(FermentationBoxBlockEntity box) {
        int duration = box.getFermentationMaxTime();
        int chosen = duration < FermentationStation.MIN_DURATION ? box.getPerfectTime() : duration;
        int limited = Mth.clamp(chosen, FermentationStation.MIN_DURATION, FermentationStation.MAX_DURATION);
        if (limited != duration) {
            box.setFermentationMaxTime(box, limited);
        }
    }

    private static boolean hasInput(FermentationBoxBlockEntity box) {
        for (int slot = 0; slot < box.getContainerSize(); slot++) {
            if (FermentationStation.canFerment(box.getLevel(), box.getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private static void setPowered(FermentationBoxBlockEntity box, boolean powered) {
        box.getPersistentData().putBoolean(POWER, powered);
        box.setChanged();
        var state = box.getBlockState();
        if (state.getValue(FermentationBoxBlock.POWERED) != powered) {
            // Bakeries refreshes the day's temperature in onPlace, which also runs for block state changes.
            int temperature = box.getTemperature();
            int perfectTime = box.getPerfectTime();
            box.getLevel().setBlockAndUpdate(box.getBlockPos(), state.setValue(FermentationBoxBlock.POWERED, powered));
            box.setTemperature(temperature);
            box.setPerfectTime(perfectTime);
        }
    }
}
