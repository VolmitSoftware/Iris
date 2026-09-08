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

package art.arcane.iris.core.commands;

import art.arcane.iris.Iris;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.core.link.WorldEditLink;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.pack.PackDirectoryResolver;
import art.arcane.iris.core.runtime.ObjectStudioActivation;
import art.arcane.iris.core.runtime.StudioOpenCoordinator;
import art.arcane.iris.core.runtime.WorldRuntimeControlService;
import art.arcane.iris.core.service.ObjectSVC;
import art.arcane.iris.core.service.StudioSVC;
import art.arcane.iris.core.service.WandSVC;
import art.arcane.iris.core.tools.IrisConverter;
import art.arcane.iris.core.tools.TreePlausibilizeBatch;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.IObjectPlacer;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.object.IrisObjectPlacement;
import art.arcane.iris.engine.object.IrisObjectPlacementScaleInterpolator;
import art.arcane.iris.engine.object.IrisObjectRotation;
import art.arcane.iris.engine.object.IrisObjectScale;
import art.arcane.iris.engine.object.TileData;
import art.arcane.iris.platform.bukkit.BukkitBlockState;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.data.Cuboid;
import art.arcane.iris.util.common.data.IrisCustomData;
import art.arcane.iris.util.common.director.DirectorExecutor;
import art.arcane.iris.util.common.director.specialhandlers.NullableDimensionHandler;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.iris.util.common.director.specialhandlers.ObjectHandler;
import art.arcane.iris.util.common.director.specialhandlers.ObjectScaleHandler;
import art.arcane.iris.util.common.director.specialhandlers.ObjectTargetHandler;
import art.arcane.iris.util.common.format.C;
import art.arcane.iris.util.common.math.Direction;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.core.localization.BukkitCommandMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.iris.core.localization.BukkitCommandMessagesExtended;
import art.arcane.iris.core.localization.RuntimeUiMessages;
@Director(name = "object", aliases = "o", origin = DirectorOrigin.PLAYER, description = "Iris object manipulation", descriptionKey = "iris.director.commandobject.director.iris_object_manipulation")
public class CommandObject implements DirectorExecutor {
    static final Set<Material> PASTE_TRANSPARENT_BLOCKS = Set.of(
            Material.AIR,
            Material.CAVE_AIR,
            Material.VOID_AIR,
            Material.SHORT_GRASS,
            Material.SNOW,
            Material.VINE,
            Material.TORCH,
            Material.DEAD_BUSH,
            Material.POPPY,
            Material.DANDELION
    );

    static boolean isPasteTarget(Material material) {
        return !PASTE_TRANSPARENT_BLOCKS.contains(material);
    }

