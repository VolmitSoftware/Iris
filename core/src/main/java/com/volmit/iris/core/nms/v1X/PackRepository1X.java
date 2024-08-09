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

package com.volmit.iris.core.nms.v1X;

import com.volmit.iris.core.nms.container.IPackRepository;

import java.util.Collection;
import java.util.List;

class PackRepository1X implements IPackRepository {
    @Override
    public void reload() {
    }

    @Override
    public void reloadWorldData() {
    }

    @Override
    public void setSelected(Collection<String> packs) {
    }

    @Override
    public boolean addPack(String packId) {
        return false;
    }

    @Override
    public boolean removePack(String packId) {
        return false;
    }

    @Override
    public Collection<String> getAvailableIds() {
        return List.of();
    }

    @Override
    public Collection<String> getSelectedIds() {
        return List.of();
    }

    @Override
    public boolean isAvailable(String packId) {
        return false;
    }
}