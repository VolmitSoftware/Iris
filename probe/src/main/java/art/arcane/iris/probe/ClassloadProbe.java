/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.probe;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class ClassloadProbe {
    private static final String ALLOWLIST_RESOURCE = "/classload-allowlist.tsv";

    private static final String[] CRITICAL_PREFIXES = {
            "art.arcane.iris.generation.",
            "art.arcane.iris.pack.",
            "art.arcane.iris.structure.",
            "art.arcane.iris.world.storage.",
            "art.arcane.iris.spi.",
    };

    public static void main(String[] args) throws IOException {
        art.arcane.iris.spi.IrisPlatforms.bind(new StubPlatform());
        boolean bukkitPresent = true;
        try {
            Class.forName("org.bukkit.Bukkit", false, ClassloadProbe.class.getClassLoader());
        } catch (ClassNotFoundException absent) {
            bukkitPresent = false;
        }
        System.out.println("[probe] org.bukkit on classpath: " + bukkitPresent);
        if (bukkitPresent) {
            System.out.println("[probe] RESULT: FAIL - org.bukkit must be absent from the classload probe runtime");
            System.exit(1);
            return;
        }

        TreeMap<String, Allowance> allowlist = loadAllowlist();
        Path classesRoot = Path.of(args[0]);
        List<String> names = scanClassNames(classesRoot);

        TreeMap<String, Failure> criticalFailures = new TreeMap<>();
        TreeMap<String, Failure> otherFailures = new TreeMap<>();
        int loaded = 0;
        int nestedTotal = 0;
        int nestedLoaded = 0;
        int criticalTotal = 0;
        for (String name : names) {
            boolean nested = name.contains("$");
            if (nested) {
                nestedTotal++;
            }
            boolean critical = matchesAny(name, CRITICAL_PREFIXES) && !allowlist.containsKey(name);
            if (critical) {
                criticalTotal++;
            }
            try {
                Class.forName(name, true, ClassloadProbe.class.getClassLoader());
                loaded++;
                if (nested) {
                    nestedLoaded++;
                }
            } catch (Throwable failure) {
                if (System.getenv("PROBE_TRACE") != null && name.contains(System.getenv("PROBE_TRACE"))) {
                    failure.printStackTrace(System.out);
                }
                Failure cause = Failure.from(failure);
                if (critical) {
                    criticalFailures.put(name, cause);
                } else {
                    otherFailures.put(name, cause);
                }
            }
        }

        System.out.println("[probe] classes scanned: " + names.size() + ", initialized OK: " + loaded);
        System.out.println("[probe] nested classes scanned: " + nestedTotal + ", initialized OK: " + nestedLoaded
                + ", initialization failures: " + (nestedTotal - nestedLoaded));
        System.out.println("[probe] critical set: " + criticalTotal + ", critical failures: " + criticalFailures.size());
        criticalFailures.forEach((name, cause) -> System.out.println("  CRITICAL " + name + "  ->  " + cause.display()));
        System.out.println("[probe] non-critical failures: " + otherFailures.size());
        Review review = review(otherFailures, allowlist);
        System.out.println("[probe] reviewed allowances: " + allowlist.size() + ", matched failures: " + review.matched());
        otherFailures.forEach((name, cause) -> {
            Allowance allowance = allowlist.get(name);
            String status = allowance != null && allowance.matches(cause) ? "allowed " + allowance.category() : "UNEXPECTED";
            System.out.println("  " + status + " " + name + "  ->  " + cause.display());
        });
        System.out.println("[probe] unexpected non-critical failures: " + review.unexpected().size());
        System.out.println("[probe] stale allowances: " + review.staleAllowances().size());
        review.staleAllowances().forEach((name, allowance) -> System.out.println("  STALE " + name + "  ->  " + allowance));

        if (!criticalFailures.isEmpty() || !review.passes()) {
            System.out.println("[probe] RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("[probe] RESULT: PASS");
    }

    static Review review(Map<String, Failure> failures, Map<String, Allowance> allowlist) {
        TreeMap<String, Failure> unexpected = new TreeMap<>();
        TreeMap<String, Allowance> staleAllowances = new TreeMap<>(allowlist);
        int matched = 0;
        for (Map.Entry<String, Failure> entry : failures.entrySet()) {
            Allowance allowance = allowlist.get(entry.getKey());
            staleAllowances.remove(entry.getKey());
            if (allowance == null || !allowance.matches(entry.getValue())) {
                unexpected.put(entry.getKey(), entry.getValue());
            } else {
                matched++;
            }
        }
        return new Review(matched, unexpected, staleAllowances);
    }

    static List<String> scanClassNames(Path classesRoot) throws IOException {
        List<String> names = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(classesRoot)) {
            walk.filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> {
                        String relative = classesRoot.relativize(path).toString();
                        names.add(relative.substring(0, relative.length() - 6).replace(File.separatorChar, '.'));
                    });
        }
        names.sort(String::compareTo);
        return names;
    }

    private static boolean matchesAny(String name, String[] prefixes) {
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static TreeMap<String, Allowance> loadAllowlist() throws IOException {
        InputStream resource = ClassloadProbe.class.getResourceAsStream(ALLOWLIST_RESOURCE);
        if (resource == null) {
            throw new IOException("Classload allowlist resource not found: " + ALLOWLIST_RESOURCE);
        }

        TreeMap<String, Allowance> allowlist = new TreeMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = line.split("\\t", -1);
                if (fields.length != 3 || fields[0].isBlank() || fields[2].isBlank()) {
                    throw new IOException("Invalid classload allowlist entry at line " + lineNumber);
                }
                Allowance allowance;
                try {
                    allowance = new Allowance(AllowanceCategory.valueOf(fields[1]), fields[2]);
                } catch (IllegalArgumentException exception) {
                    throw new IOException("Invalid classload allowlist category at line " + lineNumber + ": " + fields[1], exception);
                }
                if (allowlist.put(fields[0], allowance) != null) {
                    throw new IOException("Duplicate classload allowlist class at line " + lineNumber + ": " + fields[0]);
                }
            }
        }
        return allowlist;
    }

    enum AllowanceCategory {
        BUKKIT_API,
        PAPERLIB_API,
        ADVENTURE_API,
        MYTHICMOBS_API
    }

    record Allowance(AllowanceCategory category, String detail) {
        boolean matches(Failure failure) {
            return switch (category) {
                case BUKKIT_API -> detail.startsWith("org.bukkit.") && failure.isMissingClassIn("org.bukkit.");
                case PAPERLIB_API -> detail.startsWith("io.papermc.lib.") && failure.isMissingClassIn("io.papermc.lib.");
                case ADVENTURE_API -> detail.startsWith("net.kyori.adventure.") && failure.isMissingClassIn("net.kyori.adventure.");
                case MYTHICMOBS_API -> detail.startsWith("io.lumine.mythic.") && failure.isMissingClassIn("io.lumine.mythic.");
            };
        }
    }

    record Failure(String exceptionClass, String message) {
        static Failure from(Throwable failure) {
            Throwable cause = failure;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            return new Failure(cause.getClass().getName(), String.valueOf(cause.getMessage()));
        }

        boolean isMissingClassIn(String packagePrefix) {
            if (exceptionClass.equals("java.lang.ClassNotFoundException")) {
                return message.startsWith(packagePrefix);
            }
            String internalPrefix = packagePrefix.replace('.', '/');
            if (exceptionClass.equals("java.lang.NoClassDefFoundError")) {
                return message.startsWith(internalPrefix);
            }
            return exceptionClass.equals("java.lang.ExceptionInInitializerError")
                    && message.contains("NoClassDefFoundError: " + internalPrefix);
        }

        String display() {
            int separator = exceptionClass.lastIndexOf('.');
            String simpleName = separator < 0 ? exceptionClass : exceptionClass.substring(separator + 1);
            return simpleName + ": " + message;
        }
    }

    record Review(int matched, TreeMap<String, Failure> unexpected, TreeMap<String, Allowance> staleAllowances) {
        boolean passes() {
            return unexpected.isEmpty() && staleAllowances.isEmpty();
        }
    }
}
