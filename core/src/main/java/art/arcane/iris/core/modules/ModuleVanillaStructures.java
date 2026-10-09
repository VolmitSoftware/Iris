/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.modules;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.NativeStructureStartPlan;
import art.arcane.iris.engine.object.structure.IrisStructureTerrain;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import com.volmit.config.PackSettings;
import com.volmit.host.DiscoveredModule;
import com.volmit.host.VanillaStructureHookRegistration;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.vanilla.VanillaStructureAdjustment;
import com.volmit.vanilla.VanillaStructureContext;
import com.volmit.vanilla.VanillaStructureHook;
import com.volmit.vanilla.VanillaStructureRule;
import com.volmit.vanilla.VanillaStructureSetup;
import com.volmit.vanilla.VanillaStructureStart;
import com.volmit.vanilla.VanillaStructureStartContext;
import com.volmit.world.PackWorld;
import com.volmit.world.TerrainSampler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * What the pack's modules want for the platform's own structures, asked through their vanilla structure hooks in module
 * id order: per structure in one engine's world the combined rule ({@link VanillaStructureRule#and}) and adjustment
 * ({@link VanillaStructureAdjustment#then}), kept per engine and pack snapshot so a hotload or a reload of the pack's
 * resources asks again; per chunk the starts modules place, converted to the host's plans; and per dimension, before a
 * world exists, the combined setup ({@link VanillaStructureSetup#and}). The platform side applies the results on Bukkit
 * and the modded platforms alike.
 */
public final class ModuleVanillaStructures {
    private static final Map<Engine, ModuleVanillaStructures> WORLDS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final int START_TERRAIN_REACH_BLOCKS = 8;

    private final PackResources snapshot;
    private final List<VanillaStructureHookRegistration> hooks;
    private final VanillaStructureContext world;
    private final Map<String, VanillaStructureRule> rules = new ConcurrentHashMap<>();
    private final Map<AdjustedStructure, VanillaStructureAdjustment> adjustments = new ConcurrentHashMap<>();

    private ModuleVanillaStructures(Engine engine, PackResources snapshot) {
        this.snapshot = snapshot;
        this.hooks = hooksOf(engine.getData());
        this.world = new World(snapshot, new IrisPackSettings(engine));
    }

    /** The combined rule of the pack's modules for the platform structure {@code structureKey} in {@code engine}'s world. */
    public static VanillaStructureRule ruleFor(Engine engine, String structureKey) {
        return of(engine).rule(structureKey);
    }

    /**
     * The combined adjustment of the pack's modules for the platform's own starts of {@code structureKey} in
     * {@code engine}'s world; {@code underground} is whether the platform generates it in an underground step.
     */
    public static VanillaStructureAdjustment adjustmentFor(Engine engine, String structureKey, boolean underground) {
        return of(engine).adjustment(structureKey, underground);
    }

    /**
     * The starts the pack's modules place in chunk {@code chunkX, chunkZ} of {@code engine}'s world, in module id order,
     * as the host's plans: world heights, Iris's fit and jigsaw classes.
     */
    public static List<NativeStructureStartPlan> startsAt(Engine engine, int chunkX, int chunkZ) {
        StartContext context = new StartContext(engine.getGeneration().world(),
                new IrisTerrainSampler(engine, new PackResourceIds(engine.getData().getDataFolder()),
                        chunkX << 4, chunkZ << 4, START_TERRAIN_REACH_BLOCKS));
        List<NativeStructureStartPlan> plans = new ArrayList<>();
        for (VanillaStructureHookRegistration registration : hooksOf(engine.getData())) {
            for (VanillaStructureStart start : ask(registration, "placing starts in chunk " + chunkX + "," + chunkZ,
                    hook -> hook.starts(chunkX, chunkZ, context))) {
                plans.add(plan(registration.module(), start, chunkX, chunkZ, engine.getMinHeight()));
            }
        }
        return List.copyOf(plans);
    }

    /** The combined setup of the pack's modules for {@code dimension} of the pack {@code data} serves, before any world exists. */
    public static VanillaStructureSetup setupFor(IrisData data, IrisDimension dimension) {
        VanillaStructureContext context = new World(data.getPackResources(), new DimensionSettings(data, dimension));
        VanillaStructureSetup combined = VanillaStructureSetup.UNCHANGED;
        for (VanillaStructureHookRegistration registration : hooksOf(data)) {
            combined = combined.and(ask(registration, "setting up the vanilla structures of dimension " + dimension.getLoadKey(),
                    hook -> hook.setup(context)));
        }
        return combined;
    }

    private static List<VanillaStructureHookRegistration> hooksOf(IrisData data) {
        return data.getModules().pack().catalog().vanillaStructureHooks();
    }

    private static ModuleVanillaStructures of(Engine engine) {
        PackResources current = engine.getData().getPackResources();
        ModuleVanillaStructures known = WORLDS.get(engine);
        if (known != null && known.snapshot == current) {
            return known;
        }
        ModuleVanillaStructures fresh = new ModuleVanillaStructures(engine, current);
        WORLDS.put(engine, fresh);
        return fresh;
    }

    private VanillaStructureRule rule(String structureKey) {
        return rules.computeIfAbsent(structureKey, this::askRules);
    }

    private VanillaStructureAdjustment adjustment(String structureKey, boolean underground) {
        return adjustments.computeIfAbsent(new AdjustedStructure(structureKey, underground), this::askAdjustments);
    }

    /** A key that is no resource id names no structure a module could decide over. */
    private VanillaStructureRule askRules(String structureKey) {
        Optional<ResourceId> structure = structureId(structureKey);
        if (structure.isEmpty()) {
            return VanillaStructureRule.GENERATE;
        }
        VanillaStructureRule combined = VanillaStructureRule.GENERATE;
        for (VanillaStructureHookRegistration registration : hooks) {
            VanillaStructureRule rule = ask(registration, "deciding over vanilla structure " + structure.get(),
                    hook -> hook.rule(structure.get(), world));
            combined = combined.and(rule);
        }
        return combined;
    }

    private VanillaStructureAdjustment askAdjustments(AdjustedStructure adjusted) {
        Optional<ResourceId> structure = structureId(adjusted.key());
        if (structure.isEmpty()) {
            return VanillaStructureAdjustment.NONE;
        }
        VanillaStructureAdjustment combined = VanillaStructureAdjustment.NONE;
        for (VanillaStructureHookRegistration registration : hooks) {
            VanillaStructureAdjustment adjustment = ask(registration, "adjusting vanilla structure " + structure.get(),
                    hook -> hook.adjustment(structure.get(), adjusted.underground(), world));
            combined = combined.then(adjustment);
        }
        return combined;
    }

    private static Optional<ResourceId> structureId(String structureKey) {
        try {
            return Optional.of(ResourceId.parse(structureKey));
        } catch (IllegalArgumentException notAnId) {
            return Optional.empty();
        }
    }

    /** One hook's answer to {@code question}; a hook that throws or answers nothing fails naming its module. */
    private static <T> T ask(VanillaStructureHookRegistration registration, String question,
                             Function<VanillaStructureHook, T> answer) {
        T result;
        try {
            result = answer.apply(registration.hook());
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Module " + registration.module() + " failed " + question + ": " + failure, failure);
        }
        if (result == null) {
            throw new IllegalStateException("Module " + registration.module() + " gave no answer " + question);
        }
        return result;
    }

    private static NativeStructureStartPlan plan(DiscoveredModule module, VanillaStructureStart start, int chunkX, int chunkZ,
                                                 int minHeight) {
        if (start.chunkX() != chunkX || start.chunkZ() != chunkZ) {
            throw new IllegalStateException("Module " + module + " started " + start.structure() + " in chunk "
                    + start.chunkX() + "," + start.chunkZ() + " when asked for chunk " + chunkX + "," + chunkZ);
        }
        return new NativeStructureStartPlan(
                start.structure().toString(),
                start.chunkX(),
                start.chunkZ(),
                start.y() + minHeight,
                start.isUnderground(),
                start.replacesPlatformStart(),
                start.identity(),
                start.jigsaw().map(IrisValues::jigsawConfiguration).orElse(null),
                start.fit().stilts().map(IrisValues::structureStilts).orElse(null),
                start.fit().terrain().map(IrisValues::structureTerrain).orElseGet(IrisStructureTerrain::new));
    }

    /** The world the hooks decide for: the engine's pack snapshot and the settings its dimension reaches. */
    private record World(PackResources resources, PackSettings settings) implements VanillaStructureContext {
    }

    private record StartContext(PackWorld world, TerrainSampler terrain) implements VanillaStructureStartContext {
    }

    private record AdjustedStructure(String key, boolean underground) {
    }
}
