package art.arcane.iris.pack.loading;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.localization.C;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JsonSchemaValidator {
    private static final ConcurrentHashMap<Class<?>, Set<String>> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final int SUGGESTION_MAX_DISTANCE = 4;

    private JsonSchemaValidator() {
    }

    static void validateTopLevelKeys(JSONObject parsed, String rawText, File file, String resourceTypeName, Class<?> objectClass) {
        if (parsed == null || objectClass == null) {
            return;
        }
        Set<String> known = FIELD_CACHE.computeIfAbsent(objectClass, JsonSchemaValidator::collectFieldNames);
        for (String key : parsed.keySet()) {
            if (known.contains(key)) {
                continue;
            }
            reportUnknownKey(key, rawText, file, resourceTypeName, known);
        }
    }

    static void reportLoadFailure(File file, String rawText, String resourceTypeName, Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        int line = extractLineFromMessage(message);
        String location = file.getPath();
        if (line > 0) {
            location = location + ":" + line;
        }
        StringBuilder out = new StringBuilder();
        out.append("Couldn't load ").append(resourceTypeName)
                .append(C.RED).append(" in ").append(C.WHITE).append(location).append(C.RED)
                .append(" -> ").append(message);
        String snippet = buildSnippet(rawText, line);
        if (snippet != null) {
            out.append('\n').append(snippet);
        }
        IrisLogging.warn(out.toString());
    }

    private static void reportUnknownKey(String key, String rawText, File file, String resourceTypeName, Set<String> known) {
        int line = findLineForKey(rawText, key);
        String suggestion = closestMatch(key, known);
        StringBuilder out = new StringBuilder();
        out.append("Unknown ").append(resourceTypeName).append(" field ")
                .append(C.WHITE).append('"').append(key).append('"').append(C.YELLOW)
                .append(" in ").append(C.WHITE).append(file.getPath());
        if (line > 0) {
            out.append(":").append(line);
        }
        out.append(C.YELLOW).append(" (Gson will silently ignore this)");
        if (suggestion != null) {
            out.append(". Did you mean ").append(C.WHITE).append('"').append(suggestion).append('"').append(C.YELLOW).append("?");
        }
        String snippet = buildSnippet(rawText, line);
        if (snippet != null) {
            out.append('\n').append(snippet);
        }
        IrisLogging.warn(out.toString());
    }

    private static Set<String> collectFieldNames(Class<?> cls) {
        Set<String> names = new LinkedHashSet<>();
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            for (Field field : c.getDeclaredFields()) {
                int mods = field.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isTransient(mods)) {
                    continue;
                }
                if (field.isSynthetic()) {
                    continue;
                }
                SerializedName serialized = field.getAnnotation(SerializedName.class);
                if (serialized != null) {
                    names.add(serialized.value());
                    Collections.addAll(names, serialized.alternate());
                } else {
                    names.add(field.getName());
                }
            }
            c = c.getSuperclass();
        }
        return Collections.unmodifiableSet(names);
    }

    private static int findLineForKey(String rawText, String key) {
        if (rawText == null || key == null) {
            return -1;
        }
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:");
        Matcher matcher = pattern.matcher(rawText);
        if (!matcher.find()) {
            return -1;
        }
        int index = matcher.start();
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (rawText.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static int extractLineFromMessage(String message) {
        if (message == null) {
            return -1;
        }
        Matcher m = Pattern.compile("line\\s+(\\d+)").matcher(message);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private static String buildSnippet(String rawText, int line) {
        if (rawText == null || line <= 0) {
            return null;
        }
        String[] lines = rawText.split("\n", -1);
        if (line > lines.length) {
            return null;
        }
        int from = Math.max(0, line - 2);
        int to = Math.min(lines.length, line + 1);
        StringBuilder out = new StringBuilder();
        int width = String.valueOf(to).length();
        for (int i = from; i < to; i++) {
            int n = i + 1;
            boolean focus = n == line;
            out.append(focus ? C.RED + "> " : C.GRAY + "  ");
            out.append(String.format("%" + width + "d", n)).append(" | ");
            String content = lines[i];
            if (content.length() > 200) {
                content = content.substring(0, 200) + "...";
            }
            out.append(content);
            if (i < to - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static String closestMatch(String key, Set<String> known) {
        String lowerKey = key.toLowerCase();
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : known) {
            int d = levenshtein(lowerKey, candidate.toLowerCase());
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        if (best == null) {
            return null;
        }
        int threshold = Math.min(SUGGESTION_MAX_DISTANCE, Math.max(1, key.length() / 2));
        return bestDistance <= threshold ? best : null;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}
