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
import art.arcane.iris.engine.framework.NativeStructureGenerationPolicy;
import art.arcane.iris.engine.framework.NativeStructureGenerationStatus;
import art.arcane.iris.engine.framework.StructureReachability;
import com.volmit.config.Reference;
import com.volmit.tooling.Argument;
import com.volmit.tooling.CommandContext;
import com.volmit.tooling.Sender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * {@code /iris module structure verify}: for every structure the platform registers and every structure the pack's modules place,
 * whether it can generate in the world the sender stands in, searching around the world origin for those the pack places.
 * Native eligibility is decided without the platform's blocking locate.
 */
final class StructureVerification {
    private static final long DEFAULT_RADIUS_CHUNKS = 48L;
    private static final long MAX_RADIUS_CHUNKS = 1000L;
    private static final int ORIGIN_BLOCK = 0;

    static final Argument<Long> RADIUS = Argument.integer("radius", 1, MAX_RADIUS_CHUNKS)
            .orElse(DEFAULT_RADIUS_CHUNKS)
            .describe("Search radius in chunks around the world origin (larger is much slower)");

    private final Engine engine;
    private final CommandContext context;
    private final int radiusChunks;
    private final List<String> lines = new ArrayList<>();
    private int located;
    private int nativeEligible;
    private int disabled;
    private int unreachable;
    private int searchLimited;
    private int errors;

    private StructureVerification(Engine engine, CommandContext context, int radiusChunks) {
        this.engine = engine;
        this.context = context;
        this.radiusChunks = radiusChunks;
    }

    static void run(CommandContext context) {
        Sender sender = context.sender();
        Engine engine = context.world().flatMap(world -> world.extension(Engine.class)).orElse(null);
        if (engine == null) {
            sender.error("Stand in a world generated with this pack: verify reads the running world");
            return;
        }
        int radiusChunks = Math.toIntExact(context.get(RADIUS));
        sender.info("Verifying structures within " + radiusChunks + " chunks of the world origin...");
        new StructureVerification(engine, context, radiusChunks).report();
    }

    private void report() {
        List<String> structureKeys = structureKeys();
        if (!verifyAll(structureKeys)) {
            return;
        }
        lines.add("Structure verify: " + located + " Iris placement plans found, " + nativeEligible + " native structures eligible, "
                + disabled + " disabled by policy, " + unreachable + " biome-unreachable, " + searchLimited
                + " density searches safety-limited, " + errors + " errors. Native eligibility is checked without running blocking live locates.");
        lines.forEach(context.sender()::info);
    }

    private List<String> structureKeys() {
        Set<String> keys = new TreeSet<>();
        context.platform().ifPresent(registries -> registries.ids(Reference.STRUCTURE).forEach(id -> keys.add(id.toString())));
        List<String> ordered = new ArrayList<>(keys);
        for (String placed : ModuleServices.placedStructures(engine)) {
            if (!keys.contains(placed)) {
                ordered.add(placed);
            }
        }
        return ordered;
    }

    private boolean verifyAll(List<String> structureKeys) {
        Set<String> reachable;
        try {
            reachable = reachableNativeKeys(structureKeys);
        } catch (RuntimeException failure) {
            context.pack().log().error("Could not resolve native biome reachability for verification", failure);
            context.sender().error("Structure verification could not resolve native biome reachability.");
            return false;
        }
        for (String key : structureKeys) {
            verify(key, reachable);
        }
        return true;
    }

    private Set<String> reachableNativeKeys(List<String> structureKeys) {
        boolean needsReachability = structureKeys.stream().anyMatch(key -> !ModuleServices.placesStructure(engine, key)
                && NativeStructureGenerationPolicy.resolve(engine, key) == NativeStructureGenerationStatus.GENERATE_NATIVE);
        return needsReachability ? StructureReachability.reachableKeys(engine) : Set.of();
    }

    private void verify(String key, Set<String> reachable) {
        if (ModuleServices.placesStructure(engine, key)) {
            locatePlaced(key);
            return;
        }
        if (NativeStructureGenerationPolicy.resolve(engine, key) != NativeStructureGenerationStatus.GENERATE_NATIVE) {
            disabled++;
            lines.add("[disabled] " + key);
            return;
        }
        if (!reachable.contains(key.toLowerCase(Locale.ROOT))) {
            unreachable++;
            List<String> missing = StructureReachability.missingBiomeKeys(engine, key);
            lines.add("[unreachable] " + key + (missing.isEmpty() ? "" : " needs " + String.join("/", missing)));
            return;
        }
        nativeEligible++;
        lines.add("[native-eligible] " + key);
    }

    private void locatePlaced(String key) {
        try {
            ModuleServices.StructureSearch result = ModuleServices.locateStructure(engine, key, ORIGIN_BLOCK, ORIGIN_BLOCK, radiusChunks);
            if (result.outcome() == ModuleServices.StructureSearch.Outcome.SEARCH_LIMIT_REACHED) {
                searchLimited++;
                lines.add("[iris-search-limit] " + key + ": unable to complete the density search before its safety limit was reached");
                return;
            }
            if (!result.found()) {
                lines.add("[iris-not-found] " + key);
                return;
            }
            located++;
            lines.add("[iris-planned] " + key + " @ " + result.x() + "," + result.y() + "," + result.z());
        } catch (RuntimeException failure) {
            errors++;
            lines.add("[error] " + key + ": " + failure.getClass().getSimpleName());
            context.pack().log().error("Could not verify Iris-placed structure '" + key + "'", failure);
        }
    }
}
