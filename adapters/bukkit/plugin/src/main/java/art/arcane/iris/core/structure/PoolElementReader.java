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

import com.volmit.geometry.BlockFace;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.StructureAccessException;
import com.volmit.tooling.TemplatePool;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.LongSupplier;

/** Reads the members of a registered template pool by reflection: their weights, templates and jigsaw connectors. */
final class PoolElementReader {
    private static final String LIST_POOL_ELEMENT_SUFFIX = "ListPoolElement";
    private static final String DEFAULT_FRONT = "north";
    private static final String DEFAULT_TOP = "up";
    private static final String FINAL_STATE_NBT_KEY = "final_state";

    private PoolElementReader() {
    }

    /** The member of a pool for the pair the pool lists it as: its element and weight. */
    static TemplatePool.Member memberOf(Object pair, Object structureManager) {
        Object element;
        int weight;
        try {
            element = ServerReflection.invoke(pair, "getFirst");
            Object second = ServerReflection.invoke(pair, "getSecond");
            weight = second instanceof Number ? Math.max(1, ((Number) second).intValue()) : 1;
        } catch (Throwable e) {
            return new TemplatePool.Unreadable(TemplatePool.Part.ENTRY, ServerReflection.failureDetail(e));
        }
        if (element == null) {
            return new TemplatePool.Absent();
        }
        PoolElementResolution resolution;
        try {
            resolution = resolvePoolElement(element);
        } catch (Throwable e) {
            return new TemplatePool.Unreadable(TemplatePool.Part.TEMPLATE_LOCATION, ServerReflection.failureDetail(e));
        }
        String type = resolution.physicalElement() == null
                ? element.getClass().getSimpleName()
                : resolution.physicalElement().getClass().getSimpleName();
        Optional<ResourceId> template;
        try {
            template = Optional.ofNullable(resolution.templateLocation()).map(PoolElementReader::templateId);
        } catch (IllegalArgumentException invalidLocation) {
            return new TemplatePool.Unreadable(TemplatePool.Part.TEMPLATE_LOCATION,
                    "invalid piece key " + resolution.templateLocation());
        }
        return new TemplatePool.Element(weight, type, template, resolution.listLevels(), resolution.omittedElements(),
                shuffleSeeds -> readConnectors(element, structureManager, shuffleSeeds));
    }

    private static ResourceId templateId(String location) {
        return ResourceId.parse(location.toLowerCase(Locale.ROOT));
    }

    private static TemplatePool.Connectors readConnectors(Object element, Object structureManager, LongSupplier shuffleSeeds) {
        try {
            Object zero = ServerReflection.staticField("net.minecraft.core.BlockPos", "ZERO");
            Object rotationNone = ServerReflection.staticField("net.minecraft.world.level.block.Rotation", "NONE");
            Method m = ServerReflection.findMethod(element.getClass(), "getShuffledJigsawBlocks", 4);
            if (m == null) {
                return new TemplatePool.Connectors(TemplatePool.Availability.UNAVAILABLE, List.of());
            }
            m.setAccessible(true);
            Object random = ServerReflection.freshRandomSource(shuffleSeeds.getAsLong());
            List<?> blocks = (List<?>) m.invoke(element, structureManager, zero, rotationNone, random);
            if (blocks == null) {
                return new TemplatePool.Connectors(TemplatePool.Availability.NO_COLLECTION, List.of());
            }
            List<TemplatePool.Connector> connectors = new ArrayList<>();
            for (Object jigsaw : blocks) {
                connectors.add(connectorOf(jigsaw));
            }
            return new TemplatePool.Connectors(TemplatePool.Availability.READ, connectors);
        } catch (Throwable e) {
            throw new StructureAccessException(ServerReflection.failureDetail(e), true, e);
        }
    }

    private static TemplatePool.Connector connectorOf(Object jigsaw) {
        try {
            return connectorFrom(jigsaw);
        } catch (Throwable e) {
            return new TemplatePool.UnreadableConnector(ServerReflection.failureDetail(e));
        }
    }

    static TemplatePool.JigsawConnector connectorFrom(Object jigsaw) throws Exception {
        Object info = ServerReflection.invoke(jigsaw, "info");
        Object pos = ServerReflection.invoke(info, "pos");
        Object blockState = ServerReflection.invoke(info, "state");
        BlockPosition position = new BlockPosition(
                ServerReflection.readInt(pos, "getX"),
                ServerReflection.readInt(pos, "getY"),
                ServerReflection.readInt(pos, "getZ"));

        Object poolKey = ServerReflection.invoke(jigsaw, "pool");
        String poolId = ServerReflection.identifierString(ServerReflection.invoke(poolKey, "identifier"));
        String name = ServerReflection.identifierString(ServerReflection.invoke(jigsaw, "name"));
        String target = ServerReflection.identifierString(ServerReflection.invoke(jigsaw, "target"));
        Object jointType = ServerReflection.invoke(jigsaw, "jointType");

        BlockFace front = faceOf(frontFacing(blockState));
        BlockFace top = faceOf(topFacing(blockState));
        Optional<ResourceId> pool = poolId == null || poolId.isEmpty() ? Optional.empty() : Optional.of(ResourceId.parse(poolId));
        ConnectorMetadata metadata = readConnectorMetadata(jigsaw, info);
        boolean aligned = jointType != null && jointType.toString().toUpperCase(Locale.ROOT).contains("ALIGN");
        return new TemplatePool.JigsawConnector(
                position,
                front,
                top,
                pool,
                name,
                target,
                aligned,
                metadata.finalState(),
                metadata.selectionPriority(),
                metadata.placementPriority());
    }

