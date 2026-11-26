package art.arcane.iris.platform.bukkit;

import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.event.EventHandler;

/** Lets the server's placement rules apply again after a resource reload, which may have changed the block tags they read. */
public class BlockRulesSVC implements IrisService {
    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    @EventHandler
    public void onResourcesReloaded(ServerResourcesReloadedEvent event) {
        if (INMS.isBound()) {
            INMS.get().forgetBlockRules();
        }
    }
}
