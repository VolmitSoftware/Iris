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
}
