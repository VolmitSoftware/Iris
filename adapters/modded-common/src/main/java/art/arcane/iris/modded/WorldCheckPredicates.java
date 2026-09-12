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

package art.arcane.iris.modded;

import art.arcane.iris.structure.placement.StructureVerticalBounds;
import art.arcane.iris.modded.WorldCheckStructureAudit.StructureCheck;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class WorldCheckPredicates {

    private WorldCheckPredicates() {
    }

    static void emitSkipped(StructureCheck check, String reason, String... events) {
        for (String event : events) {
            qaEvent(event, check.label(), false, "skipped=" + reason);
        }
    }

    static void qaEvent(String event, String structure, boolean pass, String detail) {
        ModdedIrisLog.info(qaEventJson(event, structure, pass, detail));
    }

    static String qaEventJson(String event, String structure, boolean pass, String detail) {
        return "QA_EVT {\"event\":\"" + jsonEscape(event)
                + "\",\"structure\":\"" + jsonEscape(structure)
                + "\",\"pass\":" + pass
                + ",\"detail\":\"" + jsonEscape(detail) + "\"}";
    }

    static String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 32) {
                        escaped.append("\\u");
                        String hex = Integer.toHexString(character);
                        escaped.append("0".repeat(4 - hex.length())).append(hex);
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    static boolean hasNativeStructureEvidence(boolean validStart, int references) {
        return validStart || references > 0;
    }

    static boolean hasCharacteristicMaterialEvidence(int blocks, int chunksWithMaterial, int scannedChunks) {
        if (blocks <= 0 || chunksWithMaterial <= 0 || scannedChunks <= 0
                || chunksWithMaterial > scannedChunks) {
            return false;
        }
        return scannedChunks == 1 || chunksWithMaterial > 1;
    }

    static boolean verticalShiftMatches(int configuredShift, Integer appliedShift, int shiftedMinY,
                                        int shiftedMaxY, int worldMinY, int worldMaxYExclusive) {
        try {
            if (appliedShift == null) {
                return configuredShift == 0 && StructureVerticalBounds.clampOffset(
                        shiftedMinY, shiftedMaxY, 0, worldMinY, worldMaxYExclusive) == 0;
            }
            int originalMinY = Math.subtractExact(shiftedMinY, appliedShift);
            int originalMaxY = Math.subtractExact(shiftedMaxY, appliedShift);
            int expectedShift = StructureVerticalBounds.clampOffset(
                    originalMinY, originalMaxY, configuredShift, worldMinY, worldMaxYExclusive);
            return appliedShift == expectedShift;
        } catch (RuntimeException error) {
            return false;
        }
    }

    static boolean mansionVegetationPass(int remainingVegetationBlocks) {
        return remainingVegetationBlocks == 0;
    }

    static boolean mansionVegetationAbovePiece(boolean vegetation, int blockY, int highestPieceY) {
        return vegetation && blockY > highestPieceY;
    }

    static boolean villageFoundationPass(int unsupportedColumns) {
        return unsupportedColumns == 0;
    }

    static boolean villagePoiPass(int inBounds, int outOfBounds) {
        return inBounds > 0 && outOfBounds == 0;
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
