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

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.engine.mantle.IrisMantleComponent;
import com.volmit.iris.engine.mantle.MantleComponent;
import com.volmit.iris.engine.mantle.components.MantleFeatureComponent;
import com.volmit.iris.engine.mantle.components.MantleJigsawComponent;
import com.volmit.iris.engine.mantle.components.MantleObjectComponent;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.mantle.Mantle;
import lombok.Data;

import java.io.File;
import java.util.concurrent.CompletableFuture;

@Data
public class IrisEngineMantle implements EngineMantle {
    private final Engine engine;
    private final Mantle mantle;
    private final KList<MantleComponent> components;
    private final CompletableFuture<Integer> radius;

    public IrisEngineMantle(Engine engine) {
        this.engine = engine;
        this.mantle = new Mantle(new File(engine.getWorld().worldFolder(), "mantle"), engine.getTarget().getHeight());
        radius = CompletableFuture.completedFuture(0); // TODO
        components = new KList<>();
        registerComponent(new MantleFeatureComponent(this));
        registerComponent(new MantleJigsawComponent(this));
        registerComponent(new MantleObjectComponent(this));
    }

    @Override
    public void registerComponent(MantleComponent c) {
        components.add(c);
    }
}
