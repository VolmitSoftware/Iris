package com.volmit.iris.platform.bukkit.wrapper;

import com.volmit.iris.platform.PlatformBlock;
import com.volmit.iris.platform.PlatformNamespaceKey;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Data
public class BukkitBlock implements PlatformBlock {
    private final BlockData delegate;
    private final Map<String, String> properties;

    private BukkitBlock(BlockData delegate, Map<String, String> properties) {
        this.delegate = delegate;
        this.properties = properties;
    }

    private BukkitBlock(BlockData delegate) {
        this.delegate = delegate;
        String s = delegate.getAsString(true);

        if(s.contains("[")) {
            Map<String, String> properties = new HashMap<>();
            String[] props = new String[] {s.split("\\Q[\\E")[1].split("\\Q]\\E")[0]};
            String[] p;
            if(props[0].contains(",")) {
                for(String i : props[0].split("\\Q,\\E")) {
                    p = i.split("\\Q=\\E");
                    properties.put(p[0], p[1]);
                }
            } else {
                p = props[0].split("\\Q=\\E");
                properties.put(p[0], p[1]);
            }

            this.properties = Collections.unmodifiableMap(properties);
        } else {
            this.properties = Map.of();
        }
    }

    @Override
    public Map<String, String> getProperties() {
        return null;
    }

    @Override
    public PlatformNamespaceKey getKey() {
        return BukkitKey.of(delegate.getMaterial().getKey());
    }

    public static BukkitBlock of(BlockData blockData) {
        return new BukkitBlock(blockData);
    }

    public static BukkitBlock of(Material material) {
        return of(material.createBlockData());
    }

    public static BukkitBlock of(String raw) {
        return of(Bukkit.createBlockData(raw));
    }
}
