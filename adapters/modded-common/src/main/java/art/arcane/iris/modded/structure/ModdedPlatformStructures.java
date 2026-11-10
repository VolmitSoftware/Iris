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

package art.arcane.iris.modded.structure;

import art.arcane.iris.modded.ModdedEngineBootstrap;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.JigsawStructureInfo;
import com.volmit.tooling.PlatformStructures;
import com.volmit.tooling.Sender;
import com.volmit.tooling.StructureAccessException;
import com.volmit.tooling.StructureTemplate;
import com.volmit.tooling.TemplatePool;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;

/**
 * The structures of a modded server as the tools read them: templates through the server's template manager, and
 * structures, structure sets and template pools through its registries. Vanilla classes, so nothing is reflected.
 */
public final class ModdedPlatformStructures implements PlatformStructures {
    private static final String TEMPLATE_FOLDER = "structure";
    private static final String TEMPLATE_FILE_SUFFIX = ".nbt";
    private static final String MAX_DEPTH_FIELD = "maxDepth";
    private static final String MAX_DISTANCE_FIELD = "maxDistanceFromCenter";

    @Override
    public List<ResourceId> templates() {
        MinecraftServer server = server();
        List<Identifier> found = new ArrayList<>();
        for (Identifier file : server.getResourceManager()
                .listResources(TEMPLATE_FOLDER, location -> location.getPath().endsWith(TEMPLATE_FILE_SUFFIX)).keySet()) {
            String path = file.getPath().substring(TEMPLATE_FOLDER.length() + 1,
                    file.getPath().length() - TEMPLATE_FILE_SUFFIX.length());
            if (!path.isEmpty()) {
                found.add(Identifier.fromNamespaceAndPath(file.getNamespace(), path));
            }
        }
        return idsOf(found);
    }

    @Override
    public Optional<StructureTemplate> template(ResourceId id) {
        return server().getStructureManager().get(identifierOf(id)).flatMap(TemplateReading::read);
    }

    @Override
    public List<ResourceId> structures() {
        return idsOf(registries().lookupOrThrow(Registries.STRUCTURE).keySet());
    }

    @Override
    public List<ResourceId> structureSets() {
        return idsOf(registries().lookupOrThrow(Registries.STRUCTURE_SET).keySet());
    }

    @Override
    public Optional<JigsawStructureInfo> jigsaw(ResourceId structureKey) {
        RegistryAccess registries = registries();
        Structure structure = registries.lookupOrThrow(Registries.STRUCTURE).getValue(identifierOf(structureKey));
        if (!(structure instanceof JigsawStructure jigsaw)) {
            return Optional.empty();
        }
        Identifier startPool = poolKeyOf(registries.lookupOrThrow(Registries.TEMPLATE_POOL), jigsaw.getStartPool())
                .orElseThrow(() -> new StructureAccessException("Could not resolve the start pool key for " + structureKey, false));
        return Optional.of(jigsawInfoOf(structureKey, jigsaw, startPool, ModdedEngineBootstrap.loader().minecraftVersion()));
    }

    static JigsawStructureInfo jigsawInfoOf(ResourceId structureKey, JigsawStructure jigsaw, Identifier startPool, String gameVersion) {
        return new JigsawStructureInfo(
                ResourceId.parse(startPool.toString()),
                PrivateFields.readInt(jigsaw, MAX_DEPTH_FIELD),
                PrivateFields.read(jigsaw, MAX_DISTANCE_FIELD, JigsawStructure.MaxDistance.class).horizontal(),
                gameVersion,
                shuffleSeedOf(structureKey));
    }

    /** The hash a key has on Bukkit, so a structure shuffles its pool the same on every platform. */
    static long shuffleSeedOf(ResourceId structureKey) {
        return 31 * structureKey.namespace().hashCode() + structureKey.path().hashCode();
    }

    @Override
    public Optional<TemplatePool> pool(ResourceId poolKey) {
        MinecraftServer server = server();
        Registry<StructureTemplatePool> registry = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        StructureTemplatePool pool = registry.getValue(identifierOf(poolKey));
        if (pool == null) {
            return Optional.empty();
        }
        Optional<ResourceId> fallback = Optional.empty();
        Optional<String> fallbackFailure = Optional.empty();
        try {
            fallback = poolKeyOf(registry, pool.getFallback()).map(key -> ResourceId.parse(key.toString()));
        } catch (RuntimeException failure) {
            fallbackFailure = Optional.of(PoolReading.failureDetail(failure));
        }
        List<TemplatePool.Member> members = new ArrayList<>();
        pool.getTemplates().forEach(pair -> members.add(PoolReading.memberOf(pair, server.getStructureManager())));
        return Optional.of(new TemplatePool(members, fallback, fallbackFailure));
    }

    private static Optional<Identifier> poolKeyOf(Registry<StructureTemplatePool> registry, Holder<StructureTemplatePool> holder) {
        Optional<ResourceKey<StructureTemplatePool>> key = holder.unwrapKey();
        if (key.isPresent()) {
            return Optional.of(key.get().identifier());
        }
        return Optional.ofNullable(registry.getKey(holder.value()));
    }

    @Override
    public boolean supportsCapture() {
        return true;
    }

    @Override
    public Optional<CaptureArea> openCaptureArea(Sender sender) {
        return ModdedCaptureArea.open(server(), sender);
    }

    private static MinecraftServer server() {
        MinecraftServer server = ModdedEngineBootstrap.currentServer();
        if (server == null) {
            throw new StructureAccessException("No server is running", true);
        }
        return server;
    }

    private static RegistryAccess registries() {
        return server().registryAccess();
    }

    private static Identifier identifierOf(ResourceId id) {
        return Identifier.fromNamespaceAndPath(id.namespace(), id.path());
    }

    private static List<ResourceId> idsOf(Collection<Identifier> keys) {
        TreeSet<ResourceId> ids = new TreeSet<>();
        for (Identifier key : keys) {
            try {
                ids.add(ResourceId.parse(key.toString().toLowerCase(Locale.ROOT)));
            } catch (IllegalArgumentException invalidKey) {
                continue;
            }
        }
        return new ArrayList<>(ids);
    }
}
