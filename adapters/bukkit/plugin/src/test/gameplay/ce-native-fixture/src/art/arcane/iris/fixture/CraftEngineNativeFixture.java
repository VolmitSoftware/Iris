package art.arcane.iris.fixture;

import art.arcane.iris.generation.block.IrisCustomData;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.mantle.DeferredPlacementRecords;
import art.arcane.iris.generation.mantle.CustomBlockOwnership;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.integration.ExternalDataSVC;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.platform.generation.EngineBukkitOps;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.world.IrisCreator;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.nativelib.terrain.NativeBlockChangeSet;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineFurniture;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class CraftEngineNativeFixture extends JavaPlugin {
    private static final NamespacedKey WORLD_KEY = new NamespacedKey("iris", "ce_native_probe");
    private static final NamespacedKey FEATURE_WORLD_KEY = new NamespacedKey("iris", "ce_native_feature_probe");
    private static final int TEST_Y = 180;
    private static final int TEST_Z = 8;
    private static final String BLOCK_ID = "craftengine:default/topaz_ore";
    private static final String FURNITURE_ID = "craftengine:default/flower_basket[variant=ground]";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!(sender instanceof Player player) || arguments.length != 1) {
            sender.sendMessage("CE_FAILED Use /iriscenative prepare|pending|neighbor|loaded|features|restart from a player");
            return true;
        }
        if (arguments[0].equals("prepare")) {
            player.setGameMode(GameMode.CREATIVE);
            prepare(player);
            return true;
        }
        if (arguments[0].equals("features")) {
            importedFeatures(player);
            return true;
        }
        World world = Bukkit.getWorld(WORLD_KEY);
        if (world == null) {
            fail(player, new IllegalStateException("Fixture world has not been created"));
            return true;
        }
        if (arguments[0].equals("restart")) {
            loadChunks().thenCompose(ignored -> J.runRegionFuture(world, 0, 0,
                    () -> later(player, world, () -> restarted(player, world))))
                    .whenComplete((ignored, failure) -> {
                        if (failure != null) {
                            fail(player, failure);
                        }
                    });
            return true;
        }
        J.runRegionFuture(world, 0, 0, () -> {
            switch (arguments[0]) {
                case "pending" -> pending(player, world);
                case "neighbor" -> neighbor(player, world);
                case "loaded" -> invalidateLoaded(player, world, IrisToolbelt.access(world).getEngine());
                default -> throw new IllegalArgumentException("Unknown fixture operation");
            }
        }).whenComplete((ignored, failure) -> {
            if (failure != null) {
                fail(player, failure);
            }
        });
        return true;
    }

    private void prepare(Player player) {
        CompletableFuture.runAsync(() -> {
            if (Bukkit.getWorld(WORLD_KEY) == null) {
                try {
                    new IrisCreator().name(WORLD_KEY.toString()).dimension("ce-native-fixture")
                            .seed(78264193L).create();
                } catch (Exception failure) {
                    throw new IllegalStateException("Could not create CraftEngine fixture world", failure);
                }
            }
        }).thenCompose(ignored -> loadChunks()).thenCompose(ignored -> {
            World world = Bukkit.getWorld(WORLD_KEY);
            return J.runRegionFuture(world, 0, 0, () -> preparePlatform(world));
        }).thenAccept(ignored -> {
            World world = Bukkit.getWorld(WORLD_KEY);
            if (!J.runEntity(player, () -> player.teleportAsync(new Location(world, 8.5, TEST_Y + 3, 8.5))
                    .whenComplete((teleported, failure) -> {
                        if (failure != null || !Boolean.TRUE.equals(teleported)) {
                            fail(player, failure == null ? new IllegalStateException("Fixture teleport failed") : failure);
                            return;
                        }
                        player.sendMessage("CE_READY world=" + WORLD_KEY + " folia=" + J.isFolia());
                    }))) {
                throw new IllegalStateException("Could not schedule fixture teleport");
            }
        }).exceptionally(failure -> {
            fail(player, failure);
            return null;
        });
    }

    private void importedFeatures(Player player) {
        CompletableFuture.runAsync(() -> {
            if (Bukkit.getWorld(FEATURE_WORLD_KEY) == null) {
                try {
                    new IrisCreator().name(FEATURE_WORLD_KEY.toString()).dimension("ce-native-feature-fixture")
                            .seed(78264193L).create();
                } catch (Exception failure) {
                    throw new IllegalStateException("Could not create imported feature fixture world", failure);
                }
            }
        }).thenCompose(ignored -> {
            World world = Bukkit.getWorld(FEATURE_WORLD_KEY);
            return world.getChunkAtAsync(0, 0, true);
        }).thenCompose(chunk -> J.runRegionFuture(chunk.getWorld(), 0, 0, () -> {
            World world = chunk.getWorld();
            Engine engine = IrisToolbelt.access(world).getEngine();
            int snow = 0;
            int pending = 0;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (world.getBlockAt(x, 97, z).getType() == Material.SNOW) {
                        snow++;
                    }
                    if (engine.getMantle().getMantle().get(x, 97 - engine.getMinHeight(), z, Identifier.class) != null) {
                        pending++;
                    }
                }
            }
            require(snow == 256, "Imported snow feature did not cover all 256 test columns: " + snow);
            require(pending == 0, "Imported feature left pending furniture records: " + pending);
            for (Entity entity : chunk.getEntities()) {
                require(!CraftEngineFurniture.isFurniture(entity), "Imported snow feature left generated furniture");
            }
            if (!J.runEntity(player, () -> player.teleportAsync(new Location(world, 8.5, 101, 8.5))
                    .whenComplete((teleported, failure) -> {
                        if (failure != null || !Boolean.TRUE.equals(teleported)) {
                            fail(player, failure == null ? new IllegalStateException("Feature world teleport failed") : failure);
                            return;
                        }
                        player.sendMessage("CE_FEATURES snow=256 pending=0 furniture=0");
                    }))) {
                throw new IllegalStateException("Could not schedule feature world teleport");
            }
        })).exceptionally(failure -> {
            fail(player, failure);
            return null;
        });
    }

    private CompletableFuture<Void> loadChunks() {
        World world = Bukkit.getWorld(WORLD_KEY);
        require(world != null && IrisToolbelt.isIrisWorld(world), "Fixture must use a real Iris world");
        List<CompletableFuture<Void>> chunks = new ArrayList<>(15);
        for (int x = -1; x <= 3; x++) {
            for (int z = -1; z <= 1; z++) {
                chunks.add(world.getChunkAtAsync(x, z, true).thenCompose(chunk ->
                        J.runRegionFuture(world, chunk.getX(), chunk.getZ(), () -> chunk.addPluginChunkTicket(this))));
            }
        }
        return CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new));
    }

    private void preparePlatform(World world) {
        require(CraftEngineBlocks.byId(Key.of("default:topaz_ore")) != null, "CraftEngine default block is missing");
        require(CraftEngineFurniture.byId(Key.of("default:flower_basket")) != null, "CraftEngine furniture is missing");
        for (int chunkX = 0; chunkX <= 2; chunkX++) {
            for (Entity entity : world.getChunkAt(chunkX, 0).getEntities()) {
                if (CraftEngineFurniture.isFurniture(entity)) {
                    CraftEngineFurniture.remove(entity, false, false);
                }
            }
        }
        for (int x = 0; x <= 47; x++) {
            for (int z = 2; z <= 14; z++) {
                world.getBlockAt(x, TEST_Y - 1, z).setType(Material.STONE, false);
                for (int y = TEST_Y; y <= TEST_Y + 5; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    private void pending(Player player, World world) {
        Engine engine = IrisToolbelt.access(world).getEngine();
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        queue(engine, world, 2, BLOCK_ID);
        queue(engine, world, 4, BLOCK_ID);
        queue(engine, world, 6, BLOCK_ID);
        queue(engine, world, 8, BLOCK_ID);
        queue(engine, world, 10, FURNITURE_ID);
        queue(engine, world, 12, FURNITURE_ID);
        Block chest = world.getBlockAt(14, TEST_Y, TEST_Z);
        chest.setType(Material.CHEST, false);
        mantle.set(14, TEST_Y - engine.getMinHeight(), TEST_Z, new TileWrapper(TileData.getTileState(chest, false)));
        world.getBlockAt(4, TEST_Y, TEST_Z).setType(Material.AIR, false);
        world.getBlockAt(6, TEST_Y, TEST_Z).setType(Material.COBBLESTONE, false);

        NativeBlockChangeSet changes = DeferredPlacementRecords.track(engine, bounds(world));
        changes.add(8, TEST_Y, TEST_Z);
        changes.add(12, TEST_Y - 1, TEST_Z);
        changes.add(12, TEST_Y, TEST_Z);
        changes.add(14, TEST_Y, TEST_Z);
        world.getBlockAt(8, TEST_Y, TEST_Z).setType(Material.AIR, false);
        world.getBlockAt(12, TEST_Y - 1, TEST_Z).setType(Material.AIR, false);
        chest.setType(Material.COBBLESTONE, false);
        DeferredPlacementRecords.invalidate(engine, changes);
        require(record(engine, 8, Identifier.class) == null, "Native cut retained a pending custom record");
        require(record(engine, 12, Identifier.class) == null, "Flatten column retained pending furniture");
        require(record(engine, 14, TileWrapper.class) == null, "Native wall retained pending chest data");
        require(record(engine, 2, Identifier.class) != null, "Native writes invalidated an untouched custom record");
        replay(engine, world, 0);
        CustomBlockOwnership ownership = record(engine, 10, CustomBlockOwnership.class);
        require(record(engine, 2, Identifier.class) == null, "Custom materialization did not consume pending records");
        require(CraftEngineBlocks.isCustomBlock(world.getBlockAt(2, TEST_Y, TEST_Z)), "Intact custom block was not placed");
        require(world.getBlockAt(4, TEST_Y, TEST_Z).getType().isAir(), "Cut custom block was resurrected");
        require(world.getBlockAt(6, TEST_Y, TEST_Z).getType() == Material.COBBLESTONE, "Custom block replaced native wall");
        require(world.getBlockAt(8, TEST_Y, TEST_Z).getType().isAir(), "Invalidated custom block was resurrected");
        require(chest.getType() == Material.COBBLESTONE, "Chest data replaced native wall");
        later(player, world, () -> {
            int intactFurniture = furnitureAt(world, 10);
            require(intactFurniture == 1, "Intact furniture count is " + intactFurniture
                    + "; pending=" + record(engine, 10, Identifier.class)
                    + "; ownership=" + ownership
                    + "; chunkEntities=" + world.getChunkAt(0, 0).getEntities().length);
            require(furnitureAt(world, 12) == 0, "Flattened furniture was resurrected");
            player.sendMessage("CE_PENDING intact=1 air=1 wall=1 invalidated=1 flatten=1 chest=1 furniture=1");
        });
    }

    private void neighbor(Player player, World world) {
        Engine engine = IrisToolbelt.access(world).getEngine();
        queue(engine, world, 18, FURNITURE_ID);
        queue(engine, world, 20, FURNITURE_ID);
        queue(engine, world, 22, FURNITURE_ID);
        queue(engine, world, 42, FURNITURE_ID);
        replay(engine, world, 1);
        replay(engine, world, 2);
        world.getBlockAt(40, TEST_Y, TEST_Z).setType(Material.DIAMOND_BLOCK, false);
        later(player, world, () -> {
            require(furnitureAt(world, 18) == 1 && furnitureAt(world, 20) == 1
                    && furnitureAt(world, 22) == 1, "Neighbor furniture did not materialize");
            require(furnitureAt(world, 42) == 1 && record(engine, 42, CustomBlockOwnership.class) != null,
                    "Regeneration target furniture did not materialize with saved ownership");
            if (!J.runEntity(player, () -> player.teleportAsync(new Location(world, 40.5, TEST_Y + 3, TEST_Z + 0.5))
                    .whenComplete((teleported, failure) -> {
                        if (failure != null || !Boolean.TRUE.equals(teleported)) {
                            fail(player, failure == null ? new IllegalStateException("Regeneration target teleport failed") : failure);
                            return;
                        }
                        player.sendMessage("CE_NEIGHBOR_READY chunk=2,0 furniture=3 target=1");
                    }))) {
                throw new IllegalStateException("Could not schedule regeneration target teleport");
            }
        });
    }

    private void invalidateLoaded(Player player, World world, Engine engine) {
        require(world.getBlockAt(40, TEST_Y, TEST_Z).getType().isAir(), "Adjacent target chunk did not regenerate");
        require(furnitureAt(world, 18) == 1 && furnitureAt(world, 20) == 1
                && furnitureAt(world, 22) == 1, "Neighbor furniture did not materialize");
        NativeBlockChangeSet changes = DeferredPlacementRecords.track(engine, bounds(world));
        changes.add(18, TEST_Y - 1, TEST_Z);
        changes.add(18, TEST_Y, TEST_Z);
        changes.add(22, TEST_Y, TEST_Z);
        world.getBlockAt(18, TEST_Y - 1, TEST_Z).setType(Material.AIR, false);
        world.getBlockAt(18, TEST_Y, TEST_Z).setType(Material.COBBLESTONE, false);
        DeferredPlacementRecords.invalidate(engine, changes);
        require(CraftEngineFurniture.place(new Location(world, 22.5, TEST_Y, TEST_Z + 0.5),
                Key.of("default:flower_basket"), "ground", false) != null,
                "Could not place replacement furniture before scheduled cleanup");
        later(player, world, () -> {
            require(furnitureAt(world, 42) == 0, "Regeneration retained target chunk furniture");
            require(furnitureAt(world, 18) == 0, "Native write left materialized neighbor furniture");
            require(furnitureAt(world, 20) == 1, "Native write removed untouched neighbor furniture");
            require(furnitureAt(world, 22) == 1, "Native cleanup removed replacement furniture or retained the old one");
            require(world.getBlockAt(18, TEST_Y, TEST_Z).getType() == Material.COBBLESTONE,
                    "Furniture cleanup replaced the native wall");
            player.sendMessage("CE_LOADED removed=1 untouched=1 replacement=1 wall=1 target=1");
        });
    }

    private void restarted(Player player, World world) {
        require(furnitureAt(world, 20) == 1, "Generated neighbor furniture did not survive restart");
        require(furnitureAt(world, 10) == 1, "Untouched furniture did not survive restart");
        Engine engine = IrisToolbelt.access(world).getEngine();
        NativeBlockChangeSet changes = DeferredPlacementRecords.track(engine, bounds(world));
        changes.add(20, TEST_Y, TEST_Z);
        DeferredPlacementRecords.invalidate(engine, changes);
        later(player, world, () -> {
            require(furnitureAt(world, 20) == 0, "Persisted furniture ownership was not invalidated after restart");
            require(furnitureAt(world, 10) == 1, "Restart cleanup removed untouched furniture");
            player.sendMessage("CE_RESTART removed=1 untouched=1");
        });
    }

    private void queue(Engine engine, World world, int x, String identifier) {
        ExternalDataSVC service = IrisServices.get(ExternalDataSVC.class);
        BlockData data = service.getBlockData(Identifier.fromString(identifier)).orElseThrow();
        require(data instanceof IrisCustomData, "Iris did not resolve real CraftEngine content: " + identifier);
        IrisCustomData custom = (IrisCustomData) data;
        world.getBlockAt(x, TEST_Y, TEST_Z).setBlockData(custom.getBase(), false);
        engine.getMantle().getMantle().set(x, TEST_Y - engine.getMinHeight(), TEST_Z, custom.getCustom());
    }

    private <T> T record(Engine engine, int x, Class<T> type) {
        return engine.getMantle().getMantle().get(x, TEST_Y - engine.getMinHeight(), TEST_Z, type);
    }

    private void replay(Engine engine, World world, int chunkX) {
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        for (int x = chunkX - 1; x <= chunkX + 1; x++) {
            for (int z = -1; z <= 1; z++) {
                require(world.isChunkLoaded(x, z), "Replay neighbor is not loaded: " + x + "," + z);
            }
        }
        require(mantle.isLoaded(world.getChunkAt(chunkX, 0)), "Replay mantle chunk is not loaded: " + chunkX);
        mantle.flag(chunkX, 0, MantleFlag.ETCHED, false);
        mantle.flag(chunkX, 0, MantleFlag.TILE, false);
        mantle.flag(chunkX, 0, MantleFlag.CUSTOM, false);
        EngineBukkitOps.updateChunk(engine, world.getChunkAt(chunkX, 0), mantle);
    }

    private int furnitureAt(World world, int x) {
        int count = 0;
        for (Entity entity : world.getChunkAt(x >> 4, TEST_Z >> 4).getEntities()) {
            Location location = entity.getLocation();
            if (location.getBlockX() == x && location.getBlockY() == TEST_Y && location.getBlockZ() == TEST_Z
                    && CraftEngineFurniture.getLoadedFurnitureByMetaEntity(entity) != null) {
                count++;
            }
        }
        return count;
    }

    private void later(Player player, World world, Runnable assertion) {
        Bukkit.getRegionScheduler().runDelayed(this, new Location(world, 8, TEST_Y, TEST_Z), task -> {
            try {
                assertion.run();
            } catch (Throwable failure) {
                fail(player, failure);
            }
        }, 20);
    }

    private NativeBlockChangeSet.Bounds bounds(World world) {
        return new NativeBlockChangeSet.Bounds(-1, -1, 3, 1, world.getMinHeight(), world.getMaxHeight() - world.getMinHeight());
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private void fail(CommandSender sender, Throwable failure) {
        getLogger().log(Level.SEVERE, "CraftEngine native placement assertion failed", failure);
        sender.sendMessage("CE_FAILED " + failure.getMessage());
    }
}