    static ConnectorMetadata readConnectorMetadata(Object jigsaw, Object info) throws Exception {
        String finalState = readNbtString(ServerReflection.invoke(info, "nbt"), FINAL_STATE_NBT_KEY);
        return new ConnectorMetadata(
                finalState == null || finalState.isBlank() ? Optional.empty() : Optional.of(finalState),
                readIntAccessor(jigsaw, "selectionPriority"),
                readIntAccessor(jigsaw, "placementPriority"));
    }

    record ConnectorMetadata(Optional<String> finalState, int selectionPriority, int placementPriority) {
    }

    static BlockFace faceOf(String facing) {
        return switch (facing) {
            case "up" -> BlockFace.UP;
            case "down" -> BlockFace.DOWN;
            case "south" -> BlockFace.SOUTH;
            case "east" -> BlockFace.EAST;
            case "west" -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }

    private static String readNbtString(Object nbt, String key) throws Exception {
        if (nbt == null) {
            return null;
        }
        Method method = ServerReflection.findMethod(nbt.getClass(), "getString", 1);
        if (method == null) {
            throw new NoSuchMethodException("getString(String) on " + nbt.getClass().getName());
        }
        method.setAccessible(true);
        Object value = method.invoke(nbt, key);
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof Optional<?> optional) {
            return optional.isPresent() ? String.valueOf(optional.get()) : null;
        }
        return value == null ? null : String.valueOf(value);
    }

    private static int readIntAccessor(Object value, String accessor) throws Exception {
        Method method = ServerReflection.findMethod(value.getClass(), accessor);
        if (method == null) {
            throw new NoSuchMethodException(accessor + "() on " + value.getClass().getName());
        }
        method.setAccessible(true);
        Object result = method.invoke(value);
        if (result instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalStateException(accessor + " on " + value.getClass().getName() + " is not numeric");
    }

    private static String frontFacing(Object blockState) throws Exception {
        return facing(blockState, "getFrontFacing", DEFAULT_FRONT);
    }

    private static String topFacing(Object blockState) throws Exception {
        return facing(blockState, "getTopFacing", DEFAULT_TOP);
    }

    private static String facing(Object blockState, String jigsawBlockMethod, String fallback) throws Exception {
        Class<?> jigsawBlock = Class.forName("net.minecraft.world.level.block.JigsawBlock");
        Method read = jigsawBlock.getMethod(jigsawBlockMethod, Class.forName("net.minecraft.world.level.block.state.BlockState"));
        Object direction = read.invoke(null, blockState);
        if (direction == null) {
            return fallback;
        }
        Method getName = direction.getClass().getMethod("getName");
        getName.setAccessible(true);
        return String.valueOf(getName.invoke(direction)).toLowerCase();
    }

    private static String templateLocationOf(Object element) throws Exception {
        Method m = ServerReflection.findMethod(element.getClass(), "getTemplateLocation");
        if (m == null) {
            return null;
        }
        m.setAccessible(true);
        Object id = m.invoke(element);
        return ServerReflection.identifierString(id);
    }

    static PoolElementResolution resolvePoolElement(Object element) throws Exception {
        if (element == null) {
            return new PoolElementResolution(null, null, 0, 0);
        }
        if (!element.getClass().getSimpleName().endsWith(LIST_POOL_ELEMENT_SUFFIX)) {
            return new PoolElementResolution(element, templateLocationOf(element), 0, 0);
        }
        Object rawElements = ServerReflection.invoke(element, "getElements");
        if (!(rawElements instanceof List<?> elements)) {
            throw new IllegalStateException("getElements on " + element.getClass().getName() + " is not a list");
        }
        if (elements.isEmpty()) {
            return new PoolElementResolution(null, null, 1, 0);
        }
        PoolElementResolution primary = resolvePoolElement(elements.getFirst());
        return new PoolElementResolution(
                primary.physicalElement(),
                primary.templateLocation(),
                primary.listLevels() + 1,
                primary.omittedElements() + elements.size() - 1
        );
    }

    record PoolElementResolution(
            Object physicalElement,
            String templateLocation,
            int listLevels,
            int omittedElements
    ) {
    }
}
