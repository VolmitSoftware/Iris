package com.volmit.iris.core.world;

import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.util.collection.KList;

import java.io.File;

public interface WorldStorage {
    static WorldStorage get() {
        return INMS.get().getWorldStorage();
    }

    /**
     * Also resolves worlds that are not loaded.
     */
    File worldFolder(String worldName);

    KList<File> datapackFolders();

    /**
     * Has to run before the server loads the world, because the server deletes the old folder while migrating it.
     */
    default void adoptLegacyData(String worldName) {
    }
}
