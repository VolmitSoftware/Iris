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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.engine.object.terrain.carving.ResolvedCaveProfile;
import art.arcane.iris.engine.object.value.IrisRange;
import art.arcane.iris.util.project.noise.CNG;

final class CaveFieldModuleState {
    final CNG density;
    final int minY;
    final int maxY;
    final double weight;
    final double threshold;
    final boolean invert;
    final double minContribution;
    final double maxContribution;

    CaveFieldModuleState(ResolvedCaveProfile.Module module, CNG density) {
        IrisRange range = module.verticalRange();
        this.density = density;
        this.minY = (int) Math.floor(range.getMin());
        this.maxY = (int) Math.ceil(range.getMax());
        this.weight = module.weight();
        this.threshold = module.threshold();
        this.invert = module.invert();
        double rawMin = invert ? threshold - 1D : -1D - threshold;
        double rawMax = invert ? threshold + 1D : 1D - threshold;
        this.minContribution = rawMin * weight;
        this.maxContribution = rawMax * weight;
    }

    double sample(double x, int y, double z) {
        double sampled = density.noiseFastSigned3D(x, y, z);
        return invert ? (threshold - sampled) * weight : (sampled - threshold) * weight;
    }

    double sample(double x, double y, double z) {
        double sampled = density.noiseFastSigned3D(x, y, z);
        return invert ? (threshold - sampled) * weight : (sampled - threshold) * weight;
    }
}
