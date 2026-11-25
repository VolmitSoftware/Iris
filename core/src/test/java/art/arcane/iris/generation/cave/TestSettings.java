package art.arcane.iris.generation.cave;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Reads cave settings from the JSON a pack writes, the way a pack's loader does. */
public final class TestSettings {
    private static final Gson GSON = gson();

    private TestSettings() {
    }

    public static <T> T of(Class<T> type, String json) {
        return GSON.fromJson(json, type);
    }

    public static Gson gson() {
        GsonBuilder builder = new GsonBuilder().setLenient();
        CaveJson.register(builder);
        return builder.create();
    }
}
