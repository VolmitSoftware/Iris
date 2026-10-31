package art.arcane.iris.pack.datapack.export.ir;

import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;

import java.util.ArrayList;
import java.util.List;

public record NoiseDef(String id, int firstOctave, List<Double> amplitudes) {
    private static final int MAX_OCTAVE = 32;
    private static final double MAX_AMPLITUDE = 1.0e6;

    public NoiseDef {
        VanillaResourceIdentifier.requirePath(id, "noise id");
        if (firstOctave < -MAX_OCTAVE || firstOctave > MAX_OCTAVE) {
            throw new IllegalArgumentException("Noise '" + id + "' first octave must be within [-32, 32], got " + firstOctave);
        }
        if (amplitudes.isEmpty() || amplitudes.size() > MAX_OCTAVE) {
            throw new IllegalArgumentException("Noise '" + id + "' needs 1 to 32 amplitudes, got " + amplitudes.size());
        }
        List<Double> normalized = new ArrayList<>(amplitudes.size());
        boolean audible = false;
        for (Double amplitude : amplitudes) {
            if (amplitude == null || !(amplitude >= 0.0 && amplitude <= MAX_AMPLITUDE)) {
                throw new IllegalArgumentException("Noise '" + id + "' amplitudes must be within [0, 1e6], got " + amplitude);
            }
            normalized.add(amplitude == 0.0 ? 0.0 : amplitude);
            audible |= amplitude > 0.0;
        }
        if (!audible) {
            throw new IllegalArgumentException("Noise '" + id + "' needs at least one positive amplitude");
        }
        amplitudes = List.copyOf(normalized);
    }
}
