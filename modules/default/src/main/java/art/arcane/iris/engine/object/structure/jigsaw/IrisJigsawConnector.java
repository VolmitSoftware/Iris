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

package art.arcane.iris.engine.object.structure.jigsaw;

import art.arcane.iris.engine.object.value.IrisDirection;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.iris.engine.object.annotations.Required;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Objects;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A jigsaw connection point on a piece. When two connectors face each other and their names match, the assembler may join the pieces.")
@Data
public class IrisJigsawConnector {
    public static IrisPosition canonicalPlanarPosition(IrisPosition cellSize, IrisDirection direction) {
        IrisPosition dimensions = Objects.requireNonNull(cellSize, "Planar jigsaw cell size");
        IrisDirection face = Objects.requireNonNull(direction, "Planar jigsaw connector direction");
        if (dimensions.getX() < 1 || dimensions.getY() < 1 || dimensions.getZ() < 1) {
            throw new IllegalArgumentException("Planar jigsaw cell dimensions must be positive");
        }
        int centerX = dimensions.getX() / 2;
        int centerY = dimensions.getY() / 2;
        int centerZ = dimensions.getZ() / 2;
        return switch (face) {
            case NORTH_NEGATIVE_Z -> new IrisPosition(centerX, centerY, 0);
            case EAST_POSITIVE_X -> new IrisPosition(dimensions.getX() - 1, centerY, centerZ);
            case SOUTH_POSITIVE_Z -> new IrisPosition(centerX, centerY, dimensions.getZ() - 1);
            case WEST_NEGATIVE_X -> new IrisPosition(0, centerY, centerZ);
            case UP_POSITIVE_Y, DOWN_NEGATIVE_Y -> throw new IllegalArgumentException(
                    "Planar jigsaw connector direction must be horizontal");
        };
    }

    @Required
    @Desc("The position of this connector relative to the piece object's origin (0,0,0 is the lowest-corner of the object).")
    private IrisPosition position = new IrisPosition();

    @Required
    @Desc("The direction this connector faces. The connecting piece is placed on this side.")
    private IrisDirection direction = IrisDirection.NORTH_NEGATIVE_Z;

    @Required
    @Desc("The connector's authored top direction, used to preserve aligned vanilla jigsaw orientation.")
    private IrisDirection top = IrisDirection.UP_POSITIVE_Y;

    @Required
    @RegistryListResource(IrisJigsawPool.class)
    @Desc("The jigsaw pool to draw the connecting piece from.")
    private String pool = "";

    @Desc("The identity exposed by this connector. Another connector can attach when its targetName equals this name, matching Minecraft's jigsaw target-to-name contract.")
    private String name = "";

    @Desc("The name this connector wants to connect to on the other piece.")
    private String targetName = "";

    @Desc("Optional Iris-only connection channel. Matching is exact, case-sensitive, and whitespace-sensitive; empty channels match only other empty channels.")
    private String channel = "";

    @Desc("How the connecting piece may be rotated relative to this connector.")
    private JigsawJoint joint = JigsawJoint.ROLLABLE;

    @Required
    @Desc("Block state placed where this jigsaw marker was authored after assembly. Used when exporting or capturing Mojang-style jigsaw templates.")
    private String finalState = "minecraft:air";

    @Desc("Signed priority for selecting this connector within its piece. Higher values are processed before lower values; equal priorities preserve authored order.")
    private int selectionPriority = 0;

    @Desc("Signed priority assigned to a child piece attached through this connector. Higher-priority child pieces expand first; equal priorities preserve attachment order.")
    private int placementPriority = 0;
}
