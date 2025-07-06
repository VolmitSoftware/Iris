package com.volmit.iris.core.link;

import com.volmit.iris.core.service.ExternalDataSVC;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.data.IrisCustomData;
import me.kryniowesegryderiusz.kgenerators.Main;
import me.kryniowesegryderiusz.kgenerators.api.KGeneratorsAPI;
import me.kryniowesegryderiusz.kgenerators.generators.locations.objects.GeneratorLocation;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.MissingResourceException;

public class KGeneratorsDataProvider extends ExternalDataProvider {
    public KGeneratorsDataProvider() {
        super("KGenerators");
    }

    @Override
    public void init() {

    }

    @Override
    public @NotNull BlockData getBlockData(@NotNull Identifier blockId, @NotNull KMap<String, String> state) throws MissingResourceException {
        if (Main.getGenerators().get(blockId.key()) == null) throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
        return new IrisCustomData(Material.STRUCTURE_VOID.createBlockData(), ExternalDataSVC.buildState(blockId, state));
    }

    @Override
    public @NotNull ItemStack getItemStack(@NotNull Identifier itemId, @NotNull KMap<String, Object> customNbt) throws MissingResourceException {
        var gen = Main.getGenerators().get(itemId.key());
        if (gen == null) throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
        return gen.getGeneratorItem();
    }

    @Override
    public void processUpdate(@NotNull Engine engine, @NotNull Block block, @NotNull Identifier blockId) {
        if (block.getType() != Material.STRUCTURE_VOID) return;
        var existing = KGeneratorsAPI.getLoadedGeneratorLocation(block.getLocation());
        if (existing != null) return;
        block.setBlockData(B.getAir(), false);
        var gen = Main.getGenerators().get(blockId.key());
        if (gen == null) return;
        var loc = new GeneratorLocation(-1, gen, block.getLocation(), Main.getPlacedGenerators().getChunkInfo(block.getChunk()), null, null);
        Main.getDatabases().getDb().saveGenerator(loc);
        Main.getPlacedGenerators().addLoaded(loc);
        Main.getSchedules().schedule(loc, true);
    }

    @Override
    public @NotNull Identifier[] getBlockTypes() {
        return Main.getGenerators().getAll().stream()
                .map(gen -> new Identifier("kgenerators", gen.getId()))
                .filter(i -> {
                    try {
                        return getBlockData(i) != null;
                    } catch (MissingResourceException e) {
                        return false;
                    }
                })
                .toArray(Identifier[]::new);
    }

    @Override
    public @NotNull Identifier[] getItemTypes() {
        return Main.getGenerators().getAll().stream()
                .map(gen -> new Identifier("kgenerators", gen.getId()))
                .filter(i -> {
                    try {
                        return getItemStack(i) != null;
                    } catch (MissingResourceException e) {
                        return false;
                    }
                })
                .toArray(Identifier[]::new);
    }

    @Override
    public boolean isValidProvider(@NotNull Identifier id, boolean isItem) {
        return "kgenerators".equalsIgnoreCase(id.namespace());
    }
}
