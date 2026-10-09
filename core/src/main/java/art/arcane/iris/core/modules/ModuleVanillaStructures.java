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

import art.arcane.iris.engine.framework.Engine;
import com.volmit.config.PackSettings;
import com.volmit.host.VanillaStructureHookRegistration;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.vanilla.VanillaStructureContext;
import com.volmit.vanilla.VanillaStructureRule;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the pack's modules want for each of the platform's own structures in one engine's world: every vanilla structure
 * hook of the pack asked once per structure, the rules combined ({@link VanillaStructureRule#and}). Kept per engine and
 * pack snapshot, so a hotload or a reload of the pack's resources asks the hooks again. The platform side
 * ({@code NativeStructureGenerationPolicy}) applies the result on Bukkit and the modded platforms alike.
 */
public final class ModuleVanillaStructures {
    private static final Map<Engine, ModuleVanillaStructures> WORLDS = Collections.synchronizedMap(new WeakHashMap<>());

    private final PackResources snapshot;
    private final List<VanillaStructureHookRegistration> hooks;
    private final VanillaStructureContext world;
    private final Map<String, VanillaStructureRule> rules = new ConcurrentHashMap<>();

    private ModuleVanillaStructures(Engine engine, PackResources snapshot) {
        this.snapshot = snapshot;
        this.hooks = engine.getData().getModules().pack().catalog().vanillaStructureHooks();
        this.world = new World(snapshot, new IrisPackSettings(engine));
    }

    /** The combined rule of the pack's modules for the platform structure {@code structureKey} in {@code engine}'s world. */
    public static VanillaStructureRule ruleFor(Engine engine, String structureKey) {
        return of(engine).rule(structureKey);
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
        return rules.computeIfAbsent(structureKey, this::askHooks);
    }

    /** A key that is no resource id names no structure a module could decide over. */
    private VanillaStructureRule askHooks(String structureKey) {
        ResourceId structure;
        try {
            structure = ResourceId.parse(structureKey);
        } catch (IllegalArgumentException notAnId) {
            return VanillaStructureRule.GENERATE;
        }
        VanillaStructureRule combined = VanillaStructureRule.GENERATE;
        for (VanillaStructureHookRegistration registration : hooks) {
            combined = combined.and(ask(registration, structure));
        }
        return combined;
    }

    private VanillaStructureRule ask(VanillaStructureHookRegistration registration, ResourceId structure) {
        VanillaStructureRule rule;
        try {
            rule = registration.hook().rule(structure, world);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Module " + registration.module() + " failed deciding over vanilla structure "
                    + structure + ": " + failure, failure);
        }
        if (rule == null) {
            throw new IllegalStateException("Module " + registration.module() + " gave no rule for vanilla structure " + structure);
        }
        return rule;
    }

    /** The world the hooks decide for: the engine's pack snapshot and the settings its dimension reaches. */
    private record World(PackResources resources, PackSettings settings) implements VanillaStructureContext {
    }
}
