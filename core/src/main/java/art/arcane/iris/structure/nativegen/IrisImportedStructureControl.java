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

package art.arcane.iris.structure.nativegen;

import art.arcane.iris.structure.placement.IrisStructureSetFrequencyOverride;
import art.arcane.iris.structure.placement.IrisStructureStiltSettings;
import art.arcane.iris.structure.placement.IrisStructureTerrain;
import art.arcane.iris.structure.placement.IrisStructureYBand;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListVanillaStructure;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Locale;
import java.util.Objects;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Controls native vanilla, mod, and ingested datapack structure generation for this dimension (set as the dimension's 'importedStructures' field). Every registered structure generates through its native placement unless its key matches 'disabled', equals a key in 'disabledExact', or a viable dimension-level Iris placement explicitly replaces its source. Family matching uses namespace, slash, or underscore boundaries, while exact matching compares normalized complete keys only. Run '/iris structure list <dimension>' to dump every valid key. Only affects newly generated chunks and is separate from Iris structure placements.")
@Data
public class IrisImportedStructureControl {
    @ArrayType(type = String.class, min = 1)
    @RegistryListVanillaStructure(prefixes = true)
    @Description("Structure keys to deny explicitly, e.g. 'minecraft:stronghold'. A namespace:path prefix also matches, so 'minecraft:village' disables every village variant and 'minecraft:ruined_portal' disables every ruined portal. Every key not matched here remains enabled.")
    private KList<String> disabled = new KList<>();

    @ArrayType(type = String.class, min = 1)
    @RegistryListVanillaStructure(prefixes = false)
    @Description("Exact structure keys to deny after trimming and case normalization. Unlike 'disabled', entries never match key families, so 'minecraft:ruined_portal' does not disable 'minecraft:ruined_portal_nether'.")
    private KList<String> disabledExact = new KList<>();

    @MinNumber(-512)
    @MaxNumber(512)
    @Description("Vertical block offset applied only to UNDERGROUND vanilla structures (the UNDERGROUND_STRUCTURES, UNDERGROUND_DECORATION, and STRONGHOLDS generation steps: strongholds, trial chambers, mineshafts, ancient cities, etc.). Surface structures (villages, outposts, etc.) are never shifted. Use a negative value to push deep structures lower when your dimension's sea/terrain level differs from vanilla's (e.g. -64 if you lowered the fluid height to 0). 0 = no shift.")
    private int undergroundYShift = 0;

    @Description("Controls whether ingested datapacks may replace minecraft-namespaced structure definitions, sets, pools, and templates. When false, those overrides are stripped from installed datapack copies so vanilla definitions stay intact. Non-minecraft structures from datapacks and mods remain governed by disabled because namespace alone cannot identify their origin. Resolved globally across loaded packs: if any dimension sets this false, minecraft-namespaced overrides are stripped from every installed datapack copy.")
    private boolean datapackOverrides = true;

    @ArrayType(type = IrisStructureSetFrequencyOverride.class, min = 1)
    @Description("Exact registered structure-set frequency overrides for native generation. The last entry for a normalized structure-set key wins. These do not convert native structures into Iris placements and affect only newly generated chunks.")
    private KList<IrisStructureSetFrequencyOverride> frequencyOverrides = new KList<>();

    @ArrayType(type = IrisVanillaStructureAdjustment.class, min = 1)
    @Description("Per-structure adjustments applied to vanilla, mod, and datapack structures that still generate natively. Vertical shifts from every matching entry stack. A matching preserveSourceY option disables Iris burial repositioning for that structure. The last matching entry with stilt settings controls foundation columns, and likewise for terrain and yBand settings. A structure suppressed by an Iris placement is unaffected.")
    private KList<IrisVanillaStructureAdjustment> adjustments = new KList<>();

    public boolean shouldGenerate(String key) {
        return key != null && !key.isBlank()
                && !matches(disabled, key)
                && !matchesExact(disabledExact, key);
    }

    public IrisNativeStructureDecision resolve(String key, boolean undergroundStep) {
        KList<IrisVanillaStructureAdjustment> activeAdjustments = Objects.requireNonNull(
                adjustments, "importedStructures.adjustments must not be null");
        int y = undergroundStep ? undergroundYShift : 0;
        boolean preserveSourceY = false;
        IrisStructureStiltSettings stilt = null;
        IrisStructureTerrain terrain = null;
        IrisStructureYBand yBand = null;
        for (IrisVanillaStructureAdjustment adjustment : activeAdjustments) {
            if (adjustment != null && adjustment.matches(key)) {
                y += adjustment.getYShift();
                preserveSourceY |= adjustment.isPreserveSourceY();
                if (adjustment.getStilt() != null) {
                    stilt = adjustment.getStilt();
                }
                if (adjustment.getTerrain() != null) {
                    terrain = adjustment.getTerrain();
                }
                if (adjustment.getYBand() != null) {
                    yBand = adjustment.getYBand();
                }
            }
        }
        return new IrisNativeStructureDecision(
                generationStatus(key), y, yBand, preserveSourceY, stilt, terrain);
    }

    public double frequencyMultiplier(String structureSetKey) {
        KList<IrisStructureSetFrequencyOverride> activeOverrides = Objects.requireNonNull(
                frequencyOverrides, "importedStructures.frequencyOverrides must not be null");
        String normalizedKey = normalizeKey(structureSetKey);
        if (normalizedKey.isEmpty()) {
            return 1D;
        }
        double multiplier = 1D;
        for (IrisStructureSetFrequencyOverride override : activeOverrides) {
            if (override != null && normalizeKey(override.getStructureSet()).equals(normalizedKey)) {
                multiplier = override.getMultiplier();
            }
        }
        return multiplier;
    }

    public boolean hasFrequencyOverrides() {
        return !Objects.requireNonNull(
                frequencyOverrides, "importedStructures.frequencyOverrides must not be null").isEmpty();
    }

    private NativeStructureGenerationStatus generationStatus(String key) {
        if (key == null || key.isBlank()) {
            return NativeStructureGenerationStatus.INVALID_REGISTRY_KEY;
        }
        if (matches(disabled, key) || matchesExact(disabledExact, key)) {
            return NativeStructureGenerationStatus.DISABLED_BY_PACK;
        }
        return NativeStructureGenerationStatus.GENERATE_NATIVE;
    }

    private boolean matches(KList<String> list, String key) {
        if (key == null) {
            return false;
        }
        KList<String> activeList = Objects.requireNonNull(
                list, "importedStructures.disabled must not be null");
        for (String entry : activeList) {
            if (matchesKey(entry, key)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesExact(KList<String> list, String key) {
        KList<String> activeList = Objects.requireNonNull(
                list, "importedStructures.disabledExact must not be null");
        String normalizedKey = normalizeKey(key);
        for (String entry : activeList) {
            String normalizedEntry = normalizeKey(entry);
            if (!normalizedEntry.isEmpty() && normalizedEntry.equals(normalizedKey)) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesKey(String pattern, String key) {
        if (pattern == null || key == null) {
            return false;
        }
        String normalizedPattern = pattern.trim().toLowerCase(Locale.ROOT);
        String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
        if (normalizedPattern.isEmpty() || !normalizedKey.startsWith(normalizedPattern)) {
            return false;
        }
        if (normalizedKey.length() == normalizedPattern.length()) {
            return true;
        }
        char patternEnd = normalizedPattern.charAt(normalizedPattern.length() - 1);
        if (patternEnd == ':' || patternEnd == '/' || patternEnd == '_') {
            return true;
        }
        char boundary = normalizedKey.charAt(normalizedPattern.length());
        return boundary == '/' || boundary == '_';
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }
}
