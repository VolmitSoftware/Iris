package com.volmit.iris.engine.service;

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisEngineService;
import com.volmit.iris.util.collection.KList;

public class EngineStatusSVC extends IrisEngineService {
    private static final KList<EngineStatusSVC> INSTANCES = new KList<>();

    public EngineStatusSVC(Engine engine) {
        super(engine);
    }

    @Override
    public void onEnable(boolean hotload) {
        if (hotload) return;
        synchronized (INSTANCES) {
            INSTANCES.add(this);
        }
    }

    @Override
    public void onDisable(boolean hotload) {
        if (hotload) return;

        synchronized (INSTANCES) {
            INSTANCES.remove(this);
        }
    }

    public static int getEngineCount() {
        return INSTANCES.size();
    }

    public static Status getStatus() {
        synchronized (INSTANCES) {
            long loadedChunks = 0;
            long tectonicPlates = 0;
            long activeTectonicPlates = 0;
            long queuedTectonicPlates = 0;
            long minTectonicUnloadDuration = Long.MAX_VALUE;
            long maxTectonicUnloadDuration = Long.MIN_VALUE;

            for (var service : INSTANCES) {
                var world = service.engine.getWorld();
                if (world.hasRealWorld())
                    loadedChunks += world.realWorld().getLoadedChunks().length;

                tectonicPlates += service.engine.getMantle().getLoadedRegionCount();
                activeTectonicPlates += service.engine.getMantle().getNotQueuedLoadedRegions();
                queuedTectonicPlates += service.engine.getMantle().getToUnload();
                minTectonicUnloadDuration = Math.min(minTectonicUnloadDuration, (long) service.engine.getMantle().getTectonicDuration());
                maxTectonicUnloadDuration = Math.max(maxTectonicUnloadDuration, (long) service.engine.getMantle().getTectonicDuration());
            }
            return new Status(INSTANCES.size(), loadedChunks, MantleCleanerSVC.getTectonicLimit(), tectonicPlates, activeTectonicPlates, queuedTectonicPlates, minTectonicUnloadDuration, maxTectonicUnloadDuration);
        }
    }

    public record Status(int engineCount, long loadedChunks, int tectonicLimit,
                         long tectonicPlates, long activeTectonicPlates,
                         long queuedTectonicPlates,
                         long minTectonicUnloadDuration,
                         long maxTectonicUnloadDuration) {
    }
}
