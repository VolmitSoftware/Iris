package art.arcane.iris.core.service;

import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.util.common.plugin.IrisService;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.event.EventHandler;

public class BlockRulesSVC implements IrisService {
    private static final String RELOAD_EVENT_CLASS = "io.papermc.paper.event.server.ServerResourcesReloadedEvent";

    public static boolean isSupported() {
        try {
            Class.forName(RELOAD_EVENT_CLASS, false, BlockRulesSVC.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return false;
        }
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    @EventHandler
    public void onResourcesReloaded(ServerResourcesReloadedEvent event) {
        INMS.get().forgetBlockRules();
    }
}
