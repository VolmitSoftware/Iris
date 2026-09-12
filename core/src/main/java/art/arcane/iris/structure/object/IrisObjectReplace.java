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

package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;

import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import art.arcane.iris.spi.PlatformBlockState;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.Map;

@Snippet("object-block-replacer")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Find and replace object materials")
@Data
public class IrisObjectReplace {
    private final transient AtomicCache<CNG> replaceGen = new AtomicCache<>();
    private final transient AtomicCache<KList<PlatformBlockState>> findData = new AtomicCache<>();
    private final transient AtomicCache<KList<PlatformBlockState>> replaceData = new AtomicCache<>();
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Required
    @Description("Find this block")
    private KList<IrisBlockData> find = new KList<>();
    @Required
    @Description("Replace it with this block palette")
    private IrisMaterialPalette replace = new IrisMaterialPalette();
    @Description("Exactly match the block data or not")
    private boolean exact = false;
    @MinNumber(0)
    @MaxNumber(1)
    @Description("Modifies the chance the block is replaced")
    private float chance = 1;

    public KList<PlatformBlockState> getFind(IrisData rdata) {
        return findData.aquire(() ->
        {
            KList<PlatformBlockState> b = new KList<>();

            for (IrisBlockData i : find) {
                PlatformBlockState bx = i.getBlockDataOrPlaceholder(rdata);

                if (bx != null) {
                    b.add(bx);
                }
            }

            return b;
        });
    }

    public PlatformBlockState getReplace(RNG seed, double x, double y, double z, IrisData rdata) {
        return getReplace().get(seed, x, y, z, rdata);
    }

    /**
     * Key-level mirror of the runtime find match, for the version-content gate: it has to decide whether a palette
     * key the server does not have is rewritten by this rule, and a missing key has no resolved state to compare.
     * Non-exact compares the base block; exact requires every property named by the find entry to match, the same
     * partial-match contract {@code PlatformBlockState.matches} uses at runtime.
     */
    public boolean matchesState(String stateKey) {
        String state = ContentGate.normalizeState(stateKey);

        if (state == null) {
            return false;
        }

        String stateBase = ContentGate.baseKey(state);

        for (IrisBlockData f : find) {
            if (f == null) {
                continue;
            }

            String findState = ContentGate.normalizeState(declaredState(f));

            if (findState == null || !ContentGate.baseKey(findState).equals(stateBase)) {
                continue;
            }

            if (!exact || propertiesMatch(findState, state)) {
                return true;
            }
        }

        return false;
    }

    /** The find entry as one state string; {@code block} already carrying properties wins over the data map. */
    private static String declaredState(IrisBlockData block) {
        String declared = block.getBlock();

        if (declared == null) {
            return null;
        }

        return declared.indexOf('[') < 0 ? declared + block.computeProperties() : declared;
    }

    /** Every property the find side names must be present with the same value on the candidate state. */
    private static boolean propertiesMatch(String findState, String candidateState) {
        Map<String, String> required = properties(findState);

        if (required.isEmpty()) {
            return true;
        }

        Map<String, String> candidate = properties(candidateState);

        for (Map.Entry<String, String> entry : required.entrySet()) {
            if (!entry.getValue().equals(candidate.get(entry.getKey()))) {
                return false;
            }
        }

        return true;
    }

    private static Map<String, String> properties(String state) {
        int open = state.indexOf('[');
        int close = state.lastIndexOf(']');

        if (open < 0 || close < open) {
            return Map.of();
        }

        Map<String, String> properties = new LinkedHashMap<>();

        for (String part : state.substring(open + 1, close).split(",")) {
            int split = part.indexOf('=');

            if (split > 0) {
                properties.put(part.substring(0, split).trim(), part.substring(split + 1).trim());
            }
        }

        return properties;
    }
}
