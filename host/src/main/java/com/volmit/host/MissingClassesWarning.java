package com.volmit.host;

import java.util.List;

/** The one warning a pack load writes when its jars use classes nobody has. One line per jar,
 * with a capped list of classes, so a large bundled library cannot flood the log.
 */
final class MissingClassesWarning {
    static final int MAX_LISTED_CLASSES_PER_JAR = 5;
    private static final String INDENT = "  ";

    private MissingClassesWarning() {}

    /** Expects at least one jar; a pack without missing classes logs no warning. */
    static String describe(String packName, List<MissingClasses> jars) {
        var text = new StringBuilder("Pack ").append(packName)
                .append(": module jars use classes that neither the pack, Iris nor the JDK has, usually optional"
                        + " dependencies of a bundled library. The jars load, but code that needs these classes throws"
                        + " NoClassDefFoundError when it runs:");
        for (var jar : jars) {
            text.append('\n').append(INDENT).append(jar.origin()).append(": ").append(listClasses(jar.classNames()));
        }
        return text.toString();
    }

    private static String listClasses(List<String> classNames) {
        var listed = String.join(", ", classNames.subList(0, Math.min(classNames.size(), MAX_LISTED_CLASSES_PER_JAR)));
        int unlisted = classNames.size() - MAX_LISTED_CLASSES_PER_JAR;
        return unlisted > 0 ? listed + " and " + unlisted + " more" : listed;
    }
}
