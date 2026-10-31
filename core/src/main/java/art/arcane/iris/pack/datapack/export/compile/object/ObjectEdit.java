package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.compile.WeightedState;

import java.util.List;
import java.util.Map;

public record ObjectEdit(List<VanillaBlockState> find, boolean exact, List<WeightedState> replace, double chance) {
    public ObjectEdit {
        find = List.copyOf(find);
        replace = List.copyOf(replace);
        if (find.isEmpty() || replace.isEmpty()) {
            throw new IllegalArgumentException("Object edits need at least one find and one replace state");
        }
        if (!Double.isFinite(chance) || chance < 0 || chance > 1) {
            throw new IllegalArgumentException("Object edit chance must be within [0, 1], got " + chance);
        }
    }

    public boolean deterministic() {
        return chance >= 1;
    }

    public boolean matches(VanillaBlockState state) {
        for (VanillaBlockState candidate : find) {
            if (!candidate.name().equals(state.name())) {
                continue;
            }
            if (!exact || propertiesMatch(candidate, state)) {
                return true;
            }
        }
        return false;
    }

    public int totalWeight() {
        int total = 0;
        for (WeightedState state : replace) {
            total += state.weight();
        }
        return total;
    }

    public String descriptor() {
        StringBuilder builder = new StringBuilder("find=");
        for (VanillaBlockState state : find) {
            builder.append(state.canonical()).append('|');
        }
        builder.append(";exact=").append(exact).append(";replace=");
        for (WeightedState state : replace) {
            builder.append(state.state().canonical()).append('*').append(state.weight()).append('|');
        }
        return builder.append(";chance=").append(chance).toString();
    }

    private static boolean propertiesMatch(VanillaBlockState find, VanillaBlockState state) {
        for (Map.Entry<String, String> property : find.properties().entrySet()) {
            if (!property.getValue().equals(state.properties().get(property.getKey()))) {
                return false;
            }
        }
        return true;
    }
}
