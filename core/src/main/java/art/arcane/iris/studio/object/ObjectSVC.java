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

package art.arcane.iris.studio.object;

import art.arcane.iris.integration.ExternalDataSVC;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.generation.block.IrisCustomData;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
import art.arcane.iris.world.task.J;
import lombok.Getter;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.Iterator;
import java.util.Map;

public class ObjectSVC implements IrisService {

    @Getter
    // Concurrent + global-thread pipeline: pastes publish via J.runGlobal while the undo
    // command arrives on an async pool thread; a plain ArrayDeque was mutated from both.
    private final Deque<Map<Block, BlockData>> undos = new ConcurrentLinkedDeque<>();


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
        if (!J.runGlobal(() -> loopChange(amount))) {
            loopChange(amount);
        }
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
        if (blocks == null || blocks.isEmpty()) {
            return;
        }

        J.s(() -> {
            Iterator<Map.Entry<Block, BlockData>> it = blocks.entrySet().iterator();
            int amount = 0;
            while (it.hasNext()) {
                Map.Entry<Block, BlockData> entry = it.next();
                Block block = entry.getKey();
                BlockData data = entry.getValue();
                if (!J.runAt(block.getLocation(), () -> restoreBlock(block, data))) {
                    IrisLogging.warn("Could not schedule object undo at %d, %d, %d.", block.getX(), block.getY(), block.getZ());
                }
                it.remove();

                if (++amount >= 200) {
                    break;
                }
            }

            if (!blocks.isEmpty()) {
                J.s(() -> revert(blocks), 1);
            }
        });
    }

    private static void restoreBlock(Block block, BlockData data) {
        if (data instanceof IrisCustomData custom) {
            ExternalDataSVC external = IrisServices.getOrNull(ExternalDataSVC.class);
            if (external != null && external.placeBlock(block, custom.getCustom())) {
                return;
            }
            block.setBlockData(custom.getBase(), false);
            return;
        }
        block.setBlockData(data, false);
    }
}
