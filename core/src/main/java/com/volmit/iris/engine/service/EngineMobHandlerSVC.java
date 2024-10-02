package com.volmit.iris.engine.service;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisEngineService;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.mobs.IrisMobDataHandler;
import com.volmit.iris.util.mobs.IrisMobPiece;
import com.volmit.iris.util.scheduling.Looper;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.AllArgsConstructor;
import lombok.Data;
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
import java.util.stream.Collectors;

public class EngineMobHandlerSVC extends IrisEngineService implements IrisMobDataHandler {

    private HashSet<HistoryData> history;

    private int id;
    public int energyMax;
    public int energy;
    private long iteration = 0; // ~1 every second
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
        this.history = new HashSet<>();
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
                iteration++;
                if (engine.isClosed() || engine.getCacheID() != id) {
                    interrupt();
                }
                PrecisionStopwatch stopwatch = new PrecisionStopwatch();
                stopwatch.begin();

                loadedChunks = Arrays.stream(getEngine().getWorld().realWorld().getLoadedChunks())
                        .collect(Collectors.toCollection(HashSet::new));

                updateMaxEnergy();
                Predicate<IrisMobPiece> shouldTick = IrisMobPiece::shouldTick;
                Map<IrisMobPiece, Integer> costs = assignEnergyToPieces(pieces.stream()
                        .collect(Collectors.toMap(
                                piece -> piece,
                                piece -> piece.getTickCosts(60)
                        )));
                Consumer<IrisMobPiece> tick = piece -> piece.tick(costs.get(piece));

                double oe = energy;
                pieces.stream()
                        .filter(shouldTick)
                        .forEach(tick);
                oe -= energy;
                history.add(new HistoryData(DataType.ENERGY_CONSUMPTION, oe, iteration));


                stopwatch.end();
                Iris.info("Took: " + Form.f(stopwatch.getMilliseconds()));
                long millis = stopwatch.getMillis();

                // todo finish this rubbish
                wait = 1000 - millis;
                if (wait < 0) {
                    return wait;
                } else {
                    Iris.info(C.YELLOW + "Mob Engine lagging behind: " + Form.f(stopwatch.getMilliseconds()));
                   return 0;
                }
            } catch (Throwable notIgnored) {
                notIgnored.printStackTrace();
            }
            if (wait < 0) exit.countDown();
            return wait;
        }
    }

    /**
     * Assigns energy to pieces based on current and future energy requirements.
     * @param req Data to do calculations with. Map of IrisMobPiece to List<Integer> representing requested energy + future
     * @return Map of IrisMobPiece to Integer representing the energy distribution for each piece
     */
    private Map<IrisMobPiece, Integer> assignEnergyToPieces(Map<IrisMobPiece, List<Integer>> req) {
        final int FUTURE_VIEW = 60;
        int currentEnergy = calculateNewEnergy();

        Map<IrisMobPiece, Integer> finalMap = new LinkedHashMap<>();
        req.forEach((piece, value) -> finalMap.put(piece, null));

        double currentEnergyRatio = (double) currentEnergy / sumCurrentRequests(req);
        double futureEnergyRatio = (double) predictEnergy(FUTURE_VIEW) / calculateFutureMedian(req, FUTURE_VIEW);

        if (futureEnergyRatio > 1.25 && currentEnergyRatio > 1) {
            req.forEach((piece, energyList) -> finalMap.put(piece, energyList.isEmpty() ? 0 : energyList.get(0)));
        } else if (currentEnergyRatio > 1) {

            // TODO: Implement hard part

        } else if (currentEnergyRatio < 1) {
            double scale = calculateScale(currentEnergy, req);
            req.forEach((piece, energyList) -> finalMap.put(piece, (int) (energyList.get(0) * scale)));
        }

        return finalMap;
    }

    private int sumCurrentRequests(Map<IrisMobPiece, List<Integer>> req) {
        return req.values().stream()
                .mapToInt(list -> list.isEmpty() ? 0 : list.get(0))
                .sum();
    }

    private double calculateFutureMedian(Map<IrisMobPiece, List<Integer>> req, int futureView) {
        List<Integer> allFutureValues = new ArrayList<>();
        for (int i = 0; i < futureView; i++) {
            for (List<Integer> energyList : req.values()) {
                if (i < energyList.size()) {
                    allFutureValues.add(energyList.get(i));
                }
            }
        }
        Collections.sort(allFutureValues);
        int size = allFutureValues.size();
        if (size % 2 == 0) {
            return (allFutureValues.get(size / 2 - 1) + allFutureValues.get(size / 2)) / 2.0;
        } else {
            return allFutureValues.get(size / 2);
        }
    }

    private double calculateScale(int currentEnergy, Map<IrisMobPiece, List<Integer>> req) {
        double scale = 1.0;
        double ratio = (double) currentEnergy / sumCurrentRequests(req);
        while (ratio < scale) {
            scale -= 0.1;
        }
        return Math.min(scale + 0.1, 1.0);
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

    private void updateMaxEnergy() {
        var e = (int) engine.getDimension().getEnergy().evaluateMax("max", null, engine.getData(), (double) energy);
        history.add(new HistoryData(DataType.ENERGY_MAX, e, iteration));
        energyMax = e;
    }

    private int calculateNewEnergy() {
        var e = engine.getDimension().getEnergy().evaluateMax("cur",null, engine.getData(), (double) energy);
        history.add(new HistoryData(DataType.ENERGY_ADDITION, (int) e, iteration));
        return (int) e;
    }

    /**
     *
     * @param it How many iterations it should get to predict: 1 = second
     * @return The Iterations/cost
     */
    private Integer predictEnergy(int it) {
        List<Integer> list = new ArrayList<>();
        history.stream()
                .filter(data -> data.getDataType() == DataType.ENERGY_ADDITION) // Filter by target DataType
                .forEach(data -> {
                    if (data.getIteration() > iteration - it) {
                        list.add((Integer) data.getValue());
                    }
                });

        return (int) list.stream().sorted().skip((list.size() - 1) / 2).limit(2 - list.size() % 2).mapToInt(Integer::intValue).average().orElse(Double.NaN);
    }

    @Override
    public long getIteration() {
        return iteration;
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
        updateMaxEnergy();
        return energy;
    }

    @Data
    @AllArgsConstructor
    private class HistoryData<T> {
        private DataType dataType;
        private T value;
        private long iteration;
    }
}