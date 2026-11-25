package github.com.gengyoubo.immersivebakery.cup;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.renyigesai.bakeries.block.glass_drink_cup.GlassDrinkCupBlock;
import com.renyigesai.bakeries.block.glass_drink_cup.GlassDrinkCupBlockEntity;
import com.renyigesai.bakeries.init.BakeriesSounds;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class CupLogic implements StationLogic {
    /** No hand pours out a cup, and no straw falls, faster than that; and how long the finished drink is looked at, ticks. */
    private static final int POUR_LEAST = 20;
    private static final int FALL_LEAST = 6;
    private static final int LOOKED_AT = 14;

    @Nullable
    private final GlassDrinkCupBlockEntity cup;
    private long stageSince;

    CupLogic(StationContext station) {
        cup = station.blockEntity() instanceof GlassDrinkCupBlockEntity blockEntity ? blockEntity : null;
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (cup == null || cup.isRemoved() || station.blockEntity() != cup || !player.mayBuild() || !player.level().mayInteract(player, station.pos())) {
            return;
        }
        switch (CupStation.stage(station.state())) {
            case CupStation.MIXING -> {
                switch (action) {
                    case CupStation.ACTION_ADD -> addCarried(station, player);
                    case CupStation.ACTION_TAKE -> take(station, player);
                    case CupStation.ACTION_FILL -> fill(station, player, ResourceLocation.tryParse(data.getString(CupStation.RECIPE)));
                    case CupStation.ACTION_SHAKEN -> {
                        if (CupContents.made(station.level(), CupContents.in(cup)) != null) {
                            enter(station, CupStation.SHAKEN);
                        }
                    }
                    default -> { }
                }
            }
            case CupStation.SHAKEN -> {
                if (CupStation.ACTION_POURED.equals(action) && inStage(station) >= POUR_LEAST) {
                    enter(station, CupStation.POURED);
                }
            }
            case CupStation.POURED -> {
                if (CupStation.ACTION_DONE.equals(action) && inStage(station) >= FALL_LEAST) {
                    enter(station, CupStation.DONE);
                    station.level().playSound(null, station.pos(), BakeriesSounds.INSERT_STRAW.get(), SoundSource.BLOCKS, 1, 1);
                }
            }
            default -> { }
        }
    }

    private void enter(StationContext station, int stage) {
        station.state().setControl(CupStation.STAGE, stage);
        stageSince = station.level().getGameTime();
    }

    private long inStage(StationContext station) {
        return station.level().getGameTime() - stageSince;
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        enter(station, CupStation.MIXING);
    }

    @Override
    public void tick(StationContext station) {
        if (CupStation.stage(station.state()) == CupStation.DONE && inStage(station) >= LOOKED_AT) {
            finish(station);
        }
    }

    /** Poured out, it is a drink, whoever waits for its straw. */
    @Override
    public void onClose(StationContext station, Player player) {
        if (CupStation.stage(station.state()) >= CupStation.POURED) {
            finish(station);
        }
    }

    private void addCarried(StationContext station, Player player) {
        ItemStack carried = player.inventoryMenu.getCarried();
        if (!CupContents.accepts(station.level(), CupContents.in(cup), carried)) {
            return;
        }
        ItemStack one = carried.copyWithCount(1);
        if (!player.getAbilities().instabuild) {
            player.inventoryMenu.setCarried(carried.copyWithCount(carried.getCount() - 1));
        }
        add(station, player, one);
    }

    private void add(StationContext station, Player player, ItemStack one) {
        List<ItemStack> in = CupContents.in(cup);
        in.add(one);
        CupContents.set(cup, in);
        if (CupContents.isPoured(one) && !player.getAbilities().instabuild) {
            give(player, one.getCraftingRemainingItem());
        }
        if (station.blockState().getBlock() instanceof GlassDrinkCupBlock block) {
            station.level().playSound(null, station.pos(), block.getSound(one), SoundSource.BLOCKS, 1, 1);
        }
        CompoundTag data = new CompoundTag();
        data.put(CupStation.ITEM, one.save(new CompoundTag()));
        station.effect(CupStation.EFFECT_ADDED, "cup", data);
    }

    private void take(StationContext station, Player player) {
        List<ItemStack> in = CupContents.in(cup);
        for (int i = in.size() - 1; i >= 0; i--) {
            if (!CupContents.isPoured(in.get(i))) {
                ItemStack taken = in.remove(i).copy();
                CupContents.set(cup, in);
                give(player, taken);
                station.level().playSound(null, station.pos(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.2F);
                return;
            }
        }
    }

    private void fill(StationContext station, Player player, @Nullable ResourceLocation id) {
        var inventory = player.getInventory();
        CupContents.recipes(station.level()).stream().filter(recipe -> recipe.getId().equals(id)).findFirst().ifPresent(recipe -> {
            List<Integer> slots = CupContents.missingFrom(recipe, CupContents.in(cup), inventory);
            if (slots == null) {
                return;
            }
            for (int slot : slots) {
                ItemStack one = inventory.getItem(slot).copyWithCount(1);
                if (!player.getAbilities().instabuild) {
                    inventory.removeItem(slot, 1);
                }
                add(station, player, one);
            }
            inventory.setChanged();
        });
    }

    /** As when Bakeries' cup is clicked with an empty hand: the drink stands in its place. */
    private void finish(StationContext station) {
        station.state().setControl(CupStation.STAGE, CupStation.MIXING);
        if (cup == null || cup.isRemoved() || station.blockEntity() != cup) {
            return;
        }
        var made = CupContents.made(station.level(), CupContents.in(cup));
        if (made == null || !(station.blockState().getBlock() instanceof GlassDrinkCupBlock block)) {
            return;
        }
        Set<Player> users = Set.copyOf(station.users());
        block.spawnOrSetBlock(made.getResultItem(station.level().registryAccess()).copy(), station.level(), station.pos());
        for (Player user : users) {
            if (user instanceof ServerPlayer serverPlayer) {
                Diegesis.close(serverPlayer);
            }
        }
    }

    private static void give(Player player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
