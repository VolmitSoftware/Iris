package com.volmit.iris.engine.service;

import com.google.common.util.concurrent.AtomicDouble;
import com.volmit.iris.Iris;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisEngineService;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.mobs.IrisMobDataHandler;
import com.volmit.iris.util.mobs.IrisMobPiece;
import com.volmit.iris.util.scheduling.Looper;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

public class EngineMobHandlerSVC extends IrisEngineService implements IrisMobDataHandler {

    private int id;
    public double energy;
    private Supplier<IrisMobPiece> supplier;
    private ConcurrentLinkedQueue<IrisMobPiece> pieces;


    public EngineMobHandlerSVC(Engine engine) {
        super(engine);
    }

    @Override
    public void onEnable(boolean hotload) {

        this.id = engine.getCacheID();
        this.pieces = new ConcurrentLinkedQueue<>();
        this.supplier = null;

        //new Ticker();
    }

    @Override
    public void onDisable(boolean hotload) {

    }

    public Ticker tick() {
        return new EngineMobHandlerSVC.Ticker(() -> {



            return null;
        });


    }

    private class Ticker extends Looper {
        private final CountDownLatch exit = new CountDownLatch(1);

        private Ticker(Supplier<IrisMobPiece> supplier) {
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



                stopwatch.end();
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
//            pieces.removeIf(piece -> {
//                if (piece.getOwner().equals(player.getUniqueId())) {
//                    piece.close();
//                    return true;
//                }
//                return false;
//            });
        }
    }


    private void fixEnergy() {
        energy = M.clip(energy, 1D, engine.getDimension().getEnergy().evaluate(null, engine.getData(), energy));
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