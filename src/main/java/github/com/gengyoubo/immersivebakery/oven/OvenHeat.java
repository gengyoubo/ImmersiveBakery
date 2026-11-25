package github.com.gengyoubo.immersivebakery.oven;

/** What the oven keeps to itself; implemented by the block entity mixin. */
public interface OvenHeat {
    /** The temperature over which what lies in a slot burns, 0 for nothing there. */
    int immersiveBakery$mostTemperature(int slot);
}
