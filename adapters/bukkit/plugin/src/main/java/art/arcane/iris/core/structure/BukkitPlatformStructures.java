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

package art.arcane.iris.core.structure;

import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.util.common.plugin.VolmitSender;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.JigsawStructureInfo;
import com.volmit.tooling.PlatformStructures;
import com.volmit.tooling.Sender;
import com.volmit.tooling.StructureAccessException;
import com.volmit.tooling.StructureTemplate;
import com.volmit.tooling.TemplatePool;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * The structures of a Bukkit server: its registered structures and structure sets through the NMS binding, the
 * jigsaw structures and template pools through the server's registries, the templates through Bukkit's structure
 * manager and the server's resource manager, and structure capture through the NMS binding in a scratch world.
 */
public final class BukkitPlatformStructures implements PlatformStructures {
    private static final String STRUCTURE_TEMPLATE_FOLDER = "structure";
    private static final String TEMPLATE_FILE_SUFFIX = ".nbt";
    private static final String JIGSAW_STRUCTURE_CLASS_SUFFIX = "JigsawStructure";

    @Override
    public List<ResourceId> templates() {
        try {
            return enumerateTemplateKeys();
        } catch (Exception e) {
            throw new StructureAccessException(ServerReflection.failureDetail(e), true, e);
        }
    }

    @Override
    public Optional<StructureTemplate> template(ResourceId id) {
        return BukkitTemplateReader.read(Bukkit.getStructureManager().loadStructure(new NamespacedKey(id.namespace(), id.path())));
    }

    @Override
    public List<ResourceId> structures() {
        return idsOf(INMS.get().getStructureKeys());
    }

    @Override
    public List<ResourceId> structureSets() {
        return idsOf(INMS.get().getStructureSetKeys());
    }

    @Override
    public Optional<JigsawStructureInfo> jigsaw(ResourceId structureKey) {
        Object registryAccess = registryAccess();
        Object structureRegistry;
        Object structure;
        try {
            structureRegistry = ServerReflection.lookupRegistry(registryAccess, "STRUCTURE");
            structure = ServerReflection.registryGet(structureRegistry, structureKey);
        } catch (Throwable e) {
            throw new StructureAccessException("Failed to read jigsaw structure graph: " + e, true, e);
        }
        if (structure == null || !structure.getClass().getName().endsWith(JIGSAW_STRUCTURE_CLASS_SUFFIX)) {
            return Optional.empty();
        }
        Object startPool;
        int maxDepth;
        int maxDistanceFromCenter;
        try {
            startPool = ServerReflection.unwrapHolder(ServerReflection.invoke(structure, "getStartPool"));
            maxDepth = ServerReflection.readIntMember(structure, "maxDepth");
            maxDistanceFromCenter = ServerReflection.readIntMember(structure, "maxDistanceFromCenter");
        } catch (Throwable e) {
            throw new StructureAccessException("Failed to read jigsaw structure graph: " + e, true, e);
        }
        Object templatePoolRegistry = templatePoolRegistry(registryAccess);
        String startPoolKey;
        try {
            startPoolKey = ServerReflection.registryKeyOf(templatePoolRegistry, startPool);
        } catch (Throwable e) {
            throw new StructureAccessException("Could not resolve the start pool key for " + structureKey + ": " + e, true, e);
        }
        if (startPoolKey == null) {
            throw new StructureAccessException("Could not resolve the start pool key for " + structureKey, false);
        }
        return Optional.of(new JigsawStructureInfo(
                ResourceId.parse(startPoolKey),
                maxDepth,
                maxDistanceFromCenter,
                Bukkit.getBukkitVersion(),
                new NamespacedKey(structureKey.namespace(), structureKey.path()).hashCode()));
    }

    @Override
    public Optional<TemplatePool> pool(ResourceId poolKey) {
        Object server;
        Object structureManager;
        Object registry;
        Object pool;
        try {
            server = ServerReflection.minecraftServer();
            structureManager = ServerReflection.invoke(server, "getStructureManager");
            registry = templatePoolRegistry(registryAccess());
            pool = ServerReflection.registryGet(registry, poolKey);
        } catch (StructureAccessException e) {
            throw e;
        } catch (Throwable e) {
            throw new StructureAccessException("pool " + poolKey + ": " + e.getMessage(), true, e);
        }
        if (pool == null) {
            return Optional.empty();
        }

        Optional<ResourceId> fallback = Optional.empty();
        Optional<String> fallbackFailure = Optional.empty();
        try {
            Object fallbackPool = ServerReflection.unwrapHolder(ServerReflection.invoke(pool, "getFallback"));
            String fallbackKey = ServerReflection.registryKeyOf(registry, fallbackPool);
            fallback = Optional.ofNullable(fallbackKey).map(ResourceId::parse);
        } catch (Throwable e) {
            fallbackFailure = Optional.of(ServerReflection.failureDetail(e));
        }

        List<?> templates;
        try {
            templates = (List<?>) ServerReflection.invoke(pool, "getTemplates");
        } catch (Throwable e) {
            throw new StructureAccessException("templates " + poolKey + ": " + e.getMessage(), true, e);
        }
        List<TemplatePool.Member> members = new ArrayList<>();
        for (Object pair : templates) {
            members.add(PoolElementReader.memberOf(pair, structureManager));
        }
        return Optional.of(new TemplatePool(members, fallback, fallbackFailure));
    }

