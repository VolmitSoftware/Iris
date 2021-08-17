package com.volmit.iris.core.decrees;

import com.volmit.iris.engine.object.objects.IrisObject;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.scheduling.Queue;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

@Decree(name = "object", origin = DecreeOrigin.PLAYER, description = "Iris object manipulation")
public class DecObject implements DecreeExecutor {

    @Decree(description = "Check the composition of an object")
    public void analyze(
            @Param(description = "The object to analyze")
            IrisObject object
    ) {
        sender().sendMessage("Object Size: " + object.getW() + " * " + object.getH() + " * " + object.getD() + "");
        sender().sendMessage("Blocks Used: " + NumberFormat.getIntegerInstance().format(object.getBlocks().size()));

        Queue<BlockData> queue = object.getBlocks().enqueueValues();
        Map<Material, Set<BlockData>> unsorted = new HashMap<>();
        Map<BlockData, Integer> amounts = new HashMap<>();
        Map<Material, Integer> materials = new HashMap<>();
        while (queue.hasNext()) {
            BlockData block = queue.next();

            //unsorted.put(block.getMaterial(), block);

            if (!amounts.containsKey(block)) {
                amounts.put(block, 1);


            } else
                amounts.put(block, amounts.get(block) + 1);

            if (!materials.containsKey(block.getMaterial())) {
                materials.put(block.getMaterial(), 1);
                unsorted.put(block.getMaterial(), new HashSet<>());
                unsorted.get(block.getMaterial()).add(block);
            } else {
                materials.put(block.getMaterial(), materials.get(block.getMaterial()) + 1);
                unsorted.get(block.getMaterial()).add(block);
            }

        }

        List<Material> sortedMatsList = amounts.keySet().stream().map(BlockData::getMaterial)
                .sorted().collect(Collectors.toList());
        Set<Material> sortedMats = new TreeSet<>(Comparator.comparingInt(materials::get).reversed());
        sortedMats.addAll(sortedMatsList);
        sender().sendMessage("== Blocks in object ==");

        int n = 0;
        for (Material mat : sortedMats) {
            int amount = materials.get(mat);
            List<BlockData> set = new ArrayList<>(unsorted.get(mat));
            set.sort(Comparator.comparingInt(amounts::get).reversed());
            BlockData data = set.get(0);
            int dataAmount = amounts.get(data);

            String string = " - " + mat.toString() + "*" + amount;
            if (data.getAsString(true).contains("[")) {
                string = string + " --> [" + data.getAsString(true).split("\\[")[1]
                        .replaceAll("true", ChatColor.GREEN + "true" + ChatColor.GRAY)
                        .replaceAll("false", ChatColor.RED + "false" + ChatColor.GRAY) + "*" + dataAmount;
            }

            sender().sendMessage(string);

            n++;

            if (n >= 10) {
                sender().sendMessage("  + " + (sortedMats.size() - n) + " other block types");
                return;
            }
        }
    }
}
