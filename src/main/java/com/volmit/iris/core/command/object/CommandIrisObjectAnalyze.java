package com.volmit.iris.core.command.object;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.ProjectManager;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.core.tools.IrisWorlds;
import com.volmit.iris.engine.object.IrisObject;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.Queue;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class CommandIrisObjectAnalyze extends MortarCommand {

    public CommandIrisObjectAnalyze() {
        super("check", "c", "analyze");
        requiresPermission(Iris.perm);
        setCategory("Object");
        setDescription("Check an object's composition");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {
        if ((args.length == 0 || args.length == 1) && sender.isPlayer() && IrisWorlds.isIrisWorld(sender.player().getWorld())) {
            IrisData data = IrisWorlds.access(sender.player().getWorld()).getData();
            if (data == null) {
                sender.sendMessage("Tab complete options only work for objects while in an Iris world.");
            } else if (args.length == 0) {
                list.add(data.getObjectLoader().getPossibleKeys());
            } else {
                list.add(data.getObjectLoader().getPossibleKeys(args[0]));
            }
        }
    }

    @Override
    protected String getArgsUsage() {
        return "[name]";
    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (!IrisSettings.get().isStudio()) {
            sender.sendMessage("To use Iris Studio Objects, please enable studio in Iris/settings.json");
            return true;
        }

        if (!sender.isPlayer()) {
            sender.sendMessage("Only players can spawn objects with this command");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("Please specify the name of of the object want to paste");
            return true;
        }

        Player p = sender.player();

        J.a(() -> {
            IrisObject obj = IrisData.loadAnyObject(args[0]);

            if (obj == null || obj.getLoadFile() == null) {
                sender.sendMessage("Can't find " + args[0] + " in the " + ProjectManager.WORKSPACE_NAME + " folder");
                return;
            }

            sender.sendMessage("Object Size: " + obj.getW() + " * " + obj.getH() + " * " + obj.getD() + "");
            sender.sendMessage("Blocks Used: " + NumberFormat.getIntegerInstance().format(obj.getBlocks().size()));

            Queue<BlockData> queue = obj.getBlocks().enqueueValues();
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
            sender.sendMessage("== Blocks in object ==");

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
                            .replaceAll("false", ChatColor.RED + "false" + ChatColor.GRAY)+ "*" + dataAmount;
                }

                sender.sendMessage(string);

                n++;

                if (n >= 10) {
                    sender.sendMessage("  + " + (sortedMats.size() - n) + " other block types");
                    return;
                }
            }
        });

        return true;
    }


}
