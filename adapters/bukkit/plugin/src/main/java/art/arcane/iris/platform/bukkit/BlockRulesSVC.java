package art.arcane.iris.platform.bukkit;

import art.arcane.iris.generation.block.PlantPlacement;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.event.EventHandler;

/** Lets the server's own rules decide where plants can stand, and asks them again after a resource reload. */
public class BlockRulesSVC implements IrisService {
    private PlantPlacement.ServerRule previousRule;

    @Override
    public void onEnable() {
        previousRule = PlantPlacement.bindServerRule(BukkitPlacementRules::canPlaceOnto);
    }

    @Override
    public void onDisable() {
        PlantPlacement.restoreServerRule(previousRule);
    }

    @EventHandler
    public void onResourcesReloaded(ServerResourcesReloadedEvent event) {
        BukkitPlacementRules.forgetServerRules();
    }
}
