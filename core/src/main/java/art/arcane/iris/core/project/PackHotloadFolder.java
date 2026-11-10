package art.arcane.iris.core.project;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.function.Consumer3;
import art.arcane.volmlib.util.io.ReactiveFolder;

import java.io.File;

public final class PackHotloadFolder {
    private static final String EDITOR_SETTINGS_FOLDER = File.separator + ".zed" + File.separator;

    private PackHotloadFolder() {
    }

    public static ReactiveFolder watch(File packFolder, Consumer3<KList<File>, KList<File>, KList<File>> hotload) {
        return new ReactiveFolder(
                packFolder,
                hotload,
                new KList<>(".iob", ".json", ".jar"),
                new KList<>(".iris", EDITOR_SETTINGS_FOLDER),
                new KList<>()
        );
    }
}
