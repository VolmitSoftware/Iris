package art.arcane.iris.core;

import art.arcane.iris.spi.IrisLogging;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class ServerTimeoutConfiguration {
    static final String SPIGOT_TIMEOUT_KEY = "settings.timeout-time";
    static final String PAPER_WATCHDOG_KEY = "watchdog.early-warning-delay";

    private static final Object MUTATION_LOCK = new Object();
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    interface ServerYaml {
        Document load(Path file) throws IOException;
    }

    interface Document {
        boolean isSet(String key);

        boolean isInteger(String key);

        long getLong(String key);

        void set(String key, Long value);

        String saveToString();
    }

    private final Path recoveryFile;
    private final ServerYaml yaml;

    ServerTimeoutConfiguration(Path recoveryFile, ServerYaml yaml) {
        this.recoveryFile = Objects.requireNonNull(recoveryFile);
        this.yaml = Objects.requireNonNull(yaml);
    }

    void raise(Path file, String key, long minimum) throws IOException {
        synchronized (MUTATION_LOCK) {
            Path target = file.toRealPath();
            Document configuration = yaml.load(target);
            Long original = value(configuration, key);
            List<SavedTimeout> changes = readChanges();
            SavedTimeout saved = find(changes, target, key);
            if (saved == null || !Objects.equals(original, saved.original()) && !Objects.equals(original, saved.applied())) {
                changes.remove(saved);
                changes.add(new SavedTimeout(target.toString(), key, original, minimum));
                writeChanges(changes);
            }
            configuration.set(key, minimum);
            ServerConfigurator.writeStringAtomic(target, configuration.saveToString());
        }
    }

    void restore(String key) throws IOException {
        synchronized (MUTATION_LOCK) {
            List<SavedTimeout> changes = readChanges();
            for (SavedTimeout saved : List.copyOf(changes)) {
                if (!saved.key().equals(key)) {
                    continue;
                }
                restore(saved);
                changes.remove(saved);
                writeChanges(changes);
            }
        }
    }

    private void restore(SavedTimeout saved) throws IOException {
        Path target = Path.of(saved.file());
        if (!Files.exists(target)) {
            return;
        }
        Document configuration = yaml.load(target);
        if (!Objects.equals(value(configuration, saved.key()), saved.applied())) {
            IrisLogging.info("Kept the current " + target + " " + saved.key()
                    + " because it differs from the value saved by Iris.");
            return;
        }
        configuration.set(saved.key(), saved.original());
        ServerConfigurator.writeStringAtomic(target, configuration.saveToString());
        IrisLogging.info("Restored " + target + " " + saved.key() + " to "
                + (saved.original() == null ? "its server default" : saved.original())
                + ". Takes effect after a server restart.");
    }

    private List<SavedTimeout> readChanges() throws IOException {
        if (!Files.exists(recoveryFile)) {
            return new ArrayList<>();
        }
        try {
            SavedTimeouts saved = JSON.fromJson(Files.readString(recoveryFile), SavedTimeouts.class);
            if (saved == null) {
                throw new IOException("Empty server timeout recovery data: " + recoveryFile);
            }
            return new ArrayList<>(saved.changes());
        } catch (JsonParseException | IllegalArgumentException exception) {
            throw new IOException("Invalid server timeout recovery data: " + recoveryFile, exception);
        }
    }

    private void writeChanges(List<SavedTimeout> changes) throws IOException {
        ServerConfigurator.writeStringAtomic(recoveryFile, JSON.toJson(new SavedTimeouts(changes)));
    }

    private static SavedTimeout find(List<SavedTimeout> changes, Path target, String key) {
        for (SavedTimeout change : changes) {
            if (change.file().equals(target.toString()) && change.key().equals(key)) {
                return change;
            }
        }
        return null;
    }

    private static Long value(Document configuration, String key) throws IOException {
        if (!configuration.isSet(key)) {
            return null;
        }
        if (!configuration.isInteger(key)) {
            throw new IOException("Server timeout " + key + " must be an integer.");
        }
        return configuration.getLong(key);
    }

    private record SavedTimeout(String file, String key, Long original, long applied) {
        private SavedTimeout {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(key, "key");
            if (!Path.of(file).isAbsolute() || applied <= 0
                    || !key.equals(SPIGOT_TIMEOUT_KEY) && !key.equals(PAPER_WATCHDOG_KEY)) {
                throw new IllegalArgumentException("Invalid saved server timeout");
            }
        }
    }

    private record SavedTimeouts(List<SavedTimeout> changes) {
        private SavedTimeouts {
            changes = List.copyOf(changes);
        }
    }
}
