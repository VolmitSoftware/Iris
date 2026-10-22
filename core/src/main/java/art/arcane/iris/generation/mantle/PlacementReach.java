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

package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.block.VectorMap;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.structure.object.IrisObjectTranslate;
import art.arcane.iris.structure.object.IrisObjectVacuum;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.Vector3i;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Objects;

public record PlacementReach(int horizontal, int readMargin) {
    public static final PlacementReach NONE = new PlacementReach(0, 0);
    private static final int SPIN_PERIOD = 360;
    private static final int MAXIMUM_ROTATION_KERNELS = 1024;
    private static final double ROUNDING_SLACK = 1.0E-9D;

    public PlacementReach {
        if (horizontal < 0 || readMargin < 0) {
            throw new IllegalArgumentException("Placement reach must be non-negative: " + horizontal + "+" + readMargin);
        }
    }

    public static PlacementReach of(IrisObject object, IrisObjectPlacement placement, double scale) {
        return of(Extent.of(object).scaled(scale), placement);
    }

    public static PlacementReach of(Extent extent, IrisObjectPlacement placement) {
        return profile(placement).of(extent);
    }

    public static PlacementReach anchored(Extent extent, IrisObjectPlacement placement) {
        return profile(placement).anchored(extent);
    }

    public static Profile profile(IrisObjectPlacement placement) {
        return new Profile(Objects.requireNonNull(placement, "placement"));
    }

    public int input() {
        return saturate((long) horizontal + readMargin);
    }

    public PlacementReach union(PlacementReach other) {
        int unionHorizontal = Math.max(horizontal, other.horizontal);
        int unionInput = Math.max(input(), other.input());
        return new PlacementReach(unionHorizontal, unionInput - unionHorizontal);
    }

    public PlacementReach withReadMargin(int minimumReadMargin) {
        return minimumReadMargin <= readMargin ? this : new PlacementReach(horizontal, minimumReadMargin);
    }

    public static int supportReadMargin(int surfaceSupportBuffer) {
        return 1 + Math.max(0, surfaceSupportBuffer);
    }

    private static int roundedBound(double maximumMagnitude) {
        return saturate((long) Math.floor(maximumMagnitude + 0.5D + ROUNDING_SLACK));
    }

    private static int saturate(long value) {
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
    }

    private static IrisBlockVector corner(Extent extent, int corner) {
        return new IrisBlockVector(
                (corner & 1) == 0 ? extent.x().minimum() : extent.x().maximum(),
                (corner & 2) == 0 ? extent.y().minimum() : extent.y().maximum(),
                (corner & 4) == 0 ? extent.z().minimum() : extent.z().maximum());
    }

    public static final class Profile {
        private final IrisObjectRotation rotation;
        private final IrisBlockVector translation;
        private final boolean rotating;
        private final boolean radial;
        private final int[] spinsX;
        private final int[] spinsY;
        private final int[] spinsZ;
        private final int additive;
        private final int readMargin;

        private Profile(IrisObjectPlacement placement) {
            IrisObjectRotation configuredRotation = placement.getRotation();
            IrisObjectTranslate translate = placement.getTranslate();
            this.rotation = configuredRotation;
            this.translation = translate != null && translate.canTranslate()
                    ? new IrisBlockVector(translate.getX(), translate.getY(), translate.getZ())
                    : null;
            this.rotating = configuredRotation != null && configuredRotation.canRotate();
            this.spinsX = rotating ? distinctSpins(configuredRotation, Axis.X) : new int[]{0};
            this.spinsY = rotating ? distinctSpins(configuredRotation, Axis.Y) : new int[]{0};
            this.spinsZ = rotating ? distinctSpins(configuredRotation, Axis.Z) : new int[]{0};
            long kernels = (long) spinsX.length * spinsY.length * spinsZ.length;
            this.radial = rotating && (placement.isRotateTowardsSlope() || kernels > MAXIMUM_ROTATION_KERNELS);
            this.additive = saturate((long) warpReach(placement.getWarp()) + vacuumReach(placement));
            this.readMargin = supportReadMargin(placement.getSurfaceSupportBuffer());
        }

