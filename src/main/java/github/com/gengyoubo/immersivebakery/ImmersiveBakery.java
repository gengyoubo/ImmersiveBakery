package github.com.gengyoubo.immersivebakery;

import github.com.gengyoubo.immersivebakery.oven.OvenStation;
import github.com.gengyoubo.immersivebakery.toaster.ToasterStation;
import net.minecraftforge.fml.common.Mod;

@Mod(ImmersiveBakery.MOD_ID)
public final class ImmersiveBakery {
    public static final String MOD_ID = "immersive_bakery";

    public ImmersiveBakery() {
        ToasterStation.register();
        OvenStation.register();
    }
}
