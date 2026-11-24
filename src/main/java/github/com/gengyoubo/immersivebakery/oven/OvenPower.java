package github.com.gengyoubo.immersivebakery.oven;

import com.renyigesai.bakeries.block.oven.OvenBlock;
import com.renyigesai.bakeries.block.oven.OvenBlockEntity;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;

public final class OvenPower {
    private static final String POWER = ImmersiveBakery.MOD_ID + ":oven_power";

    private OvenPower() {
    }

    static void useManualPower(OvenBlockEntity oven) {
        if (!oven.getPersistentData().contains(POWER)) {
            setPowered(oven, oven.getBlockState().getValue(OvenBlock.LIT));
        }
    }

    public static boolean isPowered(OvenBlockEntity oven) {
        return oven.getPersistentData().getBoolean(POWER);
    }

    public static boolean canCook(OvenBlockEntity oven) {
        return !oven.getPersistentData().contains(POWER) || isPowered(oven);
    }

    static void toggle(OvenBlockEntity oven) {
        if (isPowered(oven)) {
            setPowered(oven, false);
        } else if (hasBakeableInput(oven)) {
            setPowered(oven, true);
        }
    }

    public static void stopAfterBatch(OvenBlockEntity oven) {
        if (isPowered(oven) && !oven.getBlockState().getValue(OvenBlock.LIT) && !hasBakeableInput(oven)) {
            setPowered(oven, false);
        }
    }

    private static boolean hasBakeableInput(OvenBlockEntity oven) {
        for (int slot = 0; slot < oven.getContainerSize(); slot++) {
            if (!oven.getItem(slot).isEmpty() && oven.getCurrentRecipe(slot).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static void setPowered(OvenBlockEntity oven, boolean powered) {
        oven.getPersistentData().putBoolean(POWER, powered);
        oven.setChanged();
        if (!powered && oven.getBlockState().getValue(OvenBlock.LIT)) {
            oven.getLevel().setBlockAndUpdate(oven.getBlockPos(), oven.getBlockState().setValue(OvenBlock.LIT, false));
        }
    }
}
