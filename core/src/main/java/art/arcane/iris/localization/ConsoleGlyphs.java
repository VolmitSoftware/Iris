package art.arcane.iris.localization;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Swaps the non-ASCII glyphs Iris prints for ASCII ones when the console charset cannot encode them. */
public final class ConsoleGlyphs {
    public static final String BAR = "\u2502";
    public static final String BRANCH = "\u251c";
    public static final String LAST_BRANCH = "\u2514";
    public static final String CHECK = "\u2714";
    public static final String WARNING = "\u25b2";
    public static final String CROSS = "\u2716";
    public static final String ELLIPSIS = "\u2026";

    private static final Map<String, String> ASCII_FALLBACKS = asciiFallbacks();
    private static final String[] CONSOLE_CHARSET_PROPERTIES = {"stdout.encoding", "native.encoding"};
    private static final ConsoleGlyphs CONSOLE = forCharset(consoleCharset());

    private final Map<String, String> replacements;

    private ConsoleGlyphs(Map<String, String> replacements) {
        this.replacements = replacements;
    }

    public static ConsoleGlyphs console() {
        return CONSOLE;
    }

    public static ConsoleGlyphs forCharset(Charset charset) {
        Map<String, String> unencodable = new LinkedHashMap<>();
        for (Map.Entry<String, String> fallback : ASCII_FALLBACKS.entrySet()) {
            if (!charset.newEncoder().canEncode(fallback.getKey())) {
                unencodable.put(fallback.getKey(), fallback.getValue());
            }
        }
        return new ConsoleGlyphs(unencodable);
    }

    public String fit(String text) {
        if (text == null || replacements.isEmpty()) {
            return text;
        }
        String fitted = text;
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            fitted = fitted.replace(replacement.getKey(), replacement.getValue());
        }
        return fitted;
    }

    private static Map<String, String> asciiFallbacks() {
        Map<String, String> fallbacks = new LinkedHashMap<>();
        fallbacks.put("\u00bb", ">");
        fallbacks.put(BAR, "|");
        fallbacks.put(BRANCH, "|");
        fallbacks.put(LAST_BRANCH, "`");
        fallbacks.put(CHECK, "+");
        fallbacks.put(WARNING, "!");
        fallbacks.put(CROSS, "x");
        fallbacks.put(ELLIPSIS, "...");
        fallbacks.put("\u2039", "[");
        fallbacks.put("\u203a", "]");
        return fallbacks;
    }

    private static Charset consoleCharset() {
        for (String property : CONSOLE_CHARSET_PROPERTIES) {
            String name = System.getProperty(property);
            if (name == null || name.isBlank()) {
                continue;
            }
            try {
                if (Charset.isSupported(name)) {
                    return Charset.forName(name);
                }
            } catch (IllegalCharsetNameException ignored) {
                // An unusable property value falls through to the next source.
            }
        }
        return Charset.defaultCharset();
    }
}