    @Override
    public boolean supportsCapture() {
        return INMS.get().supportsStructureCapture();
    }

    @Override
    public Optional<CaptureArea> openCaptureArea(Sender sender) {
        VolmitSender messages = messagesOf(sender);
        World scratch = FeatureImporter.createScratchWorld(messages);
        return scratch == null ? Optional.empty() : Optional.of(new BukkitCaptureArea(scratch, messages));
    }

    private static VolmitSender messagesOf(Sender sender) {
        return sender.extension(CommandSender.class).map(VolmitSender::new).orElseGet(BukkitPlatform::console);
    }

    private static Object registryAccess() {
        Object server;
        try {
            server = ServerReflection.minecraftServer();
        } catch (Throwable e) {
            throw new StructureAccessException("Failed to access server registries via reflection: " + e, true, e);
        }
        Object registryAccess = ServerReflection.resolveRegistryAccess(server);
        if (registryAccess == null) {
            throw new StructureAccessException("Could not resolve RegistryAccess from the server", true);
        }
        return registryAccess;
    }

    private static Object templatePoolRegistry(Object registryAccess) {
        try {
            return ServerReflection.lookupRegistry(registryAccess, "TEMPLATE_POOL");
        } catch (Throwable e) {
            throw new StructureAccessException("Failed to access TEMPLATE_POOL registry: " + e, true, e);
        }
    }

    private static List<ResourceId> idsOf(Iterable<String> keys) {
        TreeSet<ResourceId> ids = new TreeSet<>();
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            try {
                ids.add(ResourceId.parse(key.toLowerCase(Locale.ROOT)));
            } catch (IllegalArgumentException invalidKey) {
                continue;
            }
        }
        return new ArrayList<>(ids);
    }

    private static List<ResourceId> enumerateTemplateKeys() throws Exception {
        Object server = ServerReflection.minecraftServer();
        Object resourceManager = resolveResourceManager(server);
        if (resourceManager == null) {
            throw new IllegalStateException("Could not resolve the server ResourceManager via reflection");
        }

        Method listResources = null;
        for (Method m : resourceManager.getClass().getMethods()) {
            if (m.getName().equals("listResources") && m.getParameterCount() == 2
                    && m.getParameterTypes()[0] == String.class
                    && m.getParameterTypes()[1] == Predicate.class
                    && Map.class.isAssignableFrom(m.getReturnType())) {
                listResources = m;
                break;
            }
        }
        if (listResources == null) {
            throw new NoSuchMethodException("listResources(String, Predicate) on " + resourceManager.getClass().getName());
        }
        listResources.setAccessible(true);

        Predicate<Object> endsWithNbt = location -> {
            String path = identifierPath(location);
            return path != null && path.endsWith(TEMPLATE_FILE_SUFFIX);
        };

        Object resultMap = listResources.invoke(resourceManager, STRUCTURE_TEMPLATE_FOLDER, endsWithNbt);
        TreeSet<String> keys = new TreeSet<>();
        if (resultMap instanceof Map<?, ?> map) {
            for (Object location : map.keySet()) {
                String namespace = identifierNamespace(location);
                String path = identifierPath(location);
                if (namespace == null || path == null) {
                    continue;
                }
                String stripped = path;
                if (stripped.startsWith(STRUCTURE_TEMPLATE_FOLDER + "/")) {
                    stripped = stripped.substring((STRUCTURE_TEMPLATE_FOLDER + "/").length());
                }
                if (stripped.endsWith(TEMPLATE_FILE_SUFFIX)) {
                    stripped = stripped.substring(0, stripped.length() - TEMPLATE_FILE_SUFFIX.length());
                }
                if (stripped.isEmpty()) {
                    continue;
                }
                keys.add(namespace + ":" + stripped);
            }
        }
        return idsOf(keys);
    }

    private static Object resolveResourceManager(Object server) {
        try {
            Class<?> resourceManagerClass = Class.forName("net.minecraft.server.packs.resources.ResourceManager");
            Method getter = null;
            for (Method m : server.getClass().getMethods()) {
                if (m.getName().equals("getResourceManager") && m.getParameterCount() == 0
                        && resourceManagerClass.isAssignableFrom(m.getReturnType())) {
                    getter = m;
                    break;
                }
            }
            if (getter == null) {
                for (Method m : server.getClass().getMethods()) {
                    if (m.getParameterCount() == 0 && resourceManagerClass.isAssignableFrom(m.getReturnType())) {
                        getter = m;
                        break;
                    }
                }
            }
            if (getter == null) {
                return null;
            }
            getter.setAccessible(true);
            return getter.invoke(server);
        } catch (Throwable e) {
            return null;
        }
    }

    private static String identifierNamespace(Object location) {
        try {
            Method m = location.getClass().getMethod("getNamespace");
            m.setAccessible(true);
            Object value = m.invoke(location);
            return value == null ? null : value.toString();
        } catch (Throwable e) {
            return null;
        }
    }

    private static String identifierPath(Object location) {
        try {
            Method m = location.getClass().getMethod("getPath");
            m.setAccessible(true);
            Object value = m.invoke(location);
            return value == null ? null : value.toString();
        } catch (Throwable e) {
            return null;
        }
    }
}
