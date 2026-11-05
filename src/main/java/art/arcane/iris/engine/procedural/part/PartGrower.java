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

import java.util.ArrayList;
import java.util.List;

/**
 * Grows a part tree onto a canvas: each part is drawn from where it sits on its parent, then its children grow from it.
 * Every part draws from its own seed, taken from its parent's in order, so adding a part only changes what grows after it.
 */
final class PartGrower {
    private static final double GOLDEN_ANGLE_DEGREES = 137.50776405003785;
    private static final int MOST_COPIES = 256;
    private static final int DEEPEST_NESTING = 24;

    private final PartCanvas canvas;
    private final String defaultRole;
    private final PartOutline outline;

    PartGrower(PartCanvas canvas, String defaultRole, PartOutline outline) {
        this.canvas = canvas;
        this.defaultRole = defaultRole;
        this.outline = outline;
    }

    void growRoots(List<ProceduralPart> parts, long seed) {
        RNG seeds = new RNG(seed);
        for (ProceduralPart part : parts) {
            growChild(part, PartShape.ROOT, canvas.role(defaultRole), seeds.nextLong(), 0);
        }
    }

    /** Grows a part that sits on its parent where its own 'at' and 'offset' say. */
    private void growChild(ProceduralPart part, PartShape parent, int parentRole, long seed, int nesting) {
        if (part == null || canvas.isFull()) {
            return;
        }
        RNG rng = new RNG(seed);
        double at = part.at() == null ? parent.defaultAt() : part.at().sample(rng, 0);
        PartSpot spot = parent.spotAt(at);
        if (part.offset() != null) {
            spot = spot.movedBy(part.offset().roll(rng));
        }
        grow(part, parent, spot, parentRole, rng, List.of(), nesting);
    }

    /** Grows a part at {@code spot}, then its children, then any branching it repeats from its own copy. */
    private void grow(ProceduralPart part, PartShape parent, PartSpot spot, int parentRole, RNG rng,
                      List<Repeat> repeats, int nesting) {
        if (nesting > DEEPEST_NESTING || canvas.isFull()) {
            return;
        }
        int role = part.role() == null || part.role().isBlank() ? parentRole : canvas.role(part.role());
        PartFill fill = PartFill.of(canvas, part, outline.idOf(part), role, rng.nextLong());
        PartShape shape = parent;
        if (part.stem() != null) {
            StemShape stem = StemShape.trace(part.stem(), spot, rng);
            stem.draw(fill, part.stem().section(), part.stem().roughness(), part.stem().jitter());
            shape = stem;
        } else if (part.blob() != null) {
            BlobShape blob = BlobShape.trace(part.blob(), spot, rng);
            blob.draw(fill, part.blob().roughness(), part.blob().jitter());
            shape = blob;
        } else if (part.branches() != null) {
            branch(part.branches(), parent, role, rng, part.branches().depth(), nesting);
        } else if (part.cluster() != null) {
            cluster(part.cluster(), spot, role, rng, nesting);
        }

        for (ProceduralPart child : part.children()) {
            growChild(child, shape, role, rng.nextLong(), nesting + 1);
        }
        for (Repeat repeat : repeats) {
            branch(repeat.branches(), shape, role, rng, repeat.depthLeft(), nesting);
        }
    }

    private void branch(PartBranches branches, PartShape parent, int role, RNG rng, int depthLeft, int nesting) {
        if (branches.part() == null || depthLeft <= 0) {
            return;
        }
        List<Double> positions = branchPositions(branches, parent, rng);
        List<Repeat> repeats = depthLeft > 1 ? List.of(new Repeat(branches, depthLeft - 1)) : List.of();
        double azimuthOffset = branches.azimuthOffset().sample(rng, 0);
        for (int index = 0; index < positions.size(); index++) {
            double t = positions.get(index);
            PartSpot onParent = parent.spotAt(t);
            double pitch = branches.pitch().sample(rng, t);
            double[] heading = branchHeading(branches, onParent, index, positions.size(), azimuthOffset, pitch, rng);
            PartSpot spot = new PartSpot(onParent.origin(), heading, t, parent.length(), Vectors.azimuth(heading));
            RNG copyRng = new RNG(rng.nextLong());
            ProceduralPart copy = branches.part();
            if (copy.offset() != null) {
                spot = spot.movedBy(copy.offset().roll(copyRng));
            }
            grow(copy, parent, spot, role, copyRng, repeats, nesting + 1);
        }
    }

