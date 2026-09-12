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

import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectIO;

import art.arcane.iris.pack.validation.CompatAction;
import art.arcane.iris.pack.validation.CompatFinding;
import art.arcane.iris.pack.validation.CompatRegistry;
import art.arcane.iris.pack.validation.CompatStatus;
import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A jigsaw piece. A piece is a single Iris object plus the connection points (connectors) that let the assembler attach other pieces to it.")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisJigsawPiece extends IrisRegistrant {
    @Description("Optional author-facing name shown by Jigsaw Studio. The resource key is shown when this is blank.")
    private String displayName = "";

    @Required
    @RegistryListResource(IrisObject.class)
    @Description("The object (schematic) that makes up this piece.")
    private String object = "";

    @ArrayType(type = IrisJigsawConnector.class, min = 1)
    @Description("The jigsaw connection points on this piece.")
    private KList<IrisJigsawConnector> connectors = new KList<>();

    @Description("If true, this piece may be rotated around the Y axis when placed so the assembler can match its connectors. Disable for pieces that must keep a fixed orientation.")
    private boolean rotatable = true;

    @Description("If true, this piece's object bounds block overlapping jigsaw pieces. Disable for connector-only scaffolds whose stored volume is intentionally shared with physical pieces.")
    private boolean collidable = true;

    @ArrayType(type = String.class, min = 1)
    @Description("Theme keys that may select this piece. An empty list makes the piece available to every selected theme.")
    private KList<String> themes = new KList<>();

    @Description("Deterministic depth, placement-count, and terminal-role rules for this piece.")
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

    /**
     * A piece is one object stamped verbatim - nothing rewrites its blocks - so a palette key the server does not
     * have (after the pack's declared fallbacks) takes the whole piece out of every pool that lists it.
     */
    @Override
    public CompatStatus evaluateCompat(ContentGate gate) {
        CompatStatus base = super.evaluateCompat(gate);

        if (base.excluded() || gate == null || !gate.ready()) {
            return base;
        }

        IrisData data = getLoader();

        if (data == null || object == null || object.isBlank()) {
            return base;
        }

        for (String paletteKey : IrisObjectIO.readPaletteKeysCached(data.getObjectLoader().findFile(object.trim()))) {
            String key = ContentGate.baseKey(ContentGate.normalizeState(paletteKey));

            if (key == null || gate.resolveBlock(key) != null) {
                continue;
            }

            CompatFinding finding = new CompatFinding(CompatRegistry.BLOCK, key, CompatAction.EXCLUDED,
                    "jigsaw piece", getLoadKey(), "object " + object.trim());
            gate.report().record(finding);
            KList<CompatFinding> reasons = new KList<>(base.reasons());
            reasons.add(finding);
            return CompatStatus.excludedBy(reasons);
        }

        return base;
    }

    @Override
    public String getFolderName() {
        return "jigsaw-pieces";
    }

    @Override
    public String getTypeName() {
        return "Jigsaw Piece";
    }
}
