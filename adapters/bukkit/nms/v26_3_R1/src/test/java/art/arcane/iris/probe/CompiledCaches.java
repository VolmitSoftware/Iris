package art.arcane.iris.probe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.CachingDensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.material.rule.ConditionRule;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.OreVeinRule;
import net.minecraft.world.level.levelgen.material.rule.SequenceRule;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

final class CompiledCaches {
    private static final String MINECRAFT_PACKAGE = "net.minecraft.";
    private static final String DENSITY_PACKAGE = "net.minecraft.world.level.levelgen.densityfunction.";
    private static final String SPLINE_PACKAGE = "net.minecraft.util.";
    private static final String SPLINE_NAME = "Spline";

    private final RegistryAccess registries;
    private final NoiseGeneratorSettings settings;
    private final RandomState state;

    private CompiledCaches(RegistryAccess registries, NoiseGeneratorSettings settings, long seed) {
        this.registries = registries;
        this.settings = settings;
        this.state = RandomState.create(registries.lookupOrThrow(Registries.NOISE), seed, settings);
    }

    static CompiledCaches open(RegistryAccess registries, long seed) {
        if (!(DatapackCostProbe.exportedOverworld(registries).generator() instanceof NoiseBasedChunkGenerator generator)) {
            throw new IllegalStateException("Exported overworld has no noise generator");
        }
        return new CompiledCaches(registries, generator.generatorSettings().value(), seed);
    }

    DensityFunction finalDensity() {
        return settings.noiseRouter().finalDensity();
    }

    DensityFunction function(String id) {
        return registries.lookupOrThrow(Registries.DENSITY_FUNCTION)
                .getOrThrow(ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.parse(id))).value();
    }

    List<OreVeinRule> oreVeins() {
        List<OreVeinRule> veins = new ArrayList<>();
        Deque<MaterialRule> pending = new ArrayDeque<>();
        pending.push(settings.materialRule().value());
        while (!pending.isEmpty()) {
            MaterialRule rule = pending.pop();
            switch (rule) {
                case OreVeinRule vein -> veins.add(vein);
                case SequenceRule sequence -> sequence.sequence().reversed().forEach(pending::push);
                case ConditionRule condition -> pending.push(condition.thenRun());
                default -> {
                }
            }
        }
        return veins;
    }

    int rootId(DensityFunction function) {
        if (!(state.getSampler(function) instanceof CachingDensitySampler cache)) {
            throw new IllegalStateException("Density function " + function + " does not compile to a cache");
        }
        return cache.id();
    }

    Set<Integer> ids(DensityFunction function) {
        Set<Integer> ids = new TreeSet<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Object> pending = new ArrayDeque<>();
        pending.push(state.getSampler(function));
        while (!pending.isEmpty()) {
            Object node = pending.pop();
            if (node == null || !visited.add(node)) {
                continue;
            }
            if (node instanceof CachingDensitySampler cache) {
                ids.add(cache.id());
            }
            for (Object child : children(node)) {
                pending.push(child);
            }
        }
        return ids;
    }

    private static List<Object> children(Object node) {
        List<Object> children = new ArrayList<>();
        if (node instanceof Object[] array) {
            Collections.addAll(children, array);
            return children;
        }
        if (node instanceof Collection<?> collection) {
            children.addAll(collection);
            return children;
        }
        if (!traversed(node)) {
            return children;
        }
        for (Class<?> type = node.getClass(); type != null && type.getName().startsWith(MINECRAFT_PACKAGE); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                children.add(read(field, node));
            }
        }
        return children;
    }

    private static boolean traversed(Object node) {
        if (node instanceof Enum<?>) {
            return false;
        }
        String name = node.getClass().getName();
        return node instanceof DensitySampler || name.startsWith(DENSITY_PACKAGE) || name.startsWith(SPLINE_PACKAGE) && name.contains(SPLINE_NAME);
    }

    private static Object read(Field field, Object owner) {
        try {
            field.setAccessible(true);
            return field.get(owner);
        } catch (IllegalAccessException | RuntimeException failure) {
            throw new IllegalStateException("Cannot read " + field + " of " + owner.getClass().getName(), failure);
        }
    }
}
