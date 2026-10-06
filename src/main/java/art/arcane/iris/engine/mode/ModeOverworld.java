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

package art.arcane.iris.engine.mode;

import art.arcane.iris.engine.actuator.IrisBiomeActuator;
import art.arcane.iris.engine.actuator.IrisDecorantActuator;
import art.arcane.iris.engine.actuator.IrisTerrainNormalActuator;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineMode;
import art.arcane.iris.engine.framework.EnginePlatformHooks;
import art.arcane.iris.engine.framework.EngineStage;
import art.arcane.iris.engine.framework.IrisEngineMode;
import art.arcane.iris.engine.modifier.IrisCarveModifier;
import art.arcane.iris.engine.modifier.IrisCustomModifier;
import art.arcane.iris.engine.modifier.IrisDepositModifier;
import art.arcane.iris.engine.modifier.IrisFloatingChildBiomeModifier;
import art.arcane.iris.engine.modifier.IrisPerfectionModifier;
import art.arcane.iris.engine.modifier.IrisPostModifier;
import art.arcane.iris.spi.IrisLogging;

import java.util.concurrent.atomic.AtomicLong;

public class ModeOverworld extends IrisEngineMode implements EngineMode {
    private static final AtomicLong lastMaintenanceBypassLog = new AtomicLong(0L);
    private final EnginePlatformHooks platformHooks;

    public ModeOverworld(Engine engine) {
        super(engine);
        platformHooks = engine.getPlatformHooks();
        IrisTerrainNormalActuator terrain = new IrisTerrainNormalActuator(getEngine());
        IrisBiomeActuator biome = new IrisBiomeActuator(getEngine());
        IrisDecorantActuator decorant = new IrisDecorantActuator(getEngine());
        IrisCarveModifier cave = new IrisCarveModifier(getEngine());
        IrisPostModifier post = new IrisPostModifier(getEngine());
        IrisDepositModifier deposit = new IrisDepositModifier(getEngine());
        IrisPerfectionModifier perfection = new IrisPerfectionModifier(getEngine());
        IrisCustomModifier custom = new IrisCustomModifier(getEngine());
        IrisFloatingChildBiomeModifier floatingChildBiomes = new IrisFloatingChildBiomeModifier(getEngine());
        EngineStage sBiome = (x, z, k, p, m, c) -> biome.actuate(x, z, p, m, c);
        EngineStage sGenMatter = (x, z, k, p, m, c) -> {
            if (shouldBypassMantleStages()) {
                return;
            }
            generateMatter(
                    x >> 4,
                    z >> 4,
                    m || getEngine().isStudio(),
                    c);
        };
        EngineStage sTerrain = (x, z, k, p, m, c) -> terrain.actuate(x, z, k, m, c);
        EngineStage sDecorant = (x, z, k, p, m, c) -> decorant.actuate(x, z, k, m, c);
        EngineStage sCave = (x, z, k, p, m, c) -> {
            if (shouldBypassMantleStages()) {
                return;
            }
            cave.modify(x >> 4, z >> 4, k, m, c);
        };
        EngineStage sDeposit = (x, z, k, p, m, c) -> {
            if (shouldBypassMantleStages()) {
                return;
            }
            deposit.modify(x, z, k, m, c);
        };
        EngineStage sPost = (x, z, k, p, m, c) -> {
            if (shouldBypassMantleStages()) {
                return;
            }
            post.modify(x, z, k, m, c);
        };
        EngineStage sInsertMatter = (x, z, K, p, m, c) -> {
            if (shouldBypassMantleStages()) {
                return;
            }
            getMantle().insertMatter(x >> 4, z >> 4, K, m);
        };
        EngineStage sFloatingTerrainSolid = (x, z, k, p, m, c) -> floatingChildBiomes.modify(x, z, k, m, c);
        EngineStage sFloatingDecorate = (x, z, k, p, m, c) -> floatingChildBiomes.decorateColumns(x, z, k, m, c);
        EngineStage sPerfection = (x, z, k, p, m, c) -> perfection.modify(x, z, k, m, c);
        EngineStage sCustom = (x, z, k, p, m, c) -> {
            if (shouldBypassMantleStages()) {
                return;
            }
            custom.modify(x, z, k, m, c);
        };

        registerStage(burst(
                sBiome,
                sGenMatter,
                sTerrain
        ));
        registerStage(sCave);
        registerStage(sPost);
        registerStage(sFloatingTerrainSolid);
        // Never burst these three: all of them write the same block hunk (and sDecorant reads
        // the surface sInsertMatter writes), so parallel order is scheduler-dependent. The
        // production path already runs them inline in this order; sequential registration
        // makes studio (the only multicore path) match production and the goldenhash baseline.
        registerStage(sDeposit);
        registerStage(sInsertMatter);
        registerStage(sDecorant);
        registerStage(sFloatingDecorate);
        registerStage(sPerfection);
        registerStage(sCustom);
    }

    private boolean shouldBypassMantleStages() {
        boolean active = platformHooks.shouldBypassMantleStages(getEngine());
        if (active) {
            long now = System.currentTimeMillis();
            long last = lastMaintenanceBypassLog.get();
            if (now - last >= 5000L && lastMaintenanceBypassLog.compareAndSet(last, now)) {
                IrisLogging.info("Maintenance regen bypass: skipping mantle-backed overworld stages for Folia safety.");
            }
        }
        return active;
    }
}
