package art.arcane.iris.engine.object;

import com.google.gson.Gson;

/** Settings classes of the built-in modules have no setters; tests write them as the JSON packs hold. */
public final class TestSettings {
    private static final Gson GSON = new Gson();

    private TestSettings() {
    }

    public static <T> T of(Class<T> type, String json) {
        return GSON.fromJson(json, type);
    }

    /** Sets fields of settings a test built, as a pack's JSON would have: {@code with(settings, "name", value, "other", value)}; returns {@code settings}. */
    public static <T> T with(T settings, Object... namesAndValues) {
        for (int index = 0; index < namesAndValues.length; index += 2) {
            set(settings, (String) namesAndValues[index], namesAndValues[index + 1]);
        }
        return settings;
    }

    private static void set(Object settings, String field, Object value) {
        try {
            java.lang.reflect.Field target = settings.getClass().getDeclaredField(field);
            target.setAccessible(true);
            target.set(settings, value);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalArgumentException("No settings field " + field + " in " + settings.getClass().getName(), failure);
        }
    }
}
