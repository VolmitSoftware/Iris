/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.IrisBlockData;

import art.arcane.iris.pack.validation.CompatAction;
import art.arcane.iris.pack.validation.CompatFinding;
import art.arcane.iris.pack.validation.CompatRegistry;
import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.pack.validation.PackCompatReport;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.collection.KList;

/**
 * The per-object half of the version-content gate, shared by {@link IrisObjectPlacement} pools and
 * {@link IrisStaticObject} entries: whether the blocks a placement itself generates ({@code edit[].replace}) resolve
 * on this server, and which keys of one .iob palette still cannot reach the world after those edit rules and the
 * fallback chain.
 */
final class IrisObjectCompat {
    private IrisObjectCompat() {
    }

    /**
     * The placement's own composition blocks: the {@code edit[].replace} palettes. {@code edit[].find} and
     * {@code markers[].mark} are match sides and never count - matching a block the server does not have is harmless.
     *
     * @param detailPrefix text put before the {@code edit[i].replace.palette[j]} path in every finding, or empty
     * @return the finding that excludes the subject, or null when every declared block resolves
     */
    static CompatFinding evaluateEditPalettes(KList<IrisObjectReplace> edit, IrisData data, ContentGate gate,
                                              String subjectType, String subjectKey, String detailPrefix,
                                              KList<CompatFinding> reasons) {
        if (edit == null) {
            return null;
        }

        CompatFinding blocked = null;

        for (int i = 0; i < edit.size(); i++) {
            IrisObjectReplace rule = edit.get(i);

            if (rule == null || rule.getReplace() == null || rule.getReplace().getPalette() == null) {
                continue;
            }

            KList<IrisBlockData> palette = rule.getReplace().getPalette();

            for (int j = 0; j < palette.size(); j++) {
                CompatFinding finding = evaluateBlockEntry(palette.get(j), data, gate, subjectType, subjectKey,
                        detailPrefix + "edit[" + i + "].replace.palette[" + j + "]", reasons);

                if (finding != null && blocked == null) {
                    blocked = finding;
                }
            }
        }

        return blocked;
    }

    /** Records what happened to one declared block entry; returns the finding when it excludes the subject. */
    private static CompatFinding evaluateBlockEntry(IrisBlockData block, IrisData data, ContentGate gate,
                                                    String subjectType, String subjectKey, String detail,
                                                    KList<CompatFinding> reasons) {
        if (block == null) {
            return null;
        }

        BlockCompat resolution = resolveBlockEntry(block, data, gate, new KList<>());

        if (resolution.missing()) {
            CompatFinding finding = new CompatFinding(CompatRegistry.BLOCK, resolution.key(), CompatAction.EXCLUDED,
                    subjectType, subjectKey, detail);
            reasons.add(finding);
            return finding;
        }

        if (resolution.substituted()) {
            reasons.add(new CompatFinding(CompatRegistry.BLOCK, resolution.key(), CompatAction.SUBSTITUTED,
                    subjectType, subjectKey, detail + " (using " + resolution.replacement() + ")"));
        }

        return null;
    }

    /**
     * Every distinct palette key of {@code objectKey} that cannot reach the world, in palette order; empty when the
     * object still places. A key survives when the gate resolves it (dimension fallbacks count) or when an
     * {@code edit} rule with chance 1 matches it - that rule's replace palette was already verified by
     * {@link #evaluateEditPalettes}. A rule below chance 1 does not cover the key: the original block can still be
     * rolled. Substitutions are recorded against {@code subjectType}/{@code subjectKey}.
     */
    static KList<String> unplaceableKeys(String objectKey, KList<IrisObjectReplace> edit, IrisData data,
                                         ContentGate gate, String subjectType, String subjectKey, String detail,
                                         KList<CompatFinding> reasons) {
        KList<String> missing = new KList<>();

        // Palette header only - no IrisObject is built and no state is resolved. A legacy (V1) object reads as an
        // empty palette and is therefore never dropped; V1 has no palette block to scan without a full load.
        for (String paletteKey : IrisObjectIO.readPaletteKeysCached(data.getObjectLoader().findFile(objectKey))) {
            String base = ContentGate.baseKey(ContentGate.normalizeState(paletteKey));

            if (base == null) {
                continue;
            }

            // Existence is a registry question about the block, never about its properties: a property that was
            // renamed on a block the server still has is out of scope for the gate.
            ContentGate.BlockResolution resolution = gate.resolveBlock(base);

            if (resolution != null) {
                if (resolution.substituted()) {
                    reasons.add(new CompatFinding(CompatRegistry.BLOCK, base, CompatAction.SUBSTITUTED,
                            subjectType, subjectKey, detail + " (using " + resolution.resolvedKey() + ")"));
                }

                continue;
            }

            if (coveredByEdit(edit, paletteKey)) {
                continue;
            }

            missing.addIfMissing(base);
        }

        return missing;
    }

    /** True when an {@code edit} rule certainly rewrites this palette state before it is written. */
    static boolean coveredByEdit(KList<IrisObjectReplace> edit, String paletteKey) {
        if (edit == null) {
            return false;
        }

        for (IrisObjectReplace rule : edit) {
            if (rule == null || rule.getChance() < 1F) {
                continue;
            }

            if (rule.matchesState(paletteKey)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Mirrors {@code IrisBlockData.getBlockData} against the gate: pack custom block first, then the registry with
     * dimension fallbacks, then the entry's own {@code backup}.
     */
    static BlockCompat resolveBlockEntry(IrisBlockData block, IrisData data, ContentGate gate, KList<IrisBlockData> seen) {
        for (IrisBlockData visited : seen) {
            if (visited == block) {
                return new BlockCompat(String.valueOf(block.getBlock()), null, true, false);
            }
        }

        seen.add(block);
        String declared = block.getBlock();
        IrisBlockData custom = data.getBlockLoader() == null ? null : data.getBlockLoader().load(declared, false);

        if (custom != null && custom != block) {
            return resolveBlockEntry(custom, data, gate, seen);
        }

        String base = ContentGate.baseKey(ContentGate.normalizeState(declared));

        if (base == null) {
            return new BlockCompat(String.valueOf(declared), null, true, false);
        }

        ContentGate.BlockResolution resolution = gate.resolveBlock(base);

        if (resolution != null) {
            return new BlockCompat(base, resolution.resolvedKey(), false, resolution.substituted());
        }

        IrisBlockData backup = block.getBackup();

        if (backup != null) {
            BlockCompat viaBackup = resolveBlockEntry(backup, data, gate, seen);

            if (!viaBackup.missing()) {
                return new BlockCompat(base, viaBackup.replacement(), false, true);
            }
        }

        return new BlockCompat(base, null, true, false);
    }

    static void record(PackCompatReport report, KList<CompatFinding> findings) {
        for (CompatFinding finding : findings) {
            report.record(finding);
        }
    }

    /** One block entry's verdict: the key asked for, what will actually generate, and how it got there. */
    record BlockCompat(String key, String replacement, boolean missing, boolean substituted) {
    }
}
