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
