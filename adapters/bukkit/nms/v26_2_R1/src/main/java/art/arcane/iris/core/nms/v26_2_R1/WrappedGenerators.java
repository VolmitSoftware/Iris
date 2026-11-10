package art.arcane.iris.core.nms.v26_2_R1;

import net.minecraft.world.level.chunk.ChunkGenerator;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Optional;

/**
 * Finds the Iris generator behind a level's active generator. Other plugins (CraftEngine, for one)
 * wrap the active generator in a delegating generator of their own during WorldInitEvent, after
 * Iris has injected its generator, so the active generator is not always Iris itself.
 */
final class WrappedGenerators {
    private static final int MAX_WRAPPER_DEPTH = 4;
    private static final ClassValue<Optional<Field>> WRAPPED_GENERATOR_FIELDS = new ClassValue<>() {
        @Override
        protected Optional<Field> computeValue(Class<?> wrapperType) {
            return findWrappedGeneratorField(wrapperType);
        }
    };

    private WrappedGenerators() {
    }

    static IrisChunkGenerator irisGeneratorOf(ChunkGenerator activeGenerator) {
        ChunkGenerator current = activeGenerator;
        for (int depth = 0; current != null && depth <= MAX_WRAPPER_DEPTH; depth++) {
            if (current instanceof IrisChunkGenerator irisGenerator) {
                return irisGenerator;
            }
            current = wrappedBy(current);
        }
        return null;
    }

    private static ChunkGenerator wrappedBy(ChunkGenerator wrapper) {
        Optional<Field> field = WRAPPED_GENERATOR_FIELDS.get(wrapper.getClass());
        if (field.isEmpty()) {
            return null;
        }
        try {
            return field.get().get(wrapper) instanceof ChunkGenerator wrapped && wrapped != wrapper ? wrapped : null;
        } catch (IllegalAccessException unreadable) {
            return null;
        }
    }

    private static Optional<Field> findWrappedGeneratorField(Class<?> wrapperType) {
        for (Class<?> type = wrapperType; type != null && type != ChunkGenerator.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !ChunkGenerator.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    return Optional.of(field);
                } catch (RuntimeException inaccessible) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }
}