        public PlacementReach of(Extent extent) {
            Objects.requireNonNull(extent, "extent");
            return new PlacementReach(saturate((long) rotatedReach(extent) + additive), readMargin);
        }

        public PlacementReach anchored(Extent extent) {
            Objects.requireNonNull(extent, "extent");
            long span = Math.max(extent.x().span(), extent.z().span());
            return new PlacementReach(saturate(span + additive), readMargin);
        }

        private int rotatedReach(Extent extent) {
            if (!rotating) {
                return unrotatedReach(extent);
            }
            if (radial) {
                return radialReach(extent);
            }
            double maximum = 0D;
            for (int spinX : spinsX) {
                for (int spinY : spinsY) {
                    for (int spinZ : spinsZ) {
                        maximum = Math.max(maximum, rotatedCornerMaximum(extent, spinX, spinY, spinZ));
                    }
                }
            }
            return roundedBound(maximum);
        }

        private double rotatedCornerMaximum(Extent extent, int spinX, int spinY, int spinZ) {
            IrisBlockVector offset = translation == null ? null : rotation.rotate(translation, spinX, spinY, spinZ);
            double maximum = 0D;
            for (int corner = 0; corner < 8; corner++) {
                IrisBlockVector rotated = rotation.rotate(corner(extent, corner), spinX, spinY, spinZ);
                double x = offset == null ? rotated.getX() : rotated.getX() + offset.getX();
                double z = offset == null ? rotated.getZ() : rotated.getZ() + offset.getZ();
                maximum = Math.max(maximum, Math.max(Math.abs(x), Math.abs(z)));
            }
            return maximum;
        }

        private int unrotatedReach(Extent extent) {
            double translateX = translation == null ? 0D : translation.getX();
            double translateZ = translation == null ? 0D : translation.getZ();
            double x = Math.max(Math.abs(extent.x().minimum() + translateX), Math.abs(extent.x().maximum() + translateX));
            double z = Math.max(Math.abs(extent.z().minimum() + translateZ), Math.abs(extent.z().maximum() + translateZ));
            return roundedBound(Math.max(x, z));
        }

        private int radialReach(Extent extent) {
            boolean vertical = rotation.canRotateX() || rotation.canRotateZ();
            double translateX = translation == null ? 0D : translation.getX();
            double translateY = translation == null ? 0D : translation.getY();
            double translateZ = translation == null ? 0D : translation.getZ();
            double maximum = 0D;
            for (int corner = 0; corner < 8; corner++) {
                IrisBlockVector position = corner(extent, corner);
                double x = position.getX() + translateX;
                double y = vertical ? position.getY() + translateY : 0D;
                double z = position.getZ() + translateZ;
                maximum = Math.max(maximum, Math.sqrt((x * x) + (y * y) + (z * z)));
            }
            return roundedBound(maximum);
        }

        private static int warpReach(IrisGeneratorStyle warp) {
            if (warp == null || warp.isFlat()) {
                return 0;
            }
            return saturate((long) Math.ceil(Math.abs(warp.getMultiplier()) / 2D));
        }

        private static int vacuumReach(IrisObjectPlacement placement) {
            if (!IrisObjectVacuum.isVacuumMode(placement.getMode())) {
                return 0;
            }
            return IrisObjectVacuum.resolveRadius(placement.getMode(), placement.getVacuumSettings());
        }

