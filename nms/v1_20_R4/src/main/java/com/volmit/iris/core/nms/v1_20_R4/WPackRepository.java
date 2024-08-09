/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.core.nms.v1_20_R4;

import com.google.common.collect.ImmutableList;
import com.volmit.iris.core.nms.container.IPackRepository;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.WorldDataConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R4.CraftServer;
import org.bukkit.craftbukkit.v1_20_R4.packs.CraftDataPackManager;

import java.util.Collection;
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
        return getRepository().addPack(packId);
    }

    @Override
    public boolean removePack(String packId) {
        return getRepository().removePack(packId);
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
            repository = ((CraftDataPackManager) Bukkit.getDataPackManager()).getHandle();
        return repository;
    }
}
