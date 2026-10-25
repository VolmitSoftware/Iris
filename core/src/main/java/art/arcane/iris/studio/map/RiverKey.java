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

package art.arcane.iris.studio.map;

import art.arcane.iris.generation.hydrology.HydrologyCandidateKind;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.studio.render.IrisRenderer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the colours of the river view stand for, one row per colour, so features drawn alike share a row.
 */
final class RiverKey {
    private static final String PROJECTED_PREFIX = "projected ";
    private static final List<Row> ROWS = buildRows();

    private RiverKey() {
    }

    record Row(int color, String label) {
    }

    static List<Row> rows() {
        return ROWS;
    }

    private static List<Row> buildRows() {
        Map<Integer, List<String>> features = new LinkedHashMap<>();
        add(features, IrisRenderer.headwaterColor(), "headwater");
        add(features, IrisRenderer.surfaceWaterColor(), "river");
        add(features, IrisRenderer.subterraneanColor(), "underground river");
        for (HydrologyFeatureType type : HydrologyFeatureType.values()) {
            // No planner places ridge bores any more; the constant only stays for its ordinal.
            if (type != HydrologyFeatureType.RIDGE_BORE) {
                add(features, IrisRenderer.hydrologyFeatureColor(type), readable(type.name()));
            }
        }
        Map<Integer, List<String>> projections = new LinkedHashMap<>();
        for (HydrologyCandidateKind kind : HydrologyCandidateKind.values()) {
            add(projections, IrisRenderer.hydrologyDiagnosticColor(kind), readable(kind.name()));
        }
        List<Row> rows = new ArrayList<>();
        features.forEach((color, labels) -> rows.add(new Row(color & 0xFFFFFF, String.join(", ", labels))));
        projections.forEach((color, labels) -> rows.add(new Row(color & 0xFFFFFF, PROJECTED_PREFIX + String.join(", ", labels))));
        return List.copyOf(rows);
    }

    private static void add(Map<Integer, List<String>> labelsByColor, int color, String label) {
        List<String> labels = labelsByColor.computeIfAbsent(color, ignored -> new ArrayList<>());
        if (!labels.contains(label)) {
            labels.add(label);
        }
    }

    private static String readable(String constantName) {
        return constantName.toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
