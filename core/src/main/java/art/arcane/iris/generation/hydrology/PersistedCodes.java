package art.arcane.iris.generation.hydrology;

import java.util.Arrays;
import java.util.function.ToIntFunction;

public final class PersistedCodes<E extends Enum<E>> {
    private static final int MAX_CODE = 255;

    private final String type;
    private final E[] byCode;

    private PersistedCodes(String type, E[] byCode) {
        this.type = type;
        this.byCode = byCode;
    }

    public static <E extends Enum<E>> PersistedCodes<E> of(Class<E> type, ToIntFunction<E> code) {
        E[] constants = type.getEnumConstants();
        int size = 0;
        for (E constant : constants) {
            int value = code.applyAsInt(constant);
            if (value < 0 || value > MAX_CODE) {
                throw new IllegalStateException(type.getSimpleName() + "." + constant.name() + " has code " + value
                        + ", outside 0.." + MAX_CODE);
            }
            size = Math.max(size, value + 1);
        }
        E[] byCode = Arrays.copyOf(constants, size);
        Arrays.fill(byCode, null);
        for (E constant : constants) {
            int value = code.applyAsInt(constant);
            if (byCode[value] != null) {
                throw new IllegalStateException(type.getSimpleName() + "." + constant.name() + " reuses code " + value + " of "
                        + type.getSimpleName() + "." + byCode[value].name());
            }
            byCode[value] = constant;
        }
        return new PersistedCodes<>(type.getSimpleName(), byCode);
    }

    public E decode(int code) {
        E constant = code >= 0 && code < byCode.length ? byCode[code] : null;
        if (constant == null) {
            throw new IllegalStateException("Unknown " + type + " code " + code);
        }
        return constant;
    }
}
