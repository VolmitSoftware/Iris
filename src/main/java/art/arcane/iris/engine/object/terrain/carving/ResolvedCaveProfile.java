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

package art.arcane.iris.engine.object.terrain.carving;

import art.arcane.iris.builtin.carve.CaveFieldModule;
import art.arcane.iris.builtin.carve.CaveProfile;
import art.arcane.iris.core.modules.IrisValues;
import art.arcane.iris.engine.object.noise.IrisGeneratorStyle;
import art.arcane.iris.engine.object.noise.IrisStyledRange;
import art.arcane.iris.engine.object.value.IrisRange;

import java.util.List;

/**
 * The parts of a {@link CaveProfile} that the carvers read as Iris's own noise and range types, made once per carver:
 * the generator styles that build the cave fields, the ranges and the extra field modules. The carvers read these
 * through Iris's generators because those keep Iris's coordinate caches and fast signed reads, which the cave
 * hot paths rely on.
 */
public final class ResolvedCaveProfile {
    private final IrisRange verticalRange;
    private final IrisGeneratorStyle baseDensityStyle;
    private final IrisGeneratorStyle detailDensityStyle;
    private final IrisGeneratorStyle warpStyle;
    private final IrisGeneratorStyle surfaceBreakStyle;
    private final IrisStyledRange densityThreshold;
    private final List<Module> modules;

    private ResolvedCaveProfile(CaveProfile settings) {
        this.verticalRange = IrisValues.range(settings.verticalRange());
        this.baseDensityStyle = IrisValues.generatorStyle(settings.baseDensityStyle());
        this.detailDensityStyle = IrisValues.generatorStyle(settings.detailDensityStyle());
        this.warpStyle = IrisValues.generatorStyle(settings.warpStyle());
        this.surfaceBreakStyle = IrisValues.generatorStyle(settings.surfaceBreakStyle());
        this.densityThreshold = IrisValues.styledRange(settings.densityThreshold());
        this.modules = settings.modules().stream().map(Module::new).toList();
    }

    public static ResolvedCaveProfile of(CaveProfile settings) {
        return new ResolvedCaveProfile(settings);
    }

    public IrisRange verticalRange() {
        return verticalRange;
    }

    public IrisGeneratorStyle baseDensityStyle() {
        return baseDensityStyle;
    }

    public IrisGeneratorStyle detailDensityStyle() {
        return detailDensityStyle;
    }

    public IrisGeneratorStyle warpStyle() {
        return warpStyle;
    }

    public IrisGeneratorStyle surfaceBreakStyle() {
        return surfaceBreakStyle;
    }

    public IrisStyledRange densityThreshold() {
        return densityThreshold;
    }

    public List<Module> modules() {
        return modules;
    }

    /** One {@link CaveFieldModule} with its style and range as Iris's types. */
    public static final class Module {
        private final IrisGeneratorStyle style;
        private final double weight;
        private final double threshold;
        private final IrisRange verticalRange;
        private final boolean invert;

        private Module(CaveFieldModule settings) {
            this.style = IrisValues.generatorStyle(settings.style());
            this.weight = settings.weight();
            this.threshold = settings.threshold();
            this.verticalRange = IrisValues.range(settings.verticalRange());
            this.invert = settings.invert();
        }

        public IrisGeneratorStyle style() {
            return style;
        }

        public double weight() {
            return weight;
        }

        public double threshold() {
            return threshold;
        }

        public IrisRange verticalRange() {
            return verticalRange;
        }

        public boolean invert() {
            return invert;
        }
    }
}
