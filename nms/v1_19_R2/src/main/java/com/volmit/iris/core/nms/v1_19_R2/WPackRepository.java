package com.volmit.iris.core.nms.v1_19_R2;

import com.google.common.collect.ImmutableList;
import com.volmit.iris.core.nms.container.IPackRepository;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.WorldDataConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_19_R2.CraftServer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("all")
public class WPackRepository implements IPackRepository {
    private PackRepository repository;

    @Override
    public void reload() {
        getRepository().reload();
    }

    @Override
    public void reloadWorldData() {
        var worldData = ((CraftServer) Bukkit.getServer()).getServer().getWorldData();
        var config = new WorldDataConfiguration(getSelectedPacks(), worldData.enabledFeatures());
        worldData.setDataConfiguration(config);
    }

    private DataPackConfig getSelectedPacks() {
        Collection<String> selectedIds = getSelectedIds();
        List<String> enabled = ImmutableList.copyOf(selectedIds);
        List<String> disabled = getAvailableIds().stream()
                .filter((s) ->  !selectedIds.contains(s))
                .toList();
        return new DataPackConfig(enabled, disabled);
    }

    @Override
    public void setSelected(Collection<String> packs) {
        getRepository().setSelected(packs);
    }

    @Override
    public boolean addPack(String packId) {
        var repo = getRepository();
        var packs = new ArrayList<>(repo.getSelectedIds());
        if (repo.isAvailable(packId) && !packs.contains(packId)) {
            packs.add(packId);
            repo.setSelected(packs);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean removePack(String packId) {
        var repo = getRepository();
        var packs = new ArrayList<>(repo.getSelectedIds());
        if (repo.isAvailable(packId) && packs.contains(packId)) {
            packs.remove(packId);
            repo.setSelected(packs);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Collection<String> getAvailableIds() {
        return getRepository().getAvailableIds();
    }

    @Override
    public Collection<String> getSelectedIds() {
        return getRepository().getSelectedIds();
    }

    @Override
    public boolean isAvailable(String packId) {
        return getRepository().isAvailable(packId);
    }

    private PackRepository getRepository() {
        if (repository == null)
            repository = ((CraftServer) Bukkit.getServer()).getHandle().getServer().getPackRepository();
        return repository;
    }
}
