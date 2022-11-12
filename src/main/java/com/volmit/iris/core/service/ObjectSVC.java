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

package com.volmit.iris.core.service;

import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.scheduling.J;
import lombok.Getter;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;

public class ObjectSVC implements IrisService {

    @Getter
    private final Deque<Map<Block, BlockData>> undos = new ArrayDeque<>();


    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {

    }

    public void addChanges(Map<Block, BlockData> oldBlocks) {
        undos.add(oldBlocks);
    }

    public void revertChanges(int amount) {
        loopChange(amount);
    }

    private void loopChange(int amount) {
        if (undos.size() > 0) {
            revert(undos.pollLast());
            if (amount > 1) {
                J.s(() -> loopChange(amount - 1), 2);
            }
        }
    }

    /**
     * Reverts all the block changes provided, 200 blocks per tick
     *
     * @param blocks The blocks to remove
     */
    private void revert(Map<Block, BlockData> blocks) {
        int amount = 0;
        Iterator<Map.Entry<Block, BlockData>> it = blocks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Block, BlockData> entry = it.next();
            BlockData data = entry.getValue();
            entry.getKey().setBlockData(data, false);
            it.remove();

            amount++;

            if (amount > 200) {
                J.s(() -> revert(blocks), 1);
            }
        }
    }
}
