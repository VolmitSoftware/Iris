/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.uniques;

import com.volmit.iris.engine.object.NoiseStyle;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.interpolation.InterpolationMethod;
import com.volmit.iris.util.noise.CNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class UFeatureMeta {
    private KMap<String, UFeatureMetaInterpolator> interpolators;
    private KMap<String, UFeatureMetaGenerator> generators;
    private String feature;

    public void registerInterpolator(String key, InterpolationMethod method, double radius) {
        if (interpolators == null) {
            interpolators = new KMap<>();
        }
        interpolators.put(key, new UFeatureMetaInterpolator(method, radius));
    }

    public void registerGenerator(String key, CNG cng) {
        if (generators == null) {
            generators = new KMap<>();
        }
        generators.put(key, buildGenerator(cng));
    }

    public UFeatureMetaGenerator buildGenerator(CNG cng) {
        UFeatureMetaGenerator g = new UFeatureMetaGenerator();
        g.setScale(cng.getScale());
        g.setOctaves(cng.getOct());

        if (cng.getFracture() != null) {
            g.setFracture(buildGenerator(cng.getFracture()));
            g.setFractureMultiplier(cng.getFscale());
        }

        if (cng.getChildren() != null && cng.getChildren().isNotEmpty()) {
            g.setChildren(new KList<>());

            for (CNG i : cng.getChildren()) {
                g.getChildren().add(buildGenerator(i));
            }
        }

        if (cng.getInjector() == CNG.ADD) {
            g.setParentInject("add");
        } else if (cng.getInjector() == CNG.SRC_SUBTRACT) {
            g.setParentInject("src_subtract");
        } else if (cng.getInjector() == CNG.DST_SUBTRACT) {
            g.setParentInject("dst_subtract");
        } else if (cng.getInjector() == CNG.MULTIPLY) {
            g.setParentInject("multiply");
        } else if (cng.getInjector() == CNG.MAX) {
            g.setParentInject("max");
        } else if (cng.getInjector() == CNG.MIN) {
            g.setParentInject("min");
        } else if (cng.getInjector() == CNG.SRC_MOD) {
            g.setParentInject("src_mod");
        } else if (cng.getInjector() == CNG.SRC_POW) {
            g.setParentInject("src_pow");
        } else if (cng.getInjector() == CNG.DST_MOD) {
            g.setParentInject("dst_mod");
        } else if (cng.getInjector() == CNG.DST_POW) {
            g.setParentInject("dst_pow");
        }

        return g;
    }

    public boolean isEmpty() {
        return (interpolators == null || interpolators.isEmpty()) && (generators == null || generators.isEmpty());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class UFeatureMetaInterpolator {
        private InterpolationMethod interpolator;
        private double radius;
    }

    @Data
    @NoArgsConstructor
    static class UFeatureMetaGenerator {
        private NoiseStyle style;
        private int octaves = 1;
        private double scale = 1;
        private String parentInject;
        private UFeatureMetaGenerator fracture;
        private Double fractureMultiplier;
        private List<UFeatureMetaGenerator> children;
    }
}
