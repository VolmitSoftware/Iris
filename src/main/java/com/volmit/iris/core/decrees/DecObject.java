package com.volmit.iris.core.decrees;

import com.volmit.iris.core.service.WandSVC;
import com.volmit.iris.engine.object.objects.IrisObject;
import com.volmit.iris.engine.object.objects.IrisObjectPlacement;
import com.volmit.iris.engine.object.objects.IrisObjectPlacementScaleInterpolator;
import com.volmit.iris.engine.object.objects.IrisObjectRotation;
import com.volmit.iris.util.data.Cuboid;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.math.Direction;
import com.volmit.iris.util.scheduling.Queue;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

@Decree(name = "object", origin = DecreeOrigin.PLAYER, studio = true, description = "Iris object manipulation")
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

    @Decree(description = "Get a powder that reveals objects", studio = true)
    public void dust() {
        player().getInventory().addItem(WandSVC.createDust());
        sender().playSound(Sound.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS, 1f, 1.5f);
    }

    @Decree(description = "Contract a selection based on your looking direction", aliases = "-")
    public void contract(
            @Param(description = "The amount to inset by", defaultValue = "1")
            int amount
    ){
        if (!WandSVC.isHoldingWand(player())) {
            sender().sendMessage("Hold your wand.");
            return;
        }


        Location[] b = WandSVC.getCuboid(player().getInventory().getItemInMainHand());
        Location a1 = b[0].clone();
        Location a2 = b[1].clone();
        Cuboid cursor = new Cuboid(a1, a2);
        Direction d = Direction.closest(player().getLocation().getDirection()).reverse();
        assert d != null;
        cursor = cursor.expand(d, -amount);
        b[0] = cursor.getLowerNE();
        b[1] = cursor.getUpperSW();
        player().getInventory().setItemInMainHand(WandSVC.createWand(b[0], b[1]));
        player().updateInventory();
        sender().playSound(Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1f, 0.55f);
    }

    @Decree(description = "Set point 1 to look", aliases = "p1")
    public void position1(
            @Param(description = "Whether to use your current position, or where you look", defaultValue = "true")
            boolean here
    ){
        if (!WandSVC.isHoldingWand(player())) {
            sender().sendMessage("Ready your Wand.");
            return;
        }

        ItemStack wand = player().getInventory().getItemInMainHand();

        if (WandSVC.isWand(wand)) {
            Location[] g = WandSVC.getCuboid(wand);

            if (!here) {
                // TODO: WARNING HEIGHT
                g[1] = player().getTargetBlock(null, 256).getLocation().clone();
            } else {
                g[1] = player().getLocation().getBlock().getLocation().clone().add(0, -1, 0);
            }
            player().setItemInHand(WandSVC.createWand(g[0], g[1]));
        }
    }

    @Decree(description = "Set point 2 to look", aliases = "p2")
    public void position2(
            @Param(description = "Whether to use your current position, or where you look", defaultValue = "true")
                    boolean here
    ){
        if (!WandSVC.isHoldingWand(player())) {
            sender().sendMessage("Ready your Wand.");
            return;
        }

        ItemStack wand = player().getInventory().getItemInMainHand();

        if (WandSVC.isWand(wand)) {
            Location[] g = WandSVC.getCuboid(wand);

            if (!here) {
                // TODO: WARNING HEIGHT
                g[0] = player().getTargetBlock(null, 256).getLocation().clone();
            } else {
                g[0] = player().getLocation().getBlock().getLocation().clone().add(0, -1, 0);
            }
            player().setItemInHand(WandSVC.createWand(g[0], g[1]));
        }
    }

    @Decree(description = "Paste an object")
    public void paste(
            @Param(description = "The object to paste")
            IrisObject object,
            @Param(description = "Whether or not to edit the object (need to hold wand)", defaultValue = "false")
            boolean edit,
            @Param(description = "The amount of degrees to rotate by", defaultValue = "0")
            int rotate,
            @Param(description = "The factor by which to scale the object placement", defaultValue = "1")
            double scale,
            @Param(description = "The scale interpolator to use", defaultValue = "none")
            IrisObjectPlacementScaleInterpolator interpolator
    ){
        double maxScale = getMaxScale(object);
        if (scale < maxScale){
            sender().sendMessage(C.YELLOW + "Indicated scale exceeds maximum. Downscaled to maximum: " + maxScale);
            scale = maxScale;
        }

        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setRotation(IrisObjectRotation.of(0, rotate, 0));
        sender().playSound(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.5f);

    }

    private double getMaxScale(IrisObject object){
        return Double.max(10 - object.getBlocks().size() / 10000d, 1);
    }
}
