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

import com.volmit.config.Desc;
import com.volmit.config.Required;
import com.volmit.config.ResourceRef;
import com.volmit.object.GeneratedObject;
import art.arcane.volmlib.util.collection.KList;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A jigsaw piece. A piece is a single Iris object plus the connection points (connectors) that let the assembler attach other pieces to it.")
@Data
public class IrisJigsawPiece {
    @Desc("Optional author-facing name shown by Jigsaw Studio. The resource key is shown when this is blank.")
    private String displayName = "";

    @Required
    @ResourceRef(GeneratedObject.class)
    @Desc("The object (schematic) that makes up this piece.")
    private String object = "";

    @Desc("The jigsaw connection points on this piece.")
    private List<IrisJigsawConnector> connectors = new KList<>();

    @Desc("If true, this piece may be rotated around the Y axis when placed so the assembler can match its connectors. Disable for pieces that must keep a fixed orientation.")
    private boolean rotatable = true;

    @Desc("If true, this piece's object bounds block overlapping jigsaw pieces. Disable for connector-only scaffolds whose stored volume is intentionally shared with physical pieces.")
    private boolean collidable = true;

    @Desc("Theme keys that may select this piece. An empty list makes the piece available to every selected theme.")
    private List<String> themes = new KList<>();

    @Desc("Deterministic depth, placement-count, and terminal-role rules for this piece.")
    private IrisJigsawPieceRules rules = new IrisJigsawPieceRules();

    public boolean supportsTheme(String selectedTheme) {
        if (themes == null || themes.isEmpty()) {
            return true;
        }
        if (selectedTheme == null || selectedTheme.isBlank()) {
            return false;
        }
        for (String theme : themes) {
            if (selectedTheme.equals(theme)) {
                return true;
            }
        }
        return false;
    }

    public IrisJigsawPieceRules resolvedRules() {
        return rules == null ? new IrisJigsawPieceRules() : rules;
    }
}
