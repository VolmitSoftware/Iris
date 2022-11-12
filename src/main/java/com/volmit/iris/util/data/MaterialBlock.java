/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.data;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

/**
 * Material blocks
 *
 * @author cyberpwn
 */
@SuppressWarnings("deprecation")
public class MaterialBlock {
    private Material material;
    private Byte data;

    /**
     * Create a materialblock
     *
     * @param material the material
     * @param data     the data
     */
    public MaterialBlock(Material material, Byte data) {
        this.material = material;
        this.data = data;
    }

    public MaterialBlock(Material material) {
        this.material = material;
        data = 0;
    }

    public MaterialBlock(Location location) {
        this(location.getBlock());
    }

    public MaterialBlock(BlockState state) {
        material = state.getType();
        data = state.getData().getData();
    }

    public MaterialBlock(Block block) {
        material = block.getType();
        data = block.getData();
    }

    public MaterialBlock() {
        material = Material.AIR;
        data = 0;
    }

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public Byte getData() {
        return data;
    }

    public void setData(Byte data) {
        this.data = data;
    }

    @Override
    public String toString() {
        if (getData() == 0) {
            return getMaterial().toString();
        }

        return getMaterial().toString() + ":" + getData();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((data == null) ? 0 : data.hashCode());
        result = prime * result + ((material == null) ? 0 : material.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        MaterialBlock other = (MaterialBlock) obj;
        if (data == null) {
            if (other.data != null) {
                return false;
            }
        } else if (!data.equals(other.data)) {
            return false;
        }
        return material == other.material;
    }
}
