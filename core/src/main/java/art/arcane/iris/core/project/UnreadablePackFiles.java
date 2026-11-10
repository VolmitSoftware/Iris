package art.arcane.iris.core.project;

import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONException;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.volmlib.util.json.JSONTokener;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The pack files that do not parse right now. A hotload has to wait for them: it would replace the running engine
 * with one missing those files, which drops biomes from the world and can leave a region with nothing to generate.
 * A file stays here until it parses, so saving another file cannot hotload past it.
 */
public final class UnreadablePackFiles {
    private static final String JSON_EXTENSION = ".json";

    private final Map<File, String> reasonByFile = new LinkedHashMap<>();

    /**
     * @return the unreadable files after looking at the touched ones again, with what is wrong with each
     */
    public synchronized Map<File, String> recheck(Collection<File> touched) {
        Set<File> toRead = new LinkedHashSet<>(reasonByFile.keySet());
        toRead.addAll(touched);
        reasonByFile.clear();
        toRead.stream().filter(UnreadablePackFiles::isJsonFile).forEach(this::check);
        return Map.copyOf(reasonByFile);
    }

    private static boolean isJsonFile(File file) {
        return file.isFile() && file.getName().endsWith(JSON_EXTENSION);
    }

    private void check(File file) {
        try {
            Object parsed = new JSONTokener(IO.readAll(file)).nextValue();
            if (!(parsed instanceof JSONObject) && !(parsed instanceof JSONArray)) {
                reasonByFile.put(file, "not a JSON object");
            }
        } catch (JSONException | IOException e) {
            reasonByFile.put(file, e.getMessage());
        }
    }
}
