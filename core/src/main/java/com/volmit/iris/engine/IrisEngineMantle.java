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

package com.volmit.iris.engine;

import com.volmit.iris.core.nms.container.Pair;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.engine.mantle.MantleComponent;
import com.volmit.iris.engine.mantle.components.MantleCarvingComponent;
import com.volmit.iris.engine.mantle.components.MantleFluidBodyComponent;
import com.volmit.iris.engine.mantle.components.MantleJigsawComponent;
import com.volmit.iris.engine.mantle.components.MantleObjectComponent;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.mantle.flag.MantleFlag;
import lombok.*;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@EqualsAndHashCode(exclude = "engine")
@ToString(exclude = "engine")
public class IrisEngineMantle implements EngineMantle {
    private final Engine engine;
    private final Mantle mantle;
    @Getter(AccessLevel.NONE)
    private final KMap<Integer, KList<MantleComponent>> components;
    private final KMap<MantleFlag, MantleComponent> registeredComponents = new KMap<>();
    private final AtomicCache<List<Pair<List<MantleComponent>, Integer>>> componentsCache = new AtomicCache<>();
    private final AtomicCache<Set<MantleFlag>> disabledFlags = new AtomicCache<>();
    private final MantleObjectComponent object;
    private final MantleJigsawComponent jigsaw;

    public IrisEngineMantle(Engine engine) {
        this.engine = engine;
        this.mantle = new Mantle(new File(engine.getWorld().worldFolder(), "mantle"), engine.getTarget().getHeight());
        components = new KMap<>();
        registerComponent(new MantleCarvingComponent(this));
        registerComponent(new MantleFluidBodyComponent(this));
        jigsaw = new MantleJigsawComponent(this);
        registerComponent(jigsaw);
        object = new MantleObjectComponent(this);
        registerComponent(object);
    }

    @Override
    public int getRadius() {
        if (components.isEmpty()) return 0;
        return getComponents().getFirst().getB();
    }

    @Override
    public int getRealRadius() {
        if (components.isEmpty()) return 0;
        return getComponents().getLast().getB();
    }

    @Override
    public List<Pair<List<MantleComponent>, Integer>> getComponents() {
        return componentsCache.aquire(() -> {
            var list = components.keySet()
                    .stream()
                    .sorted()
                    .map(components::get)
                    .map(components -> {
                        int radius = components.stream()
                                .mapToInt(MantleComponent::getRadius)
                                .max()
                                .orElse(0);
                        return new Pair<>(List.copyOf(components), radius);
                    })
                    .toList();

            int radius = 0;
            for (var pair : list.reversed()) {
                radius += pair.getB();
                pair.setB(Math.ceilDiv(radius, 16));
            }

            return list;
        });
    }

    @Override
    public Map<MantleFlag, MantleComponent> getRegisteredComponents() {
        return Collections.unmodifiableMap(registeredComponents);
    }

    @Override
    public boolean registerComponent(MantleComponent c) {
        if (registeredComponents.putIfAbsent(c.getFlag(), c) != null) return false;
        c.setEnabled(!getDisabledFlags().contains(c.getFlag()));
        components.computeIfAbsent(c.getPriority(), k -> new KList<>()).add(c);
        componentsCache.reset();
        return true;
    }

    @Override
    public KList<MantleFlag> getComponentFlags() {
        return new KList<>(registeredComponents.keySet());
    }

    @Override
    public void hotload() {
        disabledFlags.reset();
        for (var component : registeredComponents.values()) {
            component.hotload();
            component.setEnabled(!getDisabledFlags().contains(component.getFlag()));
        }
        componentsCache.reset();
    }

    private Set<MantleFlag> getDisabledFlags() {
        return disabledFlags.aquire(() -> Set.copyOf(getDimension().getDisabledComponents()));
    }

    @Override
    public MantleJigsawComponent getJigsawComponent() {
        return jigsaw;
    }

    @Override
    public MantleObjectComponent getObjectComponent() {
        return object;
    }
}
