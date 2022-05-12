package com.volmit.iris.core.link;

import com.volmit.iris.util.collection.KList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.MissingResourceException;

@RequiredArgsConstructor
public abstract class BlockDataProvider {

    @Getter
    private final String pluginId, identifierPrefix;

    public Plugin getPlugin() {
        return Bukkit.getPluginManager().getPlugin(pluginId);
    }

    public boolean isPresent() {
        return getPlugin() != null;
    }

    public abstract BlockData getBlockData(String blockId) throws MissingResourceException;

    public String[] getBlockIdentifiers() {
        KList<String> names = new KList<>(getBlockTypes());
        names.rewrite(s -> identifierPrefix + ":" + s);
        return names.toArray(new String[0]);
    }

    public abstract String[] getBlockTypes();
}
