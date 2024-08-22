package com.volmit.iris.engine.service;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisEngineService;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.mobs.IrisMobDataHandler;
import com.volmit.iris.util.mobs.IrisMobPiece;
import com.volmit.iris.util.scheduling.Looper;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import io.lumine.mythic.bukkit.adapters.BukkitEntity;
import it.unimi.dsi.fastutil.Hash;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class EngineMobHandlerSVC extends IrisEngineService implements IrisMobDataHandler {

    private int id;
    public double energy;
    private HashMap<Types, Integer> bukkitLimits;
    private Function<EntityType, Types> entityType;
    private ConcurrentLinkedQueue<IrisMobPiece> pieces;
    public enum Types {
        monsters,
        animals,
        water_animals,
        water_ambient,
        water_underground_creature,
        axolotls,
        ambient
    }

    public EngineMobHandlerSVC(Engine engine) {
        super(engine);
    }

    @Override
    public void onEnable(boolean hotload) {

        this.id = engine.getCacheID();
        this.pieces = new ConcurrentLinkedQueue<>();
        this.entityType = (entityType) -> Types.valueOf(INMS.get().getMobCategory(entityType));
        this.bukkitLimits = new HashMap<>();


        //new Ticker();
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

                fixEnergy();
                Predicate<IrisMobPiece> shouldExecutePredicate = IrisMobPiece::shouldTick;
                Consumer<IrisMobPiece> executeMethod = IrisMobPiece::tick;

                pieces.stream()
                        .filter(shouldExecutePredicate)
                        .forEach(executeMethod);

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
        try {
            fc.load(new File("bukkit.yml"));
            ConfigurationSection section = fc.getConfigurationSection("spawn-limits");
            if (section == null) {
                throw new NoSuchFieldException("spawn-limits not found!");
            }

            for (String s : section.getKeys(false)) {
                try {
                    ConfigurationSection entry = section.getConfigurationSection(s);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            Iris.reportError(e);
        }
        return null;
    }


    private void fixEnergy() {
        energy = M.clip(energy, 1D, engine.getDimension().getEnergy().evaluate(null, engine.getData(), energy));
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
    public double getEnergy() {
        fixEnergy();
        return energy;
    }
}