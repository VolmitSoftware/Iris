package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.WorldgenExportTargets;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class VanillaNoiseModel {
    private static final double SECOND_SAMPLER_INPUT_FACTOR = 1.0181268882175227D;
    private static final double TARGET_DEVIATION = 0.3333333333333333D;
    private static final double OCTAVE_DEVIATION = 0.2702247831245211D;
    private static final double MIN_BASE_AMPLITUDE = 9.999999747378752E-6D;
    private static final double MAX_AMPLITUDE = 1000000.0D;
    private static final int MAX_OCTAVE = 32;
    private static final int MAX_OCTAVE_COUNT = 32;
    private static final String DEFAULT_NAMESPACE = "minecraft";
    private static final String OCTAVE_SEED_PREFIX = "octave_";

    private final SinglePrecisionField field;

    private VanillaNoiseModel(SinglePrecisionField field) {
        this.field = field;
    }

    public static VanillaNoiseModel create(String noiseId, int firstOctave, List<Double> amplitudes, long worldSeed,
                                           DataVersion target) {
        Objects.requireNonNull(target, "target");
        if (!WorldgenExportTargets.SUPPORTED.contains(target)) {
            throw WorldgenExportTargets.unsupported(target.getVersion());
        }
        return create(noiseId, OctaveProfile.fromAmplitudes(firstOctave, amplitudes), worldSeed);
    }

    public static VanillaNoiseModel create(String noiseId, OctaveProfile profile, long worldSeed) {
        Objects.requireNonNull(profile, "profile");
        return new VanillaNoiseModel(SinglePrecisionField.create(noiseSource(noiseId, worldSeed), profile));
    }

    public double sample(double x, double y, double z) {
        return field.sample(x, y, z);
    }

    private static XoroshiroSource noiseSource(String noiseId, long worldSeed) {
        String identifier = canonicalIdentifier(Objects.requireNonNull(noiseId, "noiseId"));
        return XoroshiroSource.fromWorldSeed(worldSeed).forkPositional().fromHashOf(identifier);
    }

    private static String canonicalIdentifier(String noiseId) {
        return noiseId.indexOf(':') >= 0 ? noiseId : DEFAULT_NAMESPACE + ":" + noiseId;
    }

    private static double spanDeviation(double[] amplitudes) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int index = 0; index < amplitudes.length; index++) {
            if (amplitudes[index] != 0.0D) {
                lowest = Math.min(lowest, index);
                highest = Math.max(highest, index);
            }
        }
        return 0.1D * (1.0D + 1.0D / (highest - lowest + 1));
    }

    public enum Normalization {
        ENABLED,
        DISABLED,
        LEGACY
    }

    public record OctaveProfile(int baseOctave, int octaveCount, List<Double> amplitudeModifiers, double baseAmplitude,
                                Normalization normalization) {
        public OctaveProfile {
            Objects.requireNonNull(normalization, "normalization");
            amplitudeModifiers = List.copyOf(Objects.requireNonNull(amplitudeModifiers, "amplitudeModifiers"));
            if (baseOctave < -MAX_OCTAVE || baseOctave > MAX_OCTAVE) {
                throw new IllegalArgumentException("Noise base octave " + baseOctave + " is outside [-" + MAX_OCTAVE + ", "
                        + MAX_OCTAVE + "]");
            }
            if (octaveCount < 1 || octaveCount > MAX_OCTAVE_COUNT) {
                throw new IllegalArgumentException("Noise octave count " + octaveCount + " is outside [1, " + MAX_OCTAVE_COUNT
                        + "]");
            }
            if (!amplitudeModifiers.isEmpty() && amplitudeModifiers.size() != octaveCount) {
                throw new IllegalArgumentException("Noise amplitude modifiers had size " + amplitudeModifiers.size()
                        + ", but octave count was " + octaveCount);
            }
            for (Double modifier : amplitudeModifiers) {
                if (!(modifier >= 0.0D && modifier <= MAX_AMPLITUDE)) {
                    throw new IllegalArgumentException("Noise amplitude modifier " + modifier + " is outside [0, "
                            + MAX_AMPLITUDE + "]");
                }
            }
            if (!(baseAmplitude >= MIN_BASE_AMPLITUDE && baseAmplitude <= MAX_AMPLITUDE)) {
                throw new IllegalArgumentException("Noise base amplitude " + baseAmplitude + " is outside ["
                        + MIN_BASE_AMPLITUDE + ", " + MAX_AMPLITUDE + "]");
            }
        }

        public static OctaveProfile fromAmplitudes(int firstOctave, List<Double> amplitudes) {
            Objects.requireNonNull(amplitudes, "amplitudes");
            return new OctaveProfile(firstOctave, amplitudes.size(), amplitudes, 1.0D, Normalization.DISABLED);
        }

        private double modifier(int octave) {
            return amplitudeModifiers.isEmpty() ? 1.0D : amplitudeModifiers.get(octave);
        }
    }

    private static final class SinglePrecisionField {
        private final GradientLattice[] lattices;
        private final double[] frequencies;
        private final float[] amplitudes;

        private SinglePrecisionField(GradientLattice[] lattices, double[] frequencies, float[] amplitudes) {
            this.lattices = lattices;
            this.frequencies = frequencies;
            this.amplitudes = amplitudes;
        }

        private static SinglePrecisionField create(XoroshiroSource source, OctaveProfile profile) {
            List<ActiveOctave> octaves = activeOctaves(profile);
            double normalization = normalizationFactor(profile, octaves);
            HashedSeedFactory firstSeeds = source.forkPositional();
            HashedSeedFactory secondSeeds = source.forkPositional();
            int layers = octaves.size() * 2;
            GradientLattice[] lattices = new GradientLattice[layers];
            double[] frequencies = new double[layers];
            float[] amplitudes = new float[layers];
            for (int index = 0; index < octaves.size(); index++) {
                ActiveOctave octave = octaves.get(index);
                String seed = OCTAVE_SEED_PREFIX + octave.octave();
                float layerAmplitude = (float) (normalization * octave.amplitude());
                lattices[index * 2] = new GradientLattice(firstSeeds.fromHashOf(seed));
                lattices[index * 2 + 1] = new GradientLattice(secondSeeds.fromHashOf(seed));
                frequencies[index * 2] = octave.frequency();
                frequencies[index * 2 + 1] = octave.frequency() * SECOND_SAMPLER_INPUT_FACTOR;
                amplitudes[index * 2] = layerAmplitude;
                amplitudes[index * 2 + 1] = layerAmplitude;
            }
            return new SinglePrecisionField(lattices, frequencies, amplitudes);
        }

        private double sample(double x, double y, double z) {
            float value = 0.0F;
            for (int index = 0; index < lattices.length; index++) {
                double frequency = frequencies[index];
                value += amplitudes[index] * lattices[index].sampleFloat(x * frequency, y * frequency, z * frequency);
            }
            return value;
        }

        private static List<ActiveOctave> activeOctaves(OctaveProfile profile) {
            int count = profile.octaveCount();
            List<ActiveOctave> octaves = new ArrayList<>(count);
            double frequency = Math.pow(2.0D, profile.baseOctave());
            double amplitude = profile.baseAmplitude();
            if (profile.normalization() != Normalization.DISABLED) {
                amplitude *= Math.pow(0.5D, -(count - 1)) / (Math.pow(0.5D, -count) - 1.0D);
            }
            for (int index = 0; index < count; index++) {
                double modifier = profile.modifier(index);
                if (modifier != 0.0D) {
                    octaves.add(new ActiveOctave(profile.baseOctave() + index, frequency, amplitude * modifier));
                }
                frequency *= 2.0D;
                amplitude *= 0.5D;
            }
            return octaves;
        }

        private static double normalizationFactor(OctaveProfile profile, List<ActiveOctave> octaves) {
            double absoluteSum = octaves.stream().mapToDouble(octave -> Math.abs(octave.amplitude())).sum();
            double squaredDeviation = 0.0D;
            for (ActiveOctave octave : octaves) {
                double deviation = OCTAVE_DEVIATION * Math.abs(octave.amplitude());
                squaredDeviation += deviation * deviation;
            }
            double deviation = Math.sqrt(squaredDeviation);
            if (deviation == 0.0D) {
                return 0.0D;
            }
            double factor = absoluteSum * TARGET_DEVIATION / (deviation * Math.sqrt(2.0D));
            if (profile.normalization() != Normalization.LEGACY) {
                return factor;
            }
            double[] modifiers = new double[profile.octaveCount()];
            for (int index = 0; index < modifiers.length; index++) {
                modifiers[index] = profile.modifier(index);
            }
            return profile.baseAmplitude() * 0.5D * TARGET_DEVIATION / spanDeviation(modifiers);
        }

        private record ActiveOctave(int octave, double frequency, double amplitude) {
        }
    }

    private static final class GradientLattice {
        private static final double PERIOD = 33554432.0D;
        private static final double HALF_PERIOD = Math.nextDown(16777216.0D);
        private static final int[][] GRADIENTS = {
                {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
                {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
                {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
                {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1}
        };

        private final byte[] permutation;
        private final double offsetX;
        private final double offsetY;
        private final double offsetZ;

        private GradientLattice(XoroshiroSource source) {
            offsetX = source.nextDouble() * 256.0D;
            offsetY = source.nextDouble() * 256.0D;
            offsetZ = source.nextDouble() * 256.0D;
            permutation = new byte[256];
            for (int index = 0; index < 256; index++) {
                permutation[index] = (byte) index;
            }
            for (int index = 0; index < 256; index++) {
                int offset = source.nextInt(256 - index);
                byte swap = permutation[index];
                permutation[index] = permutation[index + offset];
                permutation[index + offset] = swap;
            }
        }

        private float sampleFloat(double x, double y, double z) {
            double shiftedX = wrap(x) + offsetX;
            double shiftedY = wrap(y) + offsetY;
            double shiftedZ = wrap(z) + offsetZ;
            int cellX = (int) Math.floor(shiftedX);
            int cellY = (int) Math.floor(shiftedY);
            int cellZ = (int) Math.floor(shiftedZ);
            float localX = (float) (shiftedX - cellX);
            float localY = (float) (shiftedY - cellY);
            float localZ = (float) (shiftedZ - cellZ);
            int low = hash(cellX);
            int high = hash(cellX + 1);
            int lowLow = hash(low + cellY);
            int lowHigh = hash(low + cellY + 1);
            int highLow = hash(high + cellY);
            int highHigh = hash(high + cellY + 1);
            float corner000 = dot(hash(lowLow + cellZ), localX, localY, localZ);
            float corner100 = dot(hash(highLow + cellZ), localX - 1.0F, localY, localZ);
            float corner010 = dot(hash(lowHigh + cellZ), localX, localY - 1.0F, localZ);
            float corner110 = dot(hash(highHigh + cellZ), localX - 1.0F, localY - 1.0F, localZ);
            float corner001 = dot(hash(lowLow + cellZ + 1), localX, localY, localZ - 1.0F);
            float corner101 = dot(hash(highLow + cellZ + 1), localX - 1.0F, localY, localZ - 1.0F);
            float corner011 = dot(hash(lowHigh + cellZ + 1), localX, localY - 1.0F, localZ - 1.0F);
            float corner111 = dot(hash(highHigh + cellZ + 1), localX - 1.0F, localY - 1.0F, localZ - 1.0F);
            float fadeX = fade(localX);
            float fadeY = fade(localY);
            float fadeZ = fade(localZ);
            float bottom = lerp(fadeY, lerp(fadeX, corner000, corner100), lerp(fadeX, corner010, corner110));
            float top = lerp(fadeY, lerp(fadeX, corner001, corner101), lerp(fadeX, corner011, corner111));
            return lerp(fadeZ, bottom, top);
        }

        private int hash(int value) {
            return permutation[value & 255] & 255;
        }

        private static double wrap(double coordinate) {
            if (coordinate >= -HALF_PERIOD && coordinate < HALF_PERIOD) {
                return coordinate;
            }
            return coordinate - Math.floor(coordinate / PERIOD + 0.5D) * PERIOD;
        }

        private static float dot(int hash, float x, float y, float z) {
            int[] gradient = GRADIENTS[hash & 15];
            return gradient[0] * x + gradient[1] * y + gradient[2] * z;
        }

        private static float fade(float value) {
            return value * value * value * (value * (value * 6.0F - 15.0F) + 10.0F);
        }

        private static float lerp(float alpha, float start, float end) {
            return start + alpha * (end - start);
        }
    }

    private record HashedSeedFactory(long seedLow, long seedHigh) {
        private XoroshiroSource fromHashOf(String name) {
            byte[] digest = md5(name);
            long low = 0L;
            long high = 0L;
            for (int index = 0; index < 8; index++) {
                low = low << 8 | digest[index] & 0xFFL;
                high = high << 8 | digest[index + 8] & 0xFFL;
            }
            return new XoroshiroSource(low ^ seedLow, high ^ seedHigh);
        }

        private static byte[] md5(String name) {
            try {
                return MessageDigest.getInstance("MD5").digest(name.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException failure) {
                throw new IllegalStateException("MD5 digest is unavailable for noise seed '" + name + "'", failure);
            }
        }
    }

    private static final class XoroshiroSource {
        private static final long SILVER_RATIO = 7640891576956012809L;
        private static final long GOLDEN_RATIO = -7046029254386353131L;
        private static final double DOUBLE_UNIT = 1.1102230246251565E-16D;

        private long low;
        private long high;

        private XoroshiroSource(long low, long high) {
            if ((low | high) == 0L) {
                this.low = GOLDEN_RATIO;
                this.high = SILVER_RATIO;
                return;
            }
            this.low = low;
            this.high = high;
        }

        private static XoroshiroSource fromWorldSeed(long seed) {
            long unmixedLow = seed ^ SILVER_RATIO;
            long unmixedHigh = unmixedLow + GOLDEN_RATIO;
            return new XoroshiroSource(mix(unmixedLow), mix(unmixedHigh));
        }

        private HashedSeedFactory forkPositional() {
            long seedLow = nextLong();
            long seedHigh = nextLong();
            return new HashedSeedFactory(seedLow, seedHigh);
        }

        private long nextLong() {
            long currentLow = low;
            long currentHigh = high;
            long result = Long.rotateLeft(currentLow + currentHigh, 17) + currentLow;
            currentHigh ^= currentLow;
            low = Long.rotateLeft(currentLow, 49) ^ currentHigh ^ currentHigh << 21;
            high = Long.rotateLeft(currentHigh, 28);
            return result;
        }

        private int nextInt(int bound) {
            long random = Integer.toUnsignedLong((int) nextLong());
            long product = random * bound;
            long fraction = product & 0xFFFFFFFFL;
            if (fraction < bound) {
                int threshold = Integer.remainderUnsigned(~bound + 1, bound);
                while (fraction < threshold) {
                    random = Integer.toUnsignedLong((int) nextLong());
                    product = random * bound;
                    fraction = product & 0xFFFFFFFFL;
                }
            }
            return (int) (product >> 32);
        }

        private double nextDouble() {
            return (nextLong() >>> 11) * DOUBLE_UNIT;
        }

        private static long mix(long value) {
            long mixed = (value ^ value >>> 30) * -4658895280553007687L;
            mixed = (mixed ^ mixed >>> 27) * -7723592293110705685L;
            return mixed ^ mixed >>> 31;
        }
    }
}
