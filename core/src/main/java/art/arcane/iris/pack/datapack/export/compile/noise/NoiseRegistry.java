package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

public final class NoiseRegistry {
    private final String namespace;
    private final SortedMap<String, NoiseDef> definitions = new TreeMap<>();

    public NoiseRegistry(String namespace) {
        this.namespace = VanillaResourceIdentifier.requireNamespace(namespace, "noise namespace");
    }

    public String namespace() {
        return namespace;
    }

    public String register(NoiseDef definition) {
        Objects.requireNonNull(definition);
        NoiseDef existing = definitions.putIfAbsent(definition.id(), definition);
        if (existing != null && !existing.equals(definition)) {
            throw new IllegalArgumentException("Noise '" + namespace + ":" + definition.id() + "' is already registered with different parameters");
        }
        return namespace + ":" + definition.id();
    }

    public List<NoiseDef> definitions() {
        return List.copyOf(definitions.values());
    }

    public SortedMap<String, NoiseDef> byReference() {
        SortedMap<String, NoiseDef> references = new TreeMap<>();
        for (NoiseDef definition : definitions.values()) {
            references.put(namespace + ":" + definition.id(), definition);
        }
        return Collections.unmodifiableSortedMap(references);
    }
}
