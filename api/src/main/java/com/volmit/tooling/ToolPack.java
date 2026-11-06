package com.volmit.tooling;

import com.volmit.ModuleLog;
import com.volmit.config.PackSettings;
import com.volmit.resource.PackResources;
import java.io.IOException;
import java.nio.file.Path;

/**
 * A pack as tools see it: its current resources, its folder, and the one way to change files in it. Tools are file tools,
 * so a {@link Path} is honest here; it is confined to {@link #edit} and {@link #outputFolder}.
 */
public interface ToolPack {
    /** The current snapshot; a new one after every edit, so never keep it across an edit. */
    PackResources resources();

    /** Every settings entry of the pack, as validators see them. */
    PackSettings settings();

    /**
     * Runs {@code edit} with this pack locked against other edits and hotloads, then reloads the resources once if it
     * returned normally. One edit runs at a time per pack, so a second caller waits. An edit that throws leaves no reload
     * behind, so it must leave the files as it found them; multi-file atomicity is the edit's own job.
     */
    <T> T edit(PackEdit<T> edit) throws IOException;

    /** A folder outside the pack for what tools produce, such as exports; created on first use. */
    Path outputFolder();

    /** Reads and writes pack objects in the host's object file format. */
    ObjectCodec objects();

    /** The log of the module that asked. */
    ModuleLog log();
}
