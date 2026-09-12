package art.arcane.iris.world.tree;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

final class RoutedBlockBreakEvent extends BlockBreakEvent implements BlockDropRouter {
    private final TreeFellerSVC service;
    private final FellingRun run;

    RoutedBlockBreakEvent(Block block, Player player, FellingRun run, TreeFellerSVC service) {
        super(block, player);
        this.run = run;
        this.service = service;
    }

    @Override
    public boolean routeDrop(Object drop) {
        return service.isServiceEnabled() && run.presentation.routeDrop(drop);
    }
}
