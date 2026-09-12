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

package art.arcane.iris.generation.decoration.formation;


public final class FormationProfiles {
    private FormationProfiles() {
    }

    public static double radiusAt(IrisFormation f, double baseRadius, double topRadius, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return switch (f.getProfile()) {
            case CONSTANT -> baseRadius;
            case LINEAR -> baseRadius + (topRadius - baseRadius) * clamped;
            case TAPER -> baseRadius + (topRadius - baseRadius) * smoothstep(clamped);
            case PARABOLIC -> parabolic(f, baseRadius, topRadius, clamped);
            case BULGE -> baseRadius + (Math.max(baseRadius, topRadius) - baseRadius) * bell(clamped) + (topRadius - baseRadius) * clamped * 0.25;
        };
    }

    private static double parabolic(IrisFormation f, double baseRadius, double topRadius, double t) {
        double waist = Math.max(0.0, Math.min(1.0, f.getProfileWaist()));
        double floor = Math.max(0.0, Math.min(1.0, f.getProfileWaistFloor()));
        double denom = Math.pow(Math.max(waist, 1.0 - waist), 2);
        if (denom == 0) {
            denom = 1;
        }
        double envelope = baseRadius + (topRadius - baseRadius) * t;
        double pinch = floor + (1.0 - floor) * (Math.pow(t - waist, 2) / denom);
        return Math.max(0.5, envelope * pinch);
    }

    private static double smoothstep(double t) {
        return 3.0 * t * t - 2.0 * t * t * t;
    }

    private static double bell(double t) {
        return Math.exp(-Math.pow(t - 0.5, 2) / (2.0 * 0.22 * 0.22));
    }
}
