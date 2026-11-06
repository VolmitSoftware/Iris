package art.arcane.iris.core.studio.scene;

import com.volmit.tooling.JsonPath;

/** Paths written as tests read them: a {@link String} for a member, an {@link Integer} for an element. */
final class TestPaths {
    private TestPaths() {
    }

    static JsonPath path(Object... steps) {
        JsonPath path = JsonPath.ROOT;
        for (Object step : steps) {
            path = step instanceof Integer position ? path.element(position) : path.member((String) step);
        }
        return path;
    }
}
