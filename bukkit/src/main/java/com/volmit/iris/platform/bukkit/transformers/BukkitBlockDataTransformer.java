package com.volmit.iris.platform.bukkit.transformers;

import art.arcane.amulet.util.Platform;
import com.volmit.iris.engine.object.NSKey;
import com.volmit.iris.engine.object.block.IrisBlock;
import com.volmit.iris.platform.PlatformDataTransformer;
import com.volmit.iris.platform.PlatformTransformer;
import com.volmit.iris.platform.bukkit.IrisBukkit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class BukkitBlockDataTransformer implements PlatformDataTransformer<BlockData, IrisBlock> {
    @Override
    public Stream<BlockData> getRegistry() {
        return Arrays.stream(Material.values()).parallel().filter(Material::isBlock).map(Material::createBlockData);
    }

    @Override
    public NSKey getKey(BlockData nativeType) {
        return IrisBukkit.getInstance().getNamespaceTransformer().toIris(nativeType.getMaterial().getKey());
    }

    @Override
    public String getTypeName() {
        return "Biome";
    }

    @Override
    public IrisBlock toIris(BlockData blockData) {
        PlatformTransformer<NamespacedKey, NSKey> transformer = IrisBukkit.getInstance().getNamespaceTransformer();

        String s = blockData.getAsString(true);

        if(s.contains("["))
        {
            Map<String, String> properties = new HashMap<>();
            String[] props = new String[]{s.split("\\Q[\\E")[1].split("\\Q]\\E")[0]};
            String[] p;
            if(props[0].contains(","))
            {
                for(String i : props[0].split("\\Q,\\E"))
                {
                    p = i.split("\\Q=\\E");
                    properties.put(p[0], p[1]);
                }
            }

            else {
                p = props[0].split("\\Q=\\E");
                properties.put(p[0], p[1]);
            }

            return new IrisBlock(transformer.toIris(blockData.getMaterial().getKey()), properties);
        }

        else {
            return new IrisBlock(transformer.toIris(blockData.getMaterial().getKey()));
        }
    }

    @Override
    public BlockData toNative(IrisBlock irisBlock) {
        return Bukkit.createBlockData(irisBlock.toString());
    }
}
