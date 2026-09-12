package art.arcane.iris.diagnostics.splash;

import art.arcane.iris.pack.PackDirectoryResolver;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class IrisSplashPackScanner {
    private IrisSplashPackScanner() {
    }

    public static List<SplashPackMetadata> collect(File packFolder, SplashPackErrorReporter reporter) {
        if (packFolder == null || !packFolder.isDirectory()) {
            return List.of();
        }

        List<File> folders = PackDirectoryResolver.listVisiblePackDirectories(packFolder);
        if (folders.isEmpty()) {
            return List.of();
        }

        List<SplashPackMetadata> packs = new ArrayList<>(folders.size());
        for (File folder : folders) {
            SplashPackMetadata metadata = read(folder, reporter);
            if (metadata != null) {
                packs.add(metadata);
            }
        }

        packs.sort(Comparator.comparing(SplashPackMetadata::name));
        return packs;
    }

    public static SplashPackMetadata read(File pack, SplashPackErrorReporter reporter) {
        if (pack == null || !pack.isDirectory()) {
            return null;
        }

        String dimName = pack.getName();
        File dimensionFile = new File(pack, "dimensions/" + dimName + ".json");
        if (!dimensionFile.isFile()) {
            return null;
        }

        try (FileReader reader = new FileReader(dimensionFile)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            JsonElement version = json.get("version");
            if (version == null || !version.isJsonPrimitive()) {
                return null;
            }

            return new SplashPackMetadata(dimName, version.getAsString());
        } catch (IOException | JsonParseException | IllegalStateException | UnsupportedOperationException error) {
            report(reporter, "Failed to read splash metadata for dimension pack \"" + dimName + "\".", error);
            return null;
        }
    }

    private static void report(SplashPackErrorReporter reporter, String message, Throwable error) {
        if (reporter == null) {
            return;
        }

        reporter.report(message, error);
    }

    public record SplashPackMetadata(String name, String version) {
    }

    public interface SplashPackErrorReporter {
        void report(String message, Throwable error);
    }
}