    private static List<Double> branchPositions(PartBranches branches, PartShape parent, RNG rng) {
        ProceduralSpan along = branches.along();
        List<Double> positions = new ArrayList<>();
        if (branches.chance() != null) {
            double blocks = Math.max(1, parent.length());
            int first = (int) Math.ceil(along.from() * blocks);
            int last = (int) Math.floor(along.to() * blocks);
            for (int block = first; block <= last && positions.size() < MOST_COPIES; block++) {
                double t = Math.min(1, block / blocks);
                if (rng.nextDouble() < branches.chance().at(t)) {
                    positions.add(t);
                }
            }
            return positions;
        }
        int count = Math.min(MOST_COPIES, Math.max(0, (int) Math.round(branches.count().sample(rng, 0))));
        for (int index = 0; index < count; index++) {
            double fraction = along.to() - along.from() < 1e-9 ? 0 : (index + rng.d(0.25, 0.75)) / count;
            positions.add(along.from() + (along.to() - along.from()) * fraction);
        }
        return positions;
    }

    private static double[] branchHeading(PartBranches branches, PartSpot onParent, int index, int count,
                                          double azimuthOffset, double pitch, RNG rng) {
        double parentAzimuth = Vectors.isVertical(onParent.heading()) && onParent.hasOutward()
                ? onParent.outward() : Vectors.azimuth(onParent.heading());
        double turn = switch (branches.azimuth()) {
            case RANDOM -> rng.d(0, 360);
            case GOLDEN -> index * GOLDEN_ANGLE_DEGREES;
            case ALTERNATE -> (index % 2) * 180.0;
            case WHORL -> (index % branches.whorl()) * (360.0 / branches.whorl());
            case EVEN -> index * (360.0 / Math.max(1, count));
            case FAN -> branches.fan() * (index - (count - 1) / 2.0);
        };
        boolean relative = branches.relative() || branches.azimuth() == BranchAzimuth.FAN;
        double azimuth = (relative ? parentAzimuth : 0) + turn + azimuthOffset;
        double finalPitch = branches.relative() ? Vectors.pitch(onParent.heading()) + pitch : pitch;
        return Vectors.direction(azimuth, Math.max(-90, Math.min(90, finalPitch)));
    }

    private void cluster(PartCluster cluster, PartSpot spot, int role, RNG rng, int nesting) {
        if (cluster.part() == null) {
            return;
        }
        int count = Math.min(MOST_COPIES, Math.max(0, (int) Math.round(cluster.count().sample(rng, 0))));
        double radius = Math.max(0, cluster.radius().sample(rng, 0));
        double phase = rng.d(0, 360);
        for (int index = 0; index < count; index++) {
            Placed placed = placeInCluster(cluster.layout(), index, count, radius, phase, rng);
            double[] origin = Vectors.add(spot.origin(), Vectors.scale(Vectors.direction(placed.azimuth(), 0), placed.distance()));
            RNG copyRng = new RNG(rng.nextLong());
            double fromCenter = radius <= 0 ? 0 : Math.min(1, placed.distance() / radius);
            double tilt = cluster.tilt().sample(copyRng, fromCenter);
            double[] heading = tilt == 0 ? spot.heading().clone() : Vectors.tilted(spot.heading(), placed.azimuth(), tilt);
            PartSpot copySpot = new PartSpot(origin, heading, fromCenter, Math.max(1, radius), placed.azimuth());
            ProceduralPart copy = cluster.part();
            if (copy.offset() != null) {
                copySpot = copySpot.movedBy(copy.offset().roll(copyRng));
            }
            grow(copy, PartShape.ROOT, copySpot, role, copyRng, List.of(), nesting + 1);
        }
    }

    private static Placed placeInCluster(ClusterLayout layout, int index, int count, double radius, double phase, RNG rng) {
        return switch (layout) {
            case RANDOM -> new Placed(rng.d(0, 360), radius * Math.sqrt(rng.nextDouble()));
            case RING -> new Placed(phase + index * 360.0 / Math.max(1, count), radius);
            case GOLDEN -> new Placed(phase + index * GOLDEN_ANGLE_DEGREES, radius * Math.sqrt((index + 0.5) / Math.max(1, count)));
            case LINE -> {
                double lane = count <= 1 ? 0 : -radius + 2 * radius * index / (count - 1.0);
                yield new Placed(lane < 0 ? phase + 180 : phase, Math.abs(lane));
            }
        };
    }

    /** A branching that repeats on the copies it grows, with how many more times it may. */
    private record Repeat(PartBranches branches, int depthLeft) {
    }

    /** Where a cluster copy sits: which way from the center, and how far. */
    private record Placed(double azimuth, double distance) {
    }
}
