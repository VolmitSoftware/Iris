package art.arcane.iris.world.tree;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Objects;

final class RoutedBlockBreakEvent extends BlockBreakEvent implements BlockDropRouter {
    private final TreeFellerSVC service;
    private final FellingRun run;
    private final ItemStack tool;

    RoutedBlockBreakEvent(Options options) {
        super(options.block(), options.player());
        run = options.run();
        service = options.service();
        tool = Objects.requireNonNull(options.tool(), "tool").clone();
    }

    @Override
    public Collection<ItemStack> captureNativeDrops() {
        return getBlock().getDrops(tool);
    }

    @Override
    public boolean routeDrop(Object drop) {
        return service.isServiceEnabled() && run.presentation.routeDrop(drop);
    }

    record Options(Block block, Player player, FellingRun run, TreeFellerSVC service, ItemStack tool) {
    }
}
