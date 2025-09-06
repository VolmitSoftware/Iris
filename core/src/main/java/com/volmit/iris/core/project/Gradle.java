package com.volmit.iris.core.project;

import com.volmit.iris.Iris;
import com.volmit.iris.util.io.IO;
import org.zeroturnaround.zip.ZipUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Optional;
import java.util.Scanner;

public class Gradle {
    private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final String[] ENVIRONMENT = createEnvironment();
    private static final String VERSION = "8.14.2";
    private static final String DISTRIBUTION_URL = "https://services.gradle.org/distributions/gradle-" + VERSION + "-bin.zip";
    private static final String HASH = IO.hash(DISTRIBUTION_URL);

    public static synchronized void wrapper(File projectDir) {
        try {
            File settings = new File(projectDir, "settings.gradle.kts");
            if (!settings.exists()) settings.createNewFile();
            runGradle(projectDir, "wrapper");
        } catch (Throwable e) {
            Iris.error("Failed to install gradle wrapper!");
            e.printStackTrace();
            Iris.reportError(e);
        }
    }

    public static void runGradle(File projectDir, String... args) throws IOException, InterruptedException {
        File gradle = downloadGradle(false);
        String[] cmd = new String[args.length + 1];
        cmd[0] = gradle.getAbsolutePath();
        System.arraycopy(args, 0, cmd, 1, args.length);
        var process = Runtime.getRuntime().exec(cmd, ENVIRONMENT, projectDir);
        attach(process.getInputStream());
        attach(process.getErrorStream());
        var code = process.waitFor();
        if (code == 0) return;
        throw new RuntimeException("Gradle exited with code " + code);
    }

    private static synchronized File downloadGradle(boolean force) {
        var folder = Iris.instance.getDataFolder("cache", HASH.substring(0, 2), HASH);
        if (force) {
            IO.delete(folder);
            folder.mkdirs();
        }

        var bin = new File(folder, "gradle-" + VERSION + "/bin/gradle" + (WINDOWS ? ".bat" : ""));
        if (bin.exists()) {
            bin.setExecutable(true);
            return bin;
        }

        try (var input = new BufferedInputStream(URI.create(DISTRIBUTION_URL).toURL().openStream())) {
            ZipUtil.unpack(input, folder);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to download gradle", e);
        }

        bin.setExecutable(true);
        return bin;
    }

    private static String[] createEnvironment() {
        var env = new HashMap<>(System.getenv());
        env.put("JAVA_HOME", findJavaHome());
        return env.entrySet()
                .stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .toArray(String[]::new);
    }

    private static String findJavaHome() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && new File(javaHome + "/bin/java" + (WINDOWS ? ".exe" : "")).exists()) {
            return javaHome;
        }

        return ProcessHandle.current()
                .info()
                .command()
                .map(s -> new File(s).getAbsoluteFile().getParentFile().getParentFile())
                .flatMap(f -> f.exists() ? Optional.of(f.getAbsolutePath()) : Optional.empty())
                .orElseThrow(() -> new RuntimeException("Failed to find java home, please set java.home system property"));
    }

    private static void attach(InputStream stream) {
        Thread.ofVirtual().start(() -> {
            try (var in = new Scanner(stream)) {
                while (in.hasNextLine()) {
                    String line = in.nextLine();
                    Iris.debug("[GRADLE] " + line);
                }
            }
        });
    }
}
