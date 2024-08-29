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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
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
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class EngineMobHandlerSVC extends IrisEngineService implements IrisMobDataHandler {

    private HashSet<HistoryData> history;

    private Function<Map<IrisMobPiece, List<Integer>>, LinkedHashMap<IrisMobPiece, List<Integer>>> sortMapFunction;

    private Function<Map<IrisMobPiece, Integer>, LinkedHashMap<IrisMobPiece, Integer>> sortMapSimpleFunction;

    private int id;
    public int energyMax;
    public int energy;
    private long iteration = 0; // 1 every second
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
        this.sortMapFunction =
                new Function<>() {
                    private Map<IrisMobPiece, List<Integer>> lastMap;
                    private LinkedHashMap<IrisMobPiece, List<Integer>> cachedSortedMap;

                    @Override
                    public LinkedHashMap<IrisMobPiece, List<Integer>> apply(Map<IrisMobPiece, List<Integer>> inputMap) {
                        if (cachedSortedMap == null || !inputMap.equals(lastMap)) {
                            cachedSortedMap = inputMap.entrySet()
                                    .stream()
                                    .sorted(Map.Entry.<IrisMobPiece, List<Integer>>comparingByValue(
                                            Comparator.comparingInt(list -> list.stream().mapToInt(Integer::intValue).sum())
                                    ).reversed())
                                    .collect(Collectors.toMap(
                                            Map.Entry::getKey,
                                            Map.Entry::getValue,
                                            (e1, e2) -> e1,
                                            LinkedHashMap::new
                                    ));
                            lastMap = new HashMap<>(inputMap);
                        }
                        return cachedSortedMap;
                    }
                };

        this.sortMapSimpleFunction = new Function<>() {
            private Map<IrisMobPiece, Integer> lastMap;
            private LinkedHashMap<IrisMobPiece, Integer> cachedSortedMap;

            @Override
            public LinkedHashMap<IrisMobPiece, Integer> apply(Map<IrisMobPiece, Integer> inputMap) {
                if (cachedSortedMap == null || !inputMap.equals(lastMap)) {
                    cachedSortedMap = inputMap.entrySet()
                            .stream()
                            .sorted(Map.Entry.<IrisMobPiece, Integer>comparingByValue(Comparator.reverseOrder()))
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (e1, e2) -> e1,
                                    LinkedHashMap::new
                            ));
                    lastMap = new HashMap<>(inputMap);
                }
                return cachedSortedMap;
            }
        };

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

    /**
     * @param req Data to do calculations with. List<Integer>> = Requested energy + future
     * @return returns the energy distribution for each piece
     */
    private LinkedHashMap<IrisMobPiece, Integer> assignEnergyToPieces(LinkedHashMap<IrisMobPiece, List<Integer>> req) {

        // Might need caching?
        int futureView = 60;
        int energy = calculateNewEnergy();

        LinkedHashMap<IrisMobPiece, Integer> finalMap = req.keySet().stream()
                .collect(Collectors.toMap(
                        piece -> piece,
                        piece -> null,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));

        Function<Integer, Stream<Integer>> viewSingleFuture = (future) ->
                req.values().stream()
                        .mapToInt(list -> list.isEmpty() ? 0 : list.get(future)).boxed();

        Function<Integer, Function<Integer, Stream<Integer>>> viewFuture = (rangeMin) -> (rangeMax) ->
                IntStream.range(rangeMin, rangeMax)
                        .boxed()
                        .flatMap(viewSingleFuture);

        Function<Integer, Double> viewFutureMedian = (value) -> viewFuture.apply(value).apply(futureView)
                .sorted()
                .collect(Collectors.collectingAndThen(Collectors.toList(),
                        list -> {
                            int size = list.size();
                            if (size % 2 == 0) {
                                return (list.get(size / 2 - 1) + list.get(size / 2)) / 2.0;
                            } else {
                                return list.get(size / 2).doubleValue();
                            }
                        }));

        // Logic

        if ((predictEnergy(futureView) / viewFutureMedian.apply(0)) > 1.25 && (energy / viewSingleFuture.apply(0).mapToInt(Integer::intValue).sum() > 1)) {

            finalMap = req.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> e.getValue().isEmpty() ? 0 : e.getValue().get(0),
                            (v1, v2) -> v1,
                            LinkedHashMap::new
                    ));
        } else if ((energy / viewSingleFuture.apply(0).mapToInt(Integer::intValue).sum() > 1)) {
            // hard part


        } else if ((energy / viewSingleFuture.apply(0).mapToInt(Integer::intValue).sum() < 1)) {
            double scale = 1;
            while ((double) energy / viewSingleFuture.apply(0).mapToInt(Integer::intValue).sum() >= scale) {
                scale -= 0.1;
            }
            double finalScale = scale + 0.1;

            LinkedHashMap<IrisMobPiece, Integer> finalMap1 = finalMap;
            req.forEach((key, value) -> finalMap1.put(key, (int) (value.get(0) * finalScale)));
        }

        return finalMap;

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