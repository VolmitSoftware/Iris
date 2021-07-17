/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.engine.actuator.IrisBiomeActuator;
import com.volmit.iris.engine.actuator.IrisDecorantActuator;
import com.volmit.iris.engine.actuator.IrisTerrainIslandActuator;
import com.volmit.iris.engine.actuator.IrisTerrainNormalActuator;
import com.volmit.iris.engine.framework.*;
import com.volmit.iris.engine.modifier.IrisCaveModifier;
import com.volmit.iris.engine.modifier.IrisDepositModifier;
import com.volmit.iris.engine.modifier.IrisPostModifier;
import com.volmit.iris.engine.modifier.IrisRavineModifier;
import com.volmit.iris.util.scheduling.ChronoLatch;
import lombok.Getter;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.util.concurrent.atomic.AtomicBoolean;

public class IrisEngineFramework implements EngineFramework {

    @Getter
    private final Engine engine;

    @Getter
    private final IrisComplex complex;

    @Getter
    final EngineParallaxManager engineParallax;

    @Getter
    private final EngineActuator<BlockData> terrainNormalActuator;

    @Getter
    private final EngineActuator<BlockData> terrainIslandActuator;

    @Getter
    private final EngineActuator<BlockData> decorantActuator;

    @Getter
    private final EngineActuator<Biome> biomeActuator;

    @Getter
    private final EngineModifier<BlockData> depositModifier;

    @Getter
    private final EngineModifier<BlockData> caveModifier;

    @Getter
    private final EngineModifier<BlockData> ravineModifier;

    @Getter
    private final EngineModifier<BlockData> postModifier;

    private final AtomicBoolean cleaning;
    private final ChronoLatch cleanLatch;

    public IrisEngineFramework(Engine engine) {
        this.engine = engine;
        this.complex = new IrisComplex(getEngine());
        this.engineParallax = new IrisEngineParallax(getEngine());
        this.terrainNormalActuator = new IrisTerrainNormalActuator(getEngine());
        this.terrainIslandActuator = new IrisTerrainIslandActuator(getEngine());
        this.decorantActuator = new IrisDecorantActuator(getEngine());
        this.biomeActuator = new IrisBiomeActuator(getEngine());
        this.depositModifier = new IrisDepositModifier(getEngine());
        this.ravineModifier = new IrisRavineModifier(getEngine());
        this.caveModifier = new IrisCaveModifier(engine);
        this.postModifier = new IrisPostModifier(engine);
        cleaning = new AtomicBoolean(false);
        cleanLatch = new ChronoLatch(Math.max(10000, Math.min(IrisSettings.get().getParallax().getParallaxChunkEvictionMS(), IrisSettings.get().getParallax().getParallaxRegionEvictionMS())));
    }

    @Override
    public synchronized void recycle() {
        if (!cleanLatch.flip()) {
            return;
        }

        if (cleaning.get()) {
            cleanLatch.flipDown();
            return;
        }

        cleaning.set(true);

        try {
            getEngine().getParallax().cleanup();
            getData().getObjectLoader().clean();
        } catch (Throwable e) {
            Iris.reportError(e);
            Iris.error("Cleanup failed!");
            e.printStackTrace();
        }

        cleaning.lazySet(false);
    }

    @Override
    public EngineActuator<BlockData> getTerrainActuator() {
        return switch (getEngine().getDimension().getTerrainMode()) {
            case NORMAL -> getTerrainNormalActuator();
            case ISLANDS -> getTerrainIslandActuator();
        };
    }

    @Override
    public void close() {
        getEngineParallax().close();
        getTerrainActuator().close();
        getDecorantActuator().close();
        getBiomeActuator().close();
        getDepositModifier().close();
        getRavineModifier().close();
        getCaveModifier().close();
        getPostModifier().close();
    }
}