    @Director(description = "Open an object studio world (grid of every object; dimension optional, defaults to all packs)", descriptionKey = "iris.director.commandobject.director.open_object_studio_world_grid_every_object_dimension_optional_defaults_all_packs", sync = true)
    public void studio(
            @Param(defaultValue = "null", description = "Optional dimension whose object pack to lay out; omit to aggregate objects from every pack", descriptionKey = "iris.director.commandobject.param.optional_dimension_whose_object_pack_lay_out_omit_aggregate_objects_from_every", aliases = "dim", customHandler = NullableDimensionHandler.class)
            IrisDimension dimension,
            @Param(defaultValue = "1337", description = "The seed to generate the studio with", descriptionKey = "iris.director.commandobject.param.seed_generate_studio_with", aliases = "s")
            long seed
    ) {
        VolmitSender commandSender = sender();
        Map<String, IrisData> sources = new LinkedHashMap<>();
        IrisDimension hostDimension = dimension;

        if (dimension != null) {
            IrisData data = dimension.getLoader();
            if (data == null) {
                data = IrisData.get(dimension.getLoadFile().getParentFile().getParentFile());
            }
            sources.put(data.getDataFolder().getName(), data);
        } else {
            File workspace = Iris.service(StudioSVC.class).getWorkspaceFolder();
            for (File pack : PackDirectoryResolver.listVisiblePackDirectories(workspace)) {
                File dimensionsDir = new File(pack, "dimensions");
                if (!dimensionsDir.isDirectory()) continue;
                IrisData data = IrisData.get(pack);
                String[] keys = data.getObjectLoader().getPossibleKeys();
                if (keys == null || keys.length == 0) continue;
                sources.put(pack.getName(), data);
                if (hostDimension == null) {
                    File[] dimFiles = dimensionsDir.listFiles((f) -> f.isFile() && f.getName().endsWith(".json"));
                    if (dimFiles != null && dimFiles.length > 0) {
                        String loadKey = dimFiles[0].getName().replaceFirst("\\.json$", "");
                        IrisDimension loaded = data.getDimensionLoader().load(loadKey);
                        if (loaded != null) {
                            hostDimension = loaded;
                        }
                    }
                }
            }
        }

        if (hostDimension == null || sources.isEmpty()) {
            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_OBJECT_NO_PACKS_WITH_OBJECTS_WERE_FOUND_ON_THIS_SERVER));
            return;
        }

        int totalObjects = 0;
        for (IrisData d : sources.values()) {
            String[] k = d.getObjectLoader().getPossibleKeys();
            if (k != null) totalObjects += k.length;
        }
        if (totalObjects == 0) {
            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_OBJECT_NO_OBJECTS_PLACE_ACROSS_SELECTED_PACK_S));
            return;
        }

        // ObjectStudioActivation carries the buffet state; mutating the shared cached
        // IrisDimension here leaked OBJECT_BUFFET into later pack exports of this dimension.
        ObjectStudioActivation.activate(hostDimension.getLoadKey());
        ObjectStudioActivation.setSources(hostDimension.getLoadKey(), sources);

        String scope = dimension == null
                ? ("all packs [" + sources.size() + "]")
                : ("\"" + hostDimension.getName() + "\"");
        commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_OBJECT_OPENING_OBJECT_STUDIO_OBJECTS, MessageArgument.untrusted("scope", scope), MessageArgument.untrusted("totalObjects", totalObjects)));

        IrisDimension finalHost = hostDimension;
        try {
            Iris.service(StudioSVC.class).open(
                    commandSender,
                    seed,
                    hostDimension.getLoadKey(),
                    StudioOpenCoordinator.StudioOpenKind.OBJECT,
                    world -> {
                        if (world == null) return;
                        try {
                            WorldRuntimeControlService.get().applyObjectStudioWorldRules(world);
                        } catch (Throwable e) {
                            Iris.reportError("Failed to apply object studio world rules for " + world.getName(), e);
                        }

                        if (commandSender.isPlayer()) {
                            Player p = commandSender.player();
                            if (p != null) {
                                Location target = new Location(world, 0.5D, 66D, 0.5D);
                                J.runEntity(p, () -> {
                                    BukkitPlatform.teleportAsync(p, target).thenRun(() -> p.setGameMode(GameMode.CREATIVE));
                                });
                            }
                        }
                    });
        } catch (Throwable e) {
            Iris.reportError("Failed to open object studio world \"" + finalHost.getLoadKey() + "\".", e);
            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_OBJECT_FAILED_OPEN_OBJECT_STUDIO, MessageArgument.untrusted("value", String.valueOf(e.getMessage()))));
        }
    }

    public static IObjectPlacer createPlacer(World world, Map<Block, BlockData> futureBlockChanges, Engine targetEngine) {
        return new IObjectPlacer() {
            @Override
            public int getHighest(int x, int z, IrisData data) {
                return world.getHighestBlockYAt(x, z);
            }

            @Override
            public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
                return world.getHighestBlockYAt(x, z, ignoreFluid ? HeightMap.OCEAN_FLOOR : HeightMap.MOTION_BLOCKING);
            }

            @Override
            public void set(int x, int y, int z, PlatformBlockState s) {
                BlockData d = (BlockData) s.nativeHandle();
                Block block = world.getBlockAt(x, y, z);

                //Prevent blocks being set in or bellow bedrock
                if (y <= world.getMinHeight() || block.getType() == Material.BEDROCK) return;

                futureBlockChanges.putIfAbsent(block, block.getBlockData());

                if (d instanceof IrisCustomData data) {
                    block.setBlockData(data.getBase(), false);
                    Iris.warn("Tried to place custom block at " + x + ", " + y + ", " + z + " which is not supported!");
                } else block.setBlockData(d, false);
            }

            @Override
            public PlatformBlockState get(int x, int y, int z) {
                return BukkitBlockState.of(world.getBlockAt(x, y, z).getBlockData());
            }

            @Override
            public boolean isPreventingDecay() {
                return false;
            }

            @Override
            public boolean isCarved(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean isSolid(int x, int y, int z) {
                return world.getBlockAt(x, y, z).getType().isSolid();
            }

            @Override
            public boolean isUnderwater(int x, int z) {
                return false;
            }

            @Override
            public int getFluidHeight() {
                return targetEngine == null
                        ? 63
                        : targetEngine.getMinHeight() + targetEngine.getDimension().getFluidHeight();
            }

            @Override
            public boolean isDebugSmartBore() {
                return false;
            }

            @Override
            public void setTile(int xx, int yy, int zz, TileData tile) {
                tile.toBukkitTry(world.getBlockAt(xx, yy, zz));
            }

            @Override
            public <T> void setData(int xx, int yy, int zz, T data) {

            }

            @Override
            public <T> T getData(int xx, int yy, int zz, Class<T> t) {
                return null;
            }

            @Override
            public Engine getEngine() {
                return targetEngine;
            }
        };
    }

    @Director(description = "Check the composition of an object", descriptionKey = "iris.director.commandobject.director.check_composition_object")
    public void analyze(
            @Param(description = "The object to analyze", descriptionKey = "iris.director.commandobject.param.object_analyze", customHandler = ObjectHandler.class)
            String object
    ) {
        IrisObject o = IrisData.loadAnyObject(object, data());
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_OBJECT_SIZE, MessageArgument.untrusted("value", o.getW()), MessageArgument.untrusted("value2", o.getH()), MessageArgument.untrusted("value3", o.getD())));
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_BLOCKS_USED, MessageArgument.untrusted("value", NumberFormat.getIntegerInstance().format(o.getBlocks().size()))));

        var queue = o.getBlocks().values();
        Map<Material, Set<BlockData>> unsorted = new HashMap<>();
        Map<BlockData, Integer> amounts = new HashMap<>();
        Map<Material, Integer> materials = new HashMap<>();
        while (queue.hasNext()) {
            BlockData block = (BlockData) queue.next().nativeHandle();

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
                .sorted().toList();
        Set<Material> sortedMats = new TreeSet<>(Comparator.comparingInt(materials::get).reversed());
        sortedMats.addAll(sortedMatsList);
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_BLOCKS_OBJECT));

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
                sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_OTHER_BLOCK_TYPES, MessageArgument.untrusted("value", (sortedMats.size() - n))));
                return;
            }
        }
    }

    @Director(description = "Shrink an object to its minimum size", descriptionKey = "iris.director.commandobject.director.shrink_object_its_minimum_size")
    public void shrink(@Param(description = "The object to shrink", descriptionKey = "iris.director.commandobject.param.object_shrink", customHandler = ObjectHandler.class) String object) {
        IrisObject o = IrisData.loadAnyObject(object, data());
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_CURRENT_OBJECT_SIZE, MessageArgument.untrusted("value", o.getW()), MessageArgument.untrusted("value2", o.getH()), MessageArgument.untrusted("value3", o.getD())));
        o.shrinkwrap();
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_NEW_OBJECT_SIZE, MessageArgument.untrusted("value", o.getW()), MessageArgument.untrusted("value2", o.getH()), MessageArgument.untrusted("value3", o.getD())));
        try {
            o.write(o.getLoadFile());
        } catch (IOException e) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_FAILED_SAVE_OBJECT, MessageArgument.untrusted("value", o.getLoadFile()), MessageArgument.untrusted("value2", String.valueOf(e.getMessage()))));
            Iris.reportError("Failed to save object " + o.getLoadFile() + ".", e);
        }
    }

    @Director(description = "Grow organic branches through the canopy so every leaf survives vanilla decay", descriptionKey = "iris.director.commandobject.director.grow_organic_branches_through_canopy_so_every_leaf_survives_vanilla_decay",
            origin = DirectorOrigin.BOTH)
    public void plausibilize(
            @Param(description = "Object key, prefix (trees/), or filesystem path", descriptionKey = "iris.director.commandobject.param.object_key_prefix_trees_filesystem_path",
                    customHandler = ObjectTargetHandler.class)
            String target,
            @Param(name = "dryrun", description = "dryrun=true analyzes only, writes nothing", descriptionKey = "iris.director.commandobject.param.dryrun_true_analyzes_only_writes_nothing", defaultValue = "false")
            boolean dryRun,
            @Param(name = "reach", description = "reach=N max branch length in blocks from existing wood; farther leaf clusters are pinned persistent instead. reach=0 grows unlimited", descriptionKey = "iris.director.commandobject.param.reach_n_max_branch_length_blocks_from_existing_wood_farther_leaf_clusters", defaultValue = "12")
            int reach
    ) {
        IrisData nearest = data();
        List<TreePlausibilizeBatch.Target> targets = TreePlausibilizeBatch.resolve(target, nearest);
        if (targets.isEmpty() && nearest == null) {
            targets = resolveFromPacks(target);
        }
        if (targets.isEmpty()) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_NO_OBJECTS_MATCHED, MessageArgument.untrusted("target", target)));
            return;
        }

        sender().sendMessage(IrisLanguage.text(
                BukkitCommandMessagesExtended.COMMAND_OBJECT_PLAUSIBILIZE_REACH_QUEUED_OBJECT_S,
                MessageArgument.trusted("reach", reach),
                MessageArgument.trusted("value", dryRun ? IrisLanguage.text(RuntimeUiMessages.TREE_DRY_SUFFIX) : ""),
                MessageArgument.trusted("value2", targets.size())
        ));

        VolmitSender s = sender();
        List<TreePlausibilizeBatch.Target> queued = targets;
        J.a(() -> TreePlausibilizeBatch.run(queued, dryRun, reach, nearest, (TreePlausibilizeBatch.Output output) ->
                s.sendMessage(output.headline()
                        ? C.IRIS + output.text()
                        : C.GRAY + "  " + output.text())));
    }

    private static List<TreePlausibilizeBatch.Target> resolveFromPacks(String target) {
        List<TreePlausibilizeBatch.Target> out = new ArrayList<>();
        File packsFolder = Iris.instance.getDataFolder("packs");
        for (File pack : PackDirectoryResolver.listVisiblePackDirectories(packsFolder)) {
            File objectsRoot = new File(pack, "objects");
            if (!objectsRoot.isDirectory()) {
                continue;
            }
            File candidate = new File(objectsRoot, target + ".iob");
            if (candidate.isFile()) {
                out.add(new TreePlausibilizeBatch.Target(pack.getName() + "/" + target, candidate));
                continue;
            }
            File candidateDir = new File(objectsRoot, target);
            if (candidateDir.isDirectory()) {
                TreePlausibilizeBatch.walkIob(candidateDir, objectsRoot, out);
            }
        }
        return out;
    }

    @Director(description = "Convert .schem files in the 'convert' folder to .iob files.", descriptionKey = "iris.director.commandobject.director.convert_schem_files_convert_folder_iob_files")
    public void convert () {
        try {
            IrisConverter.convertSchematics(sender());
        } catch (Exception e) {
            Iris.reportError("Failed to convert schematics to Iris objects.", e);
        }

    }

    @Director(description = "Get a powder that reveals objects", descriptionKey = "iris.director.commandobject.director.get_powder_that_reveals_objects", aliases = "d")
    public void dust() {
        VolmitSender commandSender = sender();
        Player player = player();

        onPlayerThread(player, () -> {
            player.getInventory().addItem(WandSVC.createDust());
            commandSender.playSound(Sound.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS, 1f, 1.5f);
        });
    }

    @Director(description = "Contract a selection based on your looking direction", descriptionKey = "iris.director.commandobject.director.contract_selection_based_on_your_looking_direction", aliases = "-")
    public void contract(
            @Param(description = "The amount to inset by", descriptionKey = "iris.director.commandobject.param.amount_inset_by", defaultValue = "1")
            int amount
    ) {
        VolmitSender commandSender = sender();
        Player player = player();

        onPlayerThread(player, () -> {
            if (!WandSVC.isHoldingWand(player)) {
                commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_HOLD_YOUR_WAND));
                return;
            }


            Location[] b = WandSVC.getCuboid(player);
            if (b == null || b[0] == null || b[1] == null) {
                commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_NO_AREA_SELECTED));
                return;
            }
            Location a1 = b[0].clone();
            Location a2 = b[1].clone();
            Cuboid cursor = new Cuboid(a1, a2);
            Direction d = Direction.closest(player.getLocation().getDirection()).reverse();
            assert d != null;
            cursor = cursor.expand(d.f(), -amount);
            b[0] = cursor.getLowerNE();
            b[1] = cursor.getUpperSW();
            player.getInventory().setItemInMainHand(WandSVC.createWand(b[0], b[1]));
            player.updateInventory();
            commandSender.playSound(Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1f, 0.55f);
        });
    }

    @Director(description = "Set point 1 to look", descriptionKey = "iris.director.commandobject.director.set_point_1_look", aliases = "p1")
    public void position1(
            @Param(description = "Whether to use your current position, or where you look", descriptionKey = "iris.director.commandobject.param.whether_use_your_current_position_where_you_look", defaultValue = "true")
            boolean here
    ) {
        VolmitSender commandSender = sender();
        Player player = player();

        onPlayerThread(player, () -> {
            if (!WandSVC.isHoldingWand(player)) {
                commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_READY_YOUR_WAND));
                return;
            }

            if (WandSVC.isHoldingWand(player)) {
                Location[] g = WandSVC.getCuboid(player);

                if (g == null) {
                    return;
                }
                if (!here) {
                    // TODO: WARNING HEIGHT
                    g[1] = player.getTargetBlock(null, 256).getLocation().clone();
                } else {
                    g[1] = player.getLocation().getBlock().getLocation().clone().add(0, -1, 0);
                }
                player.getInventory().setItemInMainHand(WandSVC.createWand(g[0], g[1]));
            }
        });
    }

    @Director(description = "Set point 2 to look", descriptionKey = "iris.director.commandobject.director.set_point_2_look", aliases = "p2")
    public void position2(
            @Param(description = "Whether to use your current position, or where you look", descriptionKey = "iris.director.commandobject.param.whether_use_your_current_position_where_you_look_2", defaultValue = "true")
            boolean here
    ) {
        VolmitSender commandSender = sender();
        Player player = player();

        onPlayerThread(player, () -> {
            if (!WandSVC.isHoldingWand(player)) {
                commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_READY_YOUR_WAND_2));
                return;
            }

            Location[] g = WandSVC.getCuboid(player);

            if (g == null) {
                return;
            }

            if (!here) {
                // TODO: WARNING HEIGHT
                g[0] = player.getTargetBlock(null, 256).getLocation().clone();
            } else {
                g[0] = player.getLocation().getBlock().getLocation().clone().add(0, -1, 0);
            }
            player.getInventory().setItemInMainHand(WandSVC.createWand(g[0], g[1]));
        });
    }

    @Director(description = "Paste an object", descriptionKey = "iris.director.commandobject.director.paste_object", sync = true)
    public void paste(
            @Param(description = "The object to paste", descriptionKey = "iris.director.commandobject.param.object_paste", customHandler = ObjectHandler.class)
            String object,
            @Param(description = "Whether or not to edit the object (need to hold wand)", descriptionKey = "iris.director.commandobject.param.whether_not_edit_object_need_hold_wand", defaultValue = "false")
            boolean edit,
            @Param(description = "The amount of degrees to rotate by", descriptionKey = "iris.director.commandobject.param.amount_degrees_rotate_by", defaultValue = "0")
            int rotate,
            @Param(description = "Explicit scale factor, or dimension to inherit the current Iris world's factor", descriptionKey = "iris.director.commandobject.param.factor_by_which_scale_object_placement", defaultValue = "dimension", customHandler = ObjectScaleHandler.class)
            Double scale
//            ,
//            @Param(description = "The scale interpolator to use", descriptionKey = "iris.director.commandobject.param.scale_interpolator_use", defaultValue = "none")
//            IrisObjectPlacementScaleInterpolator interpolator
    ) {
        IrisObject o = IrisData.loadAnyObject(object, data());
        Engine placementEngine = engine();
        double factor = scale == null
                ? placementEngine == null ? 1D : placementEngine.getDimension().getAllObjectScaleFactor()
                : IrisObjectScale.requireValidFactor(scale, "scale");
        double maxScale = Double.max(10 - o.getBlocks().size() / 10000d, 1);
        if (factor > maxScale) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_INDICATED_SCALE_EXCEEDS_MAXIMUM_DOWNSCALED_MAXIMUM, MessageArgument.untrusted("maxScale", maxScale)));
            factor = maxScale;
        }

        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setRotation(IrisObjectRotation.of(0, rotate, 0));

        VolmitSender commandSender = sender();
        Player player = player();
        ItemStack wand = player.getInventory().getItemInMainHand();
        Block targetBlock = player.getTargetBlock(PASTE_TRANSPARENT_BLOCKS, 256);
        if (!isPasteTarget(targetBlock.getType())) {
            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_WHAT_PLEASE_LOOK_AT_ANY_BLOCK_NOT_AT_SKY));
            return;
        }
        Location block = targetBlock.getLocation().clone().add(0, 1, 0);

        commandSender.playSound(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.5f);

        Map<Block, BlockData> futureChanges = new HashMap<>();

        if (factor != 1D) {
            o = scale == null ? IrisObjectScale.getFixed(o, factor)
                    : new IrisObjectScale().setSize(factor)
                    .setInterpolation(IrisObjectPlacementScaleInterpolator.TRICUBIC).get(new RNG(), o);
        }

        // Block writes must run on the thread owning the target chunk; the undo log stays global.
        final IrisObject placed = o;
        if (!J.runAt(block, () -> {
            placed.place(block.getBlockX(), block.getBlockY() + (int) placed.getCenter().getY(), block.getBlockZ(), createPlacer(block.getWorld(), futureChanges, null), placement, new RNG(), placed.getLoader());
            J.runGlobal(() -> Iris.service(ObjectSVC.class).addChanges(futureChanges));

            if (!edit) {
                commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_PLACED, MessageArgument.untrusted("object", object)));
                return;
            }

            onPlayerThread(player, () -> {
                ObjectPasteBounds bounds = ObjectPasteBounds.resolve(placed, placement.getRotation(), block.getBlockX(),
                        block.getBlockY() + placed.getCenter().getBlockY(), block.getBlockZ());
                Location minimum = new Location(block.getWorld(), bounds.minX(), bounds.minY(), bounds.minZ());
                Location maximum = new Location(block.getWorld(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
                ItemStack newWand = WandSVC.createWand(maximum, minimum);
                if (WandSVC.isWand(wand)) {
                    player.getInventory().setItemInMainHand(newWand);
                    commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_UPDATED_WAND_OBJECTS_IOB, MessageArgument.untrusted("value", placed.getLoadKey())));
                } else {
                    int slot = WandSVC.findWand(player.getInventory());
                    if (slot == -1) {
                        player.getInventory().addItem(newWand);
                        commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_GIVEN_NEW_WAND_OBJECTS_IOB, MessageArgument.untrusted("value", placed.getLoadKey())));
                    } else {
                        player.getInventory().setItem(slot, newWand);
                        commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_UPDATED_WAND_OBJECTS_IOB_2, MessageArgument.untrusted("value", placed.getLoadKey())));
                    }
                }
            });
        })) {
            Iris.warn("Could not schedule the object paste at " + block.getBlockX() + ", " + block.getBlockY() + ", " + block.getBlockZ() + ".");
        }
    }

    /**
     * Runs the body on the thread owning the player, reporting when the hop cannot be scheduled.
     */
    private void onPlayerThread(Player player, Runnable body) {
        if (player == null) {
            return;
        }

        if (!J.runEntity(player, body)) {
            Iris.warn("Could not schedule /iris object on the thread owning " + player.getName() + ".");
        }
    }

    @Director(description = "Save an object", descriptionKey = "iris.director.commandobject.director.save_object")
    public void save(
            @Param(description = "The dimension to store the object in", descriptionKey = "iris.director.commandobject.param.dimension_store_object", contextual = true, contextualOverride = true)
            IrisDimension dimension,
            @Param(description = "The file to store it in, can use / for subfolders", descriptionKey = "iris.director.commandobject.param.file_store_it_can_use_subfolders")
            String name,
            @Param(description = "Overwrite existing object files", descriptionKey = "iris.director.commandobject.param.overwrite_existing_object_files", defaultValue = "false", aliases = "force")
            boolean overwrite,
            @Param(description = "Use legacy TileState serialization if possible", descriptionKey = "iris.director.commandobject.param.use_legacy_tilestate_serialization_if_possible", defaultValue = "true")
            boolean legacy
    ) {
        IrisObject o = WandSVC.createSchematic(player(), legacy);

        if (o == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_YOU_NEED_HOLD_YOUR_WAND));
            return;
        }

        File file = Iris.service(StudioSVC.class).getWorkspaceFile(dimension.getLoadKey(), "objects", name + ".iob");

        if (file.exists() && !overwrite) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_FILE_ALREADY_EXISTS_SET_OVERWRITE_TRUE_OVERWRITE_IT));
            return;
        }
        try {
            o.write(file, sender());
        } catch (IOException e) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_FAILED_SAVE_OBJECT_BECAUSE_IOEXCEPTION, MessageArgument.untrusted("value", String.valueOf(e.getMessage()))));
            Iris.reportError(e);
            return;
        }

        sender().playSound(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.5f);
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_SUCCESSFULLY_OBJECT_SAVED_OBJECTS, MessageArgument.untrusted("value", dimension.getLoadKey()), MessageArgument.untrusted("name", name)));
    }

    @Director(description = "Shift a selection in your looking direction", descriptionKey = "iris.director.commandobject.director.shift_selection_your_looking_direction")
    public void shift(
            @Param(description = "The amount to shift by", descriptionKey = "iris.director.commandobject.param.amount_shift_by", defaultValue = "1")
            int amount
    ) {
        VolmitSender commandSender = sender();
        Player player = player();

        onPlayerThread(player, () -> {
            if (!WandSVC.isHoldingWand(player)) {
                commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_HOLD_YOUR_WAND_2));
                return;
            }

            Location[] b = WandSVC.getCuboid(player);
            if (b == null || b[0] == null || b[1] == null) {
                commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_NO_AREA_SELECTED_2));
                return;
            }
            Location a1 = b[0].clone();
            Location a2 = b[1].clone();
            Direction d = Direction.closest(player.getLocation().getDirection()).reverse();
            if (d == null) {
                return; // HOW DID THIS HAPPEN
            }
            a1.add(d.toVector().multiply(amount));
            a2.add(d.toVector().multiply(amount));
            Cuboid cursor = new Cuboid(a1, a2);
            b[0] = cursor.getLowerNE();
            b[1] = cursor.getUpperSW();
            player.getInventory().setItemInMainHand(WandSVC.createWand(b[0], b[1]));
            player.updateInventory();
            commandSender.playSound(Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1f, 0.55f);
        });
    }

    @Director(description = "Undo a number of pastes", descriptionKey = "iris.director.commandobject.director.undo_number_pastes", aliases = "u")
    public void undo(
            @Param(description = "The amount of pastes to undo", descriptionKey = "iris.director.commandobject.param.amount_pastes_undo", defaultValue = "1")
            int amount
    ) {
        ObjectSVC service = Iris.service(ObjectSVC.class);
        int actualReverts = Math.min(service.getUndos().size(), amount);
        service.revertChanges(actualReverts);
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_REVERTED_PASTES, MessageArgument.untrusted("actualReverts", actualReverts)));
    }

    @Director(description = "Gets an object wand and grabs the current WorldEdit selection.", descriptionKey = "iris.director.commandobject.director.gets_object_wand_grabs_current_worldedit_selection", aliases = "we", origin = DirectorOrigin.PLAYER)
    public void we() {
        VolmitSender commandSender = sender();
        Player player = player();

        if (!Bukkit.getPluginManager().isPluginEnabled("WorldEdit")) {
            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_YOU_CAN_T_GET_WORLDEDIT_SELECTION_WITHOUT_WORLDEDIT_YOU_KNOW));
            return;
        }

        Cuboid locs = WorldEditLink.getSelection(player);

        if (locs == null) {
            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_YOU_DON_T_HAVE_WORLDEDIT_SELECTION_THIS_WORLD));
            return;
        }

        onPlayerThread(player, () -> {
            player.getInventory().addItem(WandSVC.createWand(locs.getLowerNE(), locs.getUpperSW()));
            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_FRESH_WAND_WITH_YOUR_CURRENT_WORLDEDIT_SELECTION_ON_IT));
        });
    }

    @Director(description = "Get an object wand", descriptionKey = "iris.director.commandobject.director.get_object_wand", sync = true)
    public void wand() {
        player().getInventory().addItem(WandSVC.createWand());
        sender().playSound(Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 1.5f);
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_POOF_GOOD_LUCK_BUILDING));
    }

    @Director(name = "x&y", description = "Autoselect up, down & out", descriptionKey = "iris.director.commandobject.director.autoselect_up_down_out", sync = true)
    public void xay() {
        if (!WandSVC.isHoldingWand(player())) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_HOLD_YOUR_WAND_3));
            return;
        }

        Location[] b = WandSVC.getCuboid(player());
        if (b == null || b[0] == null || b[1] == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_NO_AREA_SELECTED_3));
            return;
        }
        Location a1 = b[0].clone();
        Location a2 = b[1].clone();
        Location a1x = b[0].clone();
        Location a2x = b[1].clone();
        Cuboid cursor = new Cuboid(a1, a2);
        Cuboid cursorx = new Cuboid(a1, a2);

        while (!cursor.containsOnly(Material.AIR)) {
            a1.add(new org.bukkit.util.Vector(0, 1, 0));
            a2.add(new org.bukkit.util.Vector(0, 1, 0));
            cursor = new Cuboid(a1, a2);
        }

        a1.add(new org.bukkit.util.Vector(0, -1, 0));
        a2.add(new org.bukkit.util.Vector(0, -1, 0));

        while (!cursorx.containsOnly(Material.AIR)) {
            a1x.add(new org.bukkit.util.Vector(0, -1, 0));
            a2x.add(new org.bukkit.util.Vector(0, -1, 0));
            cursorx = new Cuboid(a1x, a2x);
        }

        a1x.add(new org.bukkit.util.Vector(0, 1, 0));
        a2x.add(new Vector(0, 1, 0));
        b[0] = a1;
        b[1] = a2x;
        cursor = new Cuboid(b[0], b[1]);
        cursor = cursor.contract(Cuboid.CuboidDirection.North);
        cursor = cursor.contract(Cuboid.CuboidDirection.South);
        cursor = cursor.contract(Cuboid.CuboidDirection.East);
        cursor = cursor.contract(Cuboid.CuboidDirection.West);
        b[0] = cursor.getLowerNE();
        b[1] = cursor.getUpperSW();
        player().getInventory().setItemInMainHand(WandSVC.createWand(b[0], b[1]));
        player().updateInventory();
        sender().playSound(Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1f, 0.55f);
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_AUTO_SELECT_COMPLETE));
    }

    @Director(name = "x+y", description = "Autoselect up & out", descriptionKey = "iris.director.commandobject.director.autoselect_up_out", sync = true)
    public void xpy() {
        if (!WandSVC.isHoldingWand(player())) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_HOLD_YOUR_WAND_4));
            return;
        }

        Location[] b = WandSVC.getCuboid(player());
        if (b == null || b[0] == null || b[1] == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_NO_AREA_SELECTED_4));
            return;
        }
        b[0].add(new Vector(0, 1, 0));
        b[1].add(new Vector(0, 1, 0));
        Location a1 = b[0].clone();
        Location a2 = b[1].clone();
        Cuboid cursor = new Cuboid(a1, a2);

        while (!cursor.containsOnly(Material.AIR)) {
            a1.add(new Vector(0, 1, 0));
            a2.add(new Vector(0, 1, 0));
            cursor = new Cuboid(a1, a2);
        }

        a1.add(new Vector(0, -1, 0));
        a2.add(new Vector(0, -1, 0));
        b[0] = a1;
        a2 = b[1];
        cursor = new Cuboid(a1, a2);
        cursor = cursor.contract(Cuboid.CuboidDirection.North);
        cursor = cursor.contract(Cuboid.CuboidDirection.South);
        cursor = cursor.contract(Cuboid.CuboidDirection.East);
        cursor = cursor.contract(Cuboid.CuboidDirection.West);
        b[0] = cursor.getLowerNE();
        b[1] = cursor.getUpperSW();
        player().getInventory().setItemInMainHand(WandSVC.createWand(b[0], b[1]));
        player().updateInventory();
        sender().playSound(Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1f, 0.55f);
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_OBJECT_AUTO_SELECT_COMPLETE_2));
    }
}
