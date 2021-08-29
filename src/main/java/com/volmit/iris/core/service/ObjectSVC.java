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
