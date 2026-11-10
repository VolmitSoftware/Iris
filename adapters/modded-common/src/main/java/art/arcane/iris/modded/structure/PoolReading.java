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

import com.mojang.datafixers.util.Pair;
import com.volmit.geometry.BlockFace;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.StructureAccessException;
import com.volmit.tooling.TemplatePool;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.LongSupplier;

/** The members of a vanilla template pool: their weights, templates and jigsaw connectors. */
final class PoolReading {
    private static final String ALIGNED_JOINT = "ALIGN";
    private static final String FINAL_STATE_NBT_KEY = "final_state";

    private PoolReading() {
    }

    /** The member of a pool for the pair the pool lists it as: its element and weight. */
    static TemplatePool.Member memberOf(Pair<StructurePoolElement, Integer> pair, StructureTemplateManager templates) {
        StructurePoolElement element = pair.getFirst();
        if (element == null) {
            return new TemplatePool.Absent();
        }
        int weight = pair.getSecond() == null ? 1 : Math.max(1, pair.getSecond());
        Resolution resolution = resolve(element);
        String type = resolution.physical() == null ? element.getClass().getSimpleName() : resolution.physical().getClass().getSimpleName();
        Optional<ResourceId> template = resolution.physical() instanceof SinglePoolElement single
                ? Optional.of(ResourceId.parse(single.getTemplateLocation().toString().toLowerCase(Locale.ROOT)))
                : Optional.empty();
        return new TemplatePool.Element(weight, type, template, resolution.listLevels(), resolution.omittedElements(),
                shuffleSeeds -> connectorsOf(element, templates, shuffleSeeds));
    }

    /** The first element that is no list, and how many lists and further elements were passed to reach it. */
    private record Resolution(StructurePoolElement physical, int listLevels, int omittedElements) {
    }

    private static Resolution resolve(StructurePoolElement element) {
        if (!(element instanceof ListPoolElement list)) {
            return new Resolution(element, 0, 0);
        }
        List<StructurePoolElement> elements = list.getElements();
        if (elements.isEmpty()) {
            return new Resolution(null, 1, 0);
        }
        Resolution primary = resolve(elements.getFirst());
        return new Resolution(primary.physical(), primary.listLevels() + 1, primary.omittedElements() + elements.size() - 1);
    }

    private static TemplatePool.Connectors connectorsOf(StructurePoolElement element, StructureTemplateManager templates,
                                                        LongSupplier shuffleSeeds) {
        try {
            List<StructureTemplate.JigsawBlockInfo> blocks = element.getShuffledJigsawBlocks(
                    templates, BlockPos.ZERO, Rotation.NONE, RandomSource.create(shuffleSeeds.getAsLong()));
            if (blocks == null) {
                return new TemplatePool.Connectors(TemplatePool.Availability.NO_COLLECTION, List.of());
            }
            List<TemplatePool.Connector> connectors = new ArrayList<>();
            for (StructureTemplate.JigsawBlockInfo jigsaw : blocks) {
                connectors.add(connectorOf(jigsaw));
            }
            return new TemplatePool.Connectors(TemplatePool.Availability.READ, connectors);
        } catch (RuntimeException failure) {
            throw new StructureAccessException(failureDetail(failure), true, failure);
        }
    }

    private static TemplatePool.Connector connectorOf(StructureTemplate.JigsawBlockInfo jigsaw) {
        try {
            return connectorFrom(jigsaw);
        } catch (RuntimeException failure) {
            return new TemplatePool.UnreadableConnector(failureDetail(failure));
        }
    }

    private static TemplatePool.JigsawConnector connectorFrom(StructureTemplate.JigsawBlockInfo jigsaw) {
        StructureTemplate.StructureBlockInfo info = jigsaw.info();
        BlockPos at = info.pos();
        String poolId = jigsaw.pool().identifier().toString();
        Optional<String> finalState = info.nbt() == null ? Optional.empty()
                : info.nbt().getString(FINAL_STATE_NBT_KEY).filter(state -> !state.isBlank());
        return new TemplatePool.JigsawConnector(
                new BlockPosition(at.getX(), at.getY(), at.getZ()),
                faceOf(JigsawBlock.getFrontFacing(info.state()).getName()),
                faceOf(JigsawBlock.getTopFacing(info.state()).getName()),
                poolId.isEmpty() ? Optional.empty() : Optional.of(ResourceId.parse(poolId)),
                jigsaw.name().toString(),
                jigsaw.target().toString(),
                String.valueOf(jigsaw.jointType()).toUpperCase(Locale.ROOT).contains(ALIGNED_JOINT),
                finalState,
                jigsaw.selectionPriority(),
                jigsaw.placementPriority());
    }

    private static BlockFace faceOf(String facing) {
        return switch (facing) {
            case "up" -> BlockFace.UP;
            case "down" -> BlockFace.DOWN;
            case "south" -> BlockFace.SOUTH;
            case "east" -> BlockFace.EAST;
            case "west" -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }

    /** The message a failed read shows: the failure's message, else its class name. */
    static String failureDetail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
