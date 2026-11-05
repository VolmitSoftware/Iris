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
package art.arcane.iris.engine.procedural.part;

import art.arcane.volmlib.util.math.RNG;

/**
 * A stem traced as a path of closely spaced samples, each with a center, a direction and a radius, then swept into
 * blocks: one block per sample while it is thin, a slice of its cross-section once it is thicker.
 */
final class StemShape implements PartShape {
    static final double LONGEST_BLOCKS = 256;
    private static final double SAMPLE_SPACING_BLOCKS = 0.25;
    private static final double THIN_RADIUS_BLOCKS = 0.5;
    private static final double SWAY_FREQUENCY = 0.35;

    private final double length;
    private final double[][] centers;
    private final double[][] headings;
    private final double[] radii;
    private final double outward;

    private StemShape(double length, double[][] centers, double[][] headings, double[] radii, double outward) {
        this.length = length;
        this.centers = centers;
        this.headings = headings;
        this.radii = radii;
        this.outward = outward;
    }

    /** Traces the stem from {@code spot} without placing anything. */
    static StemShape trace(PartStem stem, PartSpot spot, RNG rng) {
        double length = stemLength(stem, spot, rng);
        double baseRadius = Math.max(0, stem.radius().sample(rng, spot.parentT()));
        ProceduralValue taper = stem.taper() == null ? null : stem.taper().fix(rng);
        double facing = stem.facing() != null ? stem.facing().sample(rng, spot.parentT())
                : spot.hasOutward() ? spot.outward() : rng.d(0, 360);
        double[] heading = startHeading(stem, spot, facing, rng);
        double[] toward = Vectors.across(heading, Vectors.direction(facing, 0));
        double[] beside = Vectors.normalize(Vectors.cross(heading, toward), toward);

        Bends bends = Bends.of(stem, spot, rng, length);
        double pathLength = Math.max(0, length - 1);
        int steps = Math.max(2, (int) Math.ceil(pathLength / SAMPLE_SPACING_BLOCKS));
        double step = pathLength / steps;
        double[][] centers = new double[steps + 1][];
        double[] radii = new double[steps + 1];
        double[] base = spot.origin().clone();
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            if (i > 0) {
                double middle = (i - 0.5) / steps;
                base = Vectors.addScaled(base, bends.heading(heading, toward, middle), step);
            }
            centers[i] = Vectors.add(base, bends.offset(toward, beside, t, pathLength));
            radii[i] = baseRadius * (taper == null ? 1 : Math.max(0, taper.at(t)));
        }
        return new StemShape(length, centers, directions(centers, heading), radii, facing);
    }

    /** Places the stem's blocks and records its tip. */
    void draw(PartFill fill, PartSection section, double roughness, double jitter) {
        for (int i = 0; i < centers.length; i++) {
            double t = i / (double) (centers.length - 1);
            PartCanvas.Axis axis = PartCanvas.Axis.along(headings[i]);
            if (radii[i] <= THIN_RADIUS_BLOCKS && roughness <= 0) {
                fill.place(round(centers[i][0]), round(centers[i][1]), round(centers[i][2]), 0, t, axis);
                continue;
            }
            sweepSlice(fill, i, t, axis, section, roughness, jitter);
        }
        double[] tip = centers[centers.length - 1];
        fill.canvas().addTip(new PartCanvas.Tip(round(tip[0]), round(tip[1]), round(tip[2]), headings[headings.length - 1], fill.role()));
    }

    private void sweepSlice(PartFill fill, int index, double t, PartCanvas.Axis axis, PartSection section, double roughness, double jitter) {
        double[] center = centers[index];
        double[] heading = headings[index];
        double radius = Math.max(THIN_RADIUS_BLOCKS, radii[index]);
        double[] across = Vectors.across(heading, new double[]{1, 0, 0});
        double[] beside = Vectors.cross(heading, across);
        double slab = SAMPLE_SPACING_BLOCKS * 0.5 + 0.5;
        boolean first = index == 0;
        boolean last = index == centers.length - 1;
        int squareWidth = Math.max(1, (int) Math.round(radius * 2));
        double squareShift = section == PartSection.SQUARE && squareWidth % 2 == 0 ? 0.5 : 0;
        int reach = (int) Math.ceil(radius * (1 + roughness) + 1.5);
        int cx = round(center[0]);
        int cy = round(center[1]);
        int cz = round(center[2]);
        for (int x = cx - reach; x <= cx + reach; x++) {
            for (int y = cy - reach; y <= cy + reach; y++) {
                for (int z = cz - reach; z <= cz + reach; z++) {
                    double[] offset = {x - center[0], y - center[1], z - center[2]};
                    double axial = Vectors.dot(offset, heading);
                    if (Math.abs(axial) > slab || (first && axial < -0.5) || (last && axial > 0.5)) {
                        continue;
                    }
                    double[] radial = Vectors.addScaled(offset, heading, -axial);
                    double u = Vectors.dot(radial, across) - squareShift;
                    double w = Vectors.dot(radial, beside) - squareShift;
                    double limit = section == PartSection.SQUARE ? squareWidth / 2.0 : radius;
                    double edge = Sections.norm(section, u, w);
                    if (roughness > 0) {
                        limit += (PartNoise.hash(x, y, z, fill.seed() + 9001L) - 0.5) * roughness * (radius + 1);
                    }
                    boolean core = x == cx && y == cy && z == cz;
                    if (!core && edge > limit) {
                        continue;
                    }
                    if (!core && jitter > 0 && edge > limit - 1 && PartNoise.hash(x, y, z, fill.seed() + 77L) < jitter * 0.5) {
                        continue;
                    }
                    fill.place(x, y, z, Math.min(edge, limit) / (limit + 1), t, axis);
                }
            }
        }
    }

    @Override
    public PartSpot spotAt(double t) {
        double position = Math.max(0, Math.min(1, t)) * (centers.length - 1);
        int index = (int) Math.floor(position);
        int next = Math.min(centers.length - 1, index + 1);
        double blend = position - index;
        double[] center = Vectors.add(Vectors.scale(centers[index], 1 - blend), Vectors.scale(centers[next], blend));
        double[] heading = headings[index];
        double outwardHere = Vectors.isVertical(heading) ? outward : Vectors.azimuth(heading);
        return new PartSpot(center, heading.clone(), t, length, outwardHere);
    }

    @Override
    public double length() {
        return length;
    }

    @Override
    public double defaultAt() {
        return 1;
    }

    private static double stemLength(PartStem stem, PartSpot spot, RNG rng) {
        double length = stem.lengthScale() > 0 && spot.parentLength() > 0
                ? spot.parentLength() * stem.lengthScale()
                : stem.length().sample(rng, spot.parentT());
        return Math.max(1, Math.min(LONGEST_BLOCKS, length));
    }

    private static double[] startHeading(PartStem stem, PartSpot spot, double facing, RNG rng) {
        if (stem.pitch() == null && stem.azimuth() == null) {
            return spot.heading().clone();
        }
        double pitch = stem.pitch() != null ? stem.pitch().sample(rng, spot.parentT()) : Vectors.pitch(spot.heading());
        double azimuth = stem.azimuth() != null ? stem.azimuth().sample(rng, spot.parentT())
                : Vectors.isVertical(spot.heading()) ? facing : Vectors.azimuth(spot.heading());
        return Vectors.direction(azimuth, pitch);
    }

    /** The direction at each sample, from its neighbors, so offsets such as lean and corkscrew turn the slices too. */
    private static double[][] directions(double[][] centers, double[] fallback) {
        double[][] headings = new double[centers.length][];
        for (int i = 0; i < centers.length; i++) {
            double[] before = centers[Math.max(0, i - 1)];
            double[] after = centers[Math.min(centers.length - 1, i + 1)];
            headings[i] = Vectors.normalize(Vectors.subtract(after, before), i > 0 ? headings[i - 1] : fallback);
        }
        return headings;
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }

    /** Everything that pulls a stem off a straight line, rolled once per stem. */
    private record Bends(double leanTangent, ProceduralValue leanCurve, double bendRadians, ProceduralValue bendCurve,
                         double droop, double sway, double wave, double wavePeriods, double wavePhase,
                         ProceduralValue helixRadius, double helixTurns, long swaySeed) {
        static Bends of(PartStem stem, PartSpot spot, RNG rng, double length) {
            double t = spot.parentT();
            double lean = Math.max(-85, Math.min(85, sample(stem.lean(), rng, t)));
            return new Bends(Math.tan(Math.toRadians(lean)), fixed(stem.leanCurve(), rng),
                    Math.toRadians(sample(stem.bend(), rng, t)), fixed(stem.bendCurve(), rng),
                    sample(stem.droop(), rng, t), sample(stem.sway(), rng, t),
                    sample(stem.wave(), rng, t), sample(stem.wavePeriods(), rng, t), rng.d(0, Math.PI * 2),
                    fixed(stem.helixRadius(), rng), sample(stem.helixTurns(), rng, t), rng.nextLong());
        }

        double[] heading(double[] start, double[] toward, double t) {
            if (bendRadians == 0) {
                return start;
            }
            double angle = bendRadians * progress(bendCurve, t);
            return Vectors.add(Vectors.scale(start, Math.cos(angle)), Vectors.scale(toward, Math.sin(angle)));
        }

        double[] offset(double[] toward, double[] beside, double t, double pathLength) {
            double[] offset = Vectors.scale(toward, pathLength * leanTangent * progress(leanCurve, t));
            offset[1] -= droop * pathLength * t * t;
            double helix = helixRadius == null ? 0 : helixRadius.at(t);
            if (helix != 0) {
                double angle = Math.PI * 2 * helixTurns * t;
                offset = Vectors.addScaled(offset, toward, Math.cos(angle) * helix);
                offset = Vectors.addScaled(offset, beside, Math.sin(angle) * helix);
            }
            if (wave != 0) {
                offset = Vectors.addScaled(offset, beside, wave * Math.sin(Math.PI * 2 * wavePeriods * t + wavePhase));
            }
            if (sway != 0) {
                double along = t * pathLength * SWAY_FREQUENCY;
                offset = Vectors.addScaled(offset, toward, sway * (PartNoise.line(along, swaySeed) * 2 - 1));
                offset = Vectors.addScaled(offset, beside, sway * (PartNoise.line(along, swaySeed + 1) * 2 - 1));
            }
            return offset;
        }

        private static double progress(ProceduralValue curve, double t) {
            return curve == null ? t : curve.at(t);
        }

        private static double sample(ProceduralValue value, RNG rng, double t) {
            return value == null ? 0 : value.sample(rng, t);
        }

        private static ProceduralValue fixed(ProceduralValue value, RNG rng) {
            return value == null ? null : value.fix(rng);
        }
    }
}
