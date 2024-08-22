package com.volmit.iris.engine.service;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisEngineService;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.mobs.IrisMobDataHandler;
import com.volmit.iris.util.mobs.IrisMobPiece;
import com.volmit.iris.util.scheduling.Looper;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import io.lumine.mythic.bukkit.utils.lib.jooq.impl.QOM;
import org.bukkit.Chunk;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class EngineMobHandlerSVC extends IrisEngineService implements IrisMobDataHandler {

    private int id;
    public double energyMax;
    public double energy;
    private HashSet<Chunk> loadedChunks;
    private HashMap<Types, Integer> bukkitLimits;
    private Function<EntityType, Types> entityType;
    private ConcurrentLinkedQueue<IrisMobPiece> pieces;

    public EngineMobHandlerSVC(Engine engine) {
        super(engine);
    }

    @Override
    public void onEnable(boolean hotload) {

        this.id = engine.getCacheID();
        this.pieces = new ConcurrentLinkedQueue<>();
        this.entityType = (entityType) -> Types.valueOf(INMS.get().getMobCategory(entityType));
        this.loadedChunks = new HashSet<>();
        this.bukkitLimits = getBukkitLimits();

        new Ticker();
    }

    @Override
    public void onDisable(boolean hotload) {

    }

    private class Ticker extends Looper {
        private final CountDownLatch exit = new CountDownLatch(1);

        private Ticker() {
            setPriority(Thread.NORM_PRIORITY);
            start();
            Iris.debug("Started Mob Engine for: " + engine.getName());
        }

        @Override
        protected long loop() {
            long wait = -1;
            try {
                if (engine.isClosed() || engine.getCacheID() != id) {
                    interrupt();
                }

                PrecisionStopwatch stopwatch = new PrecisionStopwatch();
                stopwatch.begin();

                loadedChunks = Arrays.stream(getEngine().getWorld().realWorld().getLoadedChunks())
                        .collect(Collectors.toCollection(HashSet::new));

                fixEnergy();

                Predicate<IrisMobPiece> shouldTick = IrisMobPiece::shouldTick;
                Function<IrisMobPiece, List<Integer>> tickCosts = piece -> piece.getTickCosts(1);

                Map<IrisMobPiece, List<Integer>> pieceTickCostsMap = pieces.stream()
                        .collect(Collectors.toMap(
                                Function.identity(),
                                tickCosts
                        ));

                Consumer<IrisMobPiece> tick = piece -> piece.tick(42);

                pieces.stream()
                        .filter(shouldTick)
                        .forEach(tick);

                stopwatch.end();
                Iris.info("Took: " + Form.f(stopwatch.getMilliseconds()));
                double millis = stopwatch.getMilliseconds();
                int size = pieces.size();
                wait = size == 0 ? 50L : (long) Math.max(50d / size - millis, 0);
            } catch (Throwable notIgnored) {
                notIgnored.printStackTrace();
            }
            if (wait < 0) exit.countDown();
            return wait;
        }
    }

    private void assignEnergyToPieces(LinkedHashMap<IrisMobPiece, List<Integer>> map) {
        Supplier<LinkedHashMap<IrisMobPiece, List<Integer>>> sortedMapSupplier = new Supplier<>() {
            private LinkedHashMap<IrisMobPiece, List<Integer>> cachedMap;

            @Override
            public LinkedHashMap<IrisMobPiece, List<Integer>> get() {
                if (cachedMap == null) {
                    cachedMap = map.entrySet()
                            .stream()
                            .sorted(Map.Entry.<IrisMobPiece, List<Integer>>comparingByValue(
                                    Comparator.comparingInt(list -> list.stream().mapToInt(Integer::intValue).sum())
                            ).reversed())
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (e1, e2) -> e1, // Handle potential duplicates by keeping the first entry
                                    LinkedHashMap::new
                            ));
                }
                return cachedMap;
            }
        };

        Function<Integer,Integer> viewHistory = (history) -> map.values().stream()
                .mapToInt(list -> list.isEmpty() ? 0 : list.get(history)) // Extract the first element or use 0 if the list is empty
                .sum();


    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void on(PlayerChangedWorldEvent event) {
        if (!engine.getWorld().tryGetRealWorld()) {
            // todo Handle this
            return;
        }

        var world = engine.getWorld().realWorld();
        if (world == null) return; //how is this even possible

        var player = event.getPlayer();
        if (player.getWorld().equals(world)) {
            pieces.add(new IrisMobPiece(player, this));
            return;
        }

        if (event.getFrom().equals(world)) {
            pieces.removeIf(piece -> {
                if (piece.getOwner().equals(player.getUniqueId())) {
                    piece.close();
                    return true;
                }
                return false;
            });
        }
    }

    private HashMap<Types, Integer> getBukkitLimits() {
        HashMap<Types, Integer> temp = new HashMap<>();
        FileConfiguration fc = new YamlConfiguration();
        var section = fc.getConfigurationSection("spawn-limits");
        if (section != null)
            section.getKeys(false).forEach(key -> temp.put(Types.valueOf(key), section.getInt(key)));
        return temp;
    }

    private void fixEnergy() {
        energyMax = engine.getDimension().getEnergy().evaluate(null, engine.getData(), energy);
    }

    @Override
    public Function<EntityType, Types> getMobType() {
        return entityType;
    }

    @Override
    public Engine getEngine() {
        return engine;
    }

    @Override
    public HashSet<Chunk> getChunks() {
        return loadedChunks;
    }

    @Override
    public HashMap<Types, Integer> bukkitLimits() {
        return bukkitLimits;
    }

    @Override
    public double getEnergy() {
        fixEnergy();
        return energy;
    }
}