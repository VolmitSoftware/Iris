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
import art.arcane.iris.engine.framework.NativeJigsawOverrides;
import art.arcane.iris.engine.framework.NativeStartMemo;
import art.arcane.iris.engine.framework.NativeStartPlacement;
import art.arcane.iris.engine.framework.NativeStructureReach;
import art.arcane.iris.engine.framework.NativeStructureStartPlan;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import com.volmit.config.PackSettings;
import com.volmit.host.DiscoveredModule;
import com.volmit.host.ServiceRegistration;
import com.volmit.geometry.BlockBox;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.service.VanillaStructures;
import com.volmit.vanilla.JigsawOverrides;
import com.volmit.vanilla.PlatformStart;
import com.volmit.vanilla.StartMemo;
import com.volmit.vanilla.StartReach;
import com.volmit.vanilla.VanillaStructureContext;
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
import java.util.OptionalInt;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * What the pack's modules want for the platform's own structures, asked through their {@link VanillaStructures}
 * providers in module id order: per structure in one engine's world the combined rule ({@link VanillaStructureRule#and}),
 * kept per engine and pack snapshot so a hotload or a reload of the pack's resources asks again; per chunk the starts
 * modules place, converted to the host's plans; per platform start the shift every module gives it and every module's
 * reach; and per dimension, before a world exists, the combined setup ({@link VanillaStructureSetup#and}). The platform
 * side applies the results on Bukkit and the modded platforms alike.
 */
public final class ModuleVanillaStructures {
    private static final Map<Engine, ModuleVanillaStructures> WORLDS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final int START_TERRAIN_REACH_BLOCKS = 8;

    private final PackResources snapshot;
    private final List<ServiceRegistration> providers;
    private final VanillaStructureContext world;
    private final Map<String, VanillaStructureRule> rules = new ConcurrentHashMap<>();

    private ModuleVanillaStructures(Engine engine, PackResources snapshot) {
        this.snapshot = snapshot;
        this.providers = providersOf(engine.getData());
        this.world = new World(snapshot, new IrisPackSettings(engine));
    }

    /** The combined rule of the pack's modules for the platform structure {@code structureKey} in {@code engine}'s world. */
    public static VanillaStructureRule ruleFor(Engine engine, String structureKey) {
        return of(engine).rule(structureKey);
    }

    /**
     * The starts the pack's modules place in chunk {@code chunkX, chunkZ} of {@code engine}'s world, in module id order,
     * as the host's plans: world heights, the platform's jigsaw changes and the modules' memos.
     */
    public static List<NativeStructureStartPlan> startsAt(Engine engine, int chunkX, int chunkZ) {
        TerrainSampler terrain = new IrisTerrainSampler(engine, new PackResourceIds(engine.getData().getDataFolder()),
                chunkX << 4, chunkZ << 4, START_TERRAIN_REACH_BLOCKS);
        List<NativeStructureStartPlan> plans = new ArrayList<>();
        for (ServiceRegistration registration : providersOf(engine.getData())) {
            StartContext context = new StartContext(registration.scope(engine.getGeneration().world()), terrain);
            for (VanillaStructureStart start : ask(registration, "placing starts in chunk " + chunkX + "," + chunkZ,
                    provider -> provider.starts(chunkX, chunkZ, context))) {
                plans.add(plan(registration.module(), start, chunkX, chunkZ, engine.getMinHeight()));
            }
        }
        return List.copyOf(plans);
    }

    /** The shifts every module of the pack gives the platform's own start {@code start}, added up. */
    public static int shiftOf(Engine engine, NativeStartPlacement start) {
        ModuleVanillaStructures structures = of(engine);
        Optional<ResourceId> structure = structureId(start.structureKey());
        if (structure.isEmpty()) {
            return 0;
        }
        PlatformStart platformStart = new HostPlatformStart(structure.get(), start);
        int shift = 0;
        for (ServiceRegistration registration : structures.providers) {
            shift = Math.addExact(shift, ask(registration, "moving vanilla structure " + structure.get(),
                    provider -> provider.shift(platformStart, structures.world)));
        }
        return shift;
    }

    /** Every module's reach around a start of {@code structureKey}, placed by a module with {@code memo} or by the platform. */
    public static List<NativeStructureReach> reachesOf(Engine engine, String structureKey, boolean underground,
                                                       NativeStartMemo memo) {
        ModuleVanillaStructures structures = of(engine);
        Optional<ResourceId> structure = structureId(structureKey);
        if (structure.isEmpty()) {
            return List.of(NativeStructureReach.PLATFORM);
        }
        Optional<StartMemo> startMemo = Optional.ofNullable(memo).map(note -> new StartMemo(note.terrain(), note.foundation()));
        List<NativeStructureReach> reaches = new ArrayList<>(structures.providers.size());
        for (ServiceRegistration registration : structures.providers) {
            StartReach reach = ask(registration, "reaching around vanilla structure " + structure.get(),
                    provider -> provider.reach(structure.get(), underground, startMemo, structures.world));
            reaches.add(new NativeStructureReach(reach.platform(), reach.blocks()));
        }
        return reaches.isEmpty() ? List.of(NativeStructureReach.PLATFORM) : List.copyOf(reaches);
    }

    /** The combined setup of the pack's modules for {@code dimension} of the pack {@code data} serves, before any world exists. */
    public static VanillaStructureSetup setupFor(IrisData data, IrisDimension dimension) {
        VanillaStructureContext context = new World(data.getPackResources(), new DimensionSettings(data, dimension));
        VanillaStructureSetup combined = VanillaStructureSetup.UNCHANGED;
        for (ServiceRegistration registration : providersOf(data)) {
            combined = combined.and(ask(registration, "setting up the vanilla structures of dimension " + dimension.getLoadKey(),
                    provider -> provider.setup(context)));
        }
        return combined;
    }

    /** The pack's {@link VanillaStructures} providers with the modules that registered them, in module id order. */
    static List<ServiceRegistration> providersOf(IrisData data) {
        return data.getModules().pack().catalog().registrations(VanillaStructures.class);
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

    /** A key that is no resource id names no structure a module could decide over. */
    private VanillaStructureRule askRules(String structureKey) {
        Optional<ResourceId> structure = structureId(structureKey);
        if (structure.isEmpty()) {
            return VanillaStructureRule.GENERATE;
        }
        VanillaStructureRule combined = VanillaStructureRule.GENERATE;
        for (ServiceRegistration registration : providers) {
            VanillaStructureRule rule = ask(registration, "deciding over vanilla structure " + structure.get(),
                    provider -> provider.rule(structure.get(), world));
            combined = combined.and(rule);
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

    /** One provider's answer to {@code question}; a provider that throws or answers nothing fails naming its module. */
    static <T> T ask(ServiceRegistration registration, String question, Function<VanillaStructures, T> answer) {
        T result;
        try {
            result = answer.apply((VanillaStructures) registration.provider());
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
                start.jigsaw().map(ModuleVanillaStructures::jigsaw).orElse(null),
                start.memo().map(memo -> new NativeStartMemo(memo.terrain(), memo.foundation())).orElse(null));
    }

    private static NativeJigsawOverrides jigsaw(JigsawOverrides overrides) {
        return new NativeJigsawOverrides(
                overrides.startPool(),
                overrides.startJigsawName(),
                boxed(overrides.maxDepth()),
                boxed(overrides.maxDistanceHorizontal()),
                boxed(overrides.maxDistanceVertical()),
                overrides.useExpansionHack().orElse(null),
                overrides.projectStartToHeightmap().name(),
                boxed(overrides.dimensionPaddingBottom()),
                boxed(overrides.dimensionPaddingTop()),
                overrides.liquidSettings().name());
    }

    private static Integer boxed(OptionalInt value) {
        return value.isPresent() ? value.getAsInt() : null;
    }

    /** The world the providers decide for: the engine's pack snapshot and the settings its dimension reaches. */
    private record World(PackResources resources, PackSettings settings) implements VanillaStructureContext {
    }

    private record StartContext(PackWorld world, TerrainSampler terrain) implements VanillaStructureStartContext {
    }

    /** A platform start in the API's heights: above the dimension's bottom, maxima exclusive. */
    private record HostPlatformStart(ResourceId structure, NativeStartPlacement start) implements PlatformStart {
        @Override
        public int chunkX() {
            return start.chunkX();
        }

        @Override
        public int chunkZ() {
            return start.chunkZ();
        }

        @Override
        public BlockBox bounds() {
            int bottom = start.bottomY();
            return new BlockBox(start.minX(), start.minY() - bottom, start.minZ(),
                    start.maxX() + 1, start.maxY() + 1 - bottom, start.maxZ() + 1);
        }

        @Override
        public boolean underground() {
            return start.underground();
        }

        @Override
        public boolean aligned() {
            return start.aligned();
        }

        @Override
        public int minWorldY() {
            return start.bottomY();
        }

        @Override
        public int surfaceY(int x, int z) {
            return start.surfaceY().applyAsInt(x, z) - start.bottomY();
        }
    }
}