        private static int[] distinctSpins(IrisObjectRotation rotation, Axis axis) {
            boolean rotates = switch (axis) {
                case X -> rotation.canRotateX();
                case Y -> rotation.canRotateY();
                case Z -> rotation.canRotateZ();
            };
            if (!rotates) {
                return new int[]{0};
            }
            LongOpenHashSet angles = new LongOpenHashSet();
            IntArrayList spins = new IntArrayList();
            for (int spin = 0; spin < SPIN_PERIOD; spin++) {
                double angle = switch (axis) {
                    case X -> rotation.getXRotation(spin);
                    case Y -> rotation.getYRotation(spin);
                    case Z -> rotation.getZRotation(spin);
                };
                if (angles.add(Double.doubleToLongBits(angle))) {
                    spins.add(spin);
                }
            }
            return spins.toIntArray();
        }
    }

    private enum Axis {
        X,
        Y,
        Z
    }

    public record Extent(Span x, Span y, Span z) {
        public Extent {
            Objects.requireNonNull(x, "x");
            Objects.requireNonNull(y, "y");
            Objects.requireNonNull(z, "z");
        }

        public static Extent of(IrisObject object) {
            Objects.requireNonNull(object, "object");
            Vector3i center = object.getCenter();
            int[] keys = keyBounds(object.getBlocks());
            return new Extent(
                    Span.of(object.getW(), center.getBlockX(), keys[0], keys[3]),
                    Span.of(object.getH(), center.getBlockY(), keys[1], keys[4]),
                    Span.of(object.getD(), center.getBlockZ(), keys[2], keys[5]));
        }

        public Extent scaled(double scale) {
            if (scale == 1D) {
                return this;
            }
            return new Extent(x.scaled(scale), y.scaled(scale), z.scaled(scale));
        }

        private static int[] keyBounds(VectorMap<NativeBlockState> blocks) {
            int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                    Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            VectorMap<NativeBlockState>.Cursor cursor = blocks.cursor();
            while (cursor.next()) {
                IrisBlockVector key = cursor.key();
                bounds[0] = Math.min(bounds[0], key.getBlockX());
                bounds[1] = Math.min(bounds[1], key.getBlockY());
                bounds[2] = Math.min(bounds[2], key.getBlockZ());
                bounds[3] = Math.max(bounds[3], key.getBlockX());
                bounds[4] = Math.max(bounds[4], key.getBlockY());
                bounds[5] = Math.max(bounds[5], key.getBlockZ());
            }
            return bounds;
        }
    }

    public record Span(int size, int center, int minimum, int maximum, boolean framed) {
        public Span {
            if (minimum > -center || maximum < size - 1 - center) {
                throw new IllegalArgumentException("Span must cover the object frame: " + minimum + ".." + maximum
                        + " for size " + size + " centered at " + center);
            }
        }

        public static Span of(int size, int center, int keyMinimum, int keyMaximum) {
            int frameMinimum = -center;
            int frameMaximum = size - 1 - center;
            boolean framed = keyMinimum >= frameMinimum && keyMaximum <= frameMaximum;
            return new Span(size, center, Math.min(keyMinimum, frameMinimum), Math.max(keyMaximum, frameMaximum), framed);
        }

        public int span() {
            return maximum - minimum;
        }

        public Span scaled(double scale) {
            int scaledSize = (int) Math.ceil(size * scale);
            int scaledCenter = scaledSize / 2;
            int frameMinimum = -scaledCenter;
            int frameMaximum = scaledSize - 1 - scaledCenter;
            if (framed) {
                return new Span(scaledSize, scaledCenter, frameMinimum, frameMaximum, true);
            }
            int scaledMinimum = (int) Math.floor((minimum + center) * scale) - scaledCenter;
            double centerExcess = Math.max(0D, center - (size / 2D));
            int scaledMaximum = (int) Math.ceil(((maximum + 1 + centerExcess) * scale) + 0.5D + ROUNDING_SLACK) - 1;
            return new Span(scaledSize, scaledCenter, Math.min(scaledMinimum, frameMinimum),
                    Math.max(scaledMaximum, frameMaximum), false);
        }
    }
}
