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

package art.arcane.iris.structure.jigsaw;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A weighted reference to a jigsaw piece inside a jigsaw pool.")
@Data
public class IrisJigsawPieceEntry {
    @RegistryListResource(IrisJigsawPiece.class)
    @Description("The jigsaw piece this entry refers to. Omit when empty is true.")
    private String piece;

    @MinNumber(1)
    @Description("The relative weight of this piece within its pool. Higher weights are chosen more often.")
    private int weight = 1;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("The independent eligibility chance applied to this exact pool membership before weighted selection. Zero never passes and one always passes.")
    private double chance = 1D;

    @Description("When true, choosing this entry successfully terminates the current branch without placing a piece.")
    private boolean empty = false;

    public IrisJigsawPieceEntry(String piece, int weight) {
        this(piece, weight, 1D, false);
    }

    public boolean requiresChanceRoll() {
        return chance > 0D && chance < 1D;
    }

    public boolean passesChance(double roll) {
        if (!Double.isFinite(roll) || roll < 0D || roll >= 1D) {
            throw new IllegalArgumentException("Jigsaw chance roll must be finite and within 0 inclusive and 1 exclusive");
        }
        return chance >= 1D || chance > 0D && roll < chance;
    }
}
