package com.volmit.iris.engine.object;

import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.nms.container.AutoClosing;
import com.volmit.iris.util.misc.ServerProperties;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;

import java.util.List;

import static com.volmit.iris.Iris.instance;

public class IrisContextInjector implements Listener {
    @Getter
    private static boolean missingDimensionTypes = false;
    private AutoClosing autoClosing = null;
    private final int totalWorlds;
    private int worldCounter = 0;

    public IrisContextInjector() {
        if (!Bukkit.getWorlds().isEmpty()) {
            totalWorlds = 0;
            return;
        }

        String levelName = ServerProperties.LEVEL_NAME;
        List<String> irisWorlds = irisWorlds();
        boolean overworld = irisWorlds.contains(levelName);
        boolean nether = irisWorlds.contains(levelName + "_nether");
        boolean end = irisWorlds.contains(levelName + "_end");

        int i = 1;
        if (Bukkit.getAllowNether()) i++;
        if (Bukkit.getAllowEnd()) i++;

        if (INMS.get().missingDimensionTypes(overworld, nether, end)) {
            missingDimensionTypes = true;
            totalWorlds = 0;
            return;
        }

        if (overworld || nether || end) {
            var pair = INMS.get().injectUncached(overworld, nether, end);
            i += pair.getA() - 3;
            autoClosing = pair.getB();
        }

        totalWorlds = i;
        instance.registerListener(this);
    }

    @EventHandler
    public void on(WorldInitEvent event) {
        if (++worldCounter < totalWorlds) return;
        if (autoClosing != null) {
            autoClosing.close();
            autoClosing = null;
        }
        instance.unregisterListener(this);
    }

    private List<String> irisWorlds() {
        var config = YamlConfiguration.loadConfiguration(ServerProperties.BUKKIT_YML);
        ConfigurationSection section = config.getConfigurationSection("worlds");
        if (section == null) return List.of();

        return section.getKeys(false)
                .stream()
                .filter(k -> section.getString(k + ".generator", "").startsWith("Iris"))
                .toList();
    }
}
