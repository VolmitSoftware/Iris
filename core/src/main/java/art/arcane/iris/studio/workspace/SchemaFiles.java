/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.studio.workspace;

import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.io.IO;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Writes schema files and hands back the text written, so the editor settings can carry a hash of what is on disk.
 */
public final class SchemaFiles {
    public static final String FAILED_SCHEMA_TEXT = "";

    private SchemaFiles() {
    }

    /**
     * Builds and writes {@code schemaFile} now, or in the background. A background write that fails is reported and
     * completes with an empty text; a write now throws.
     */
    public static CompletableFuture<String> write(File schemaFile, Supplier<String> schemaText, boolean immediate) {
        if (immediate) {
            return CompletableFuture.completedFuture(writeNow(schemaFile, schemaText));
        }
        return writeAsync(schemaFile, schemaText);
    }

    public static CompletableFuture<String> writeAsync(File schemaFile, Supplier<String> schemaText) {
        return MultiBurst.burst.completeValueAsync(() -> writeReporting(schemaFile, schemaText));
    }

    public static String writeNow(File schemaFile, Supplier<String> schemaText) {
        String text = schemaText.get();
        try {
            IO.writeAll(schemaFile, text);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not write schema " + schemaFile.getAbsolutePath(), failure);
        }
        return text;
    }

    public static String writeReporting(File schemaFile, Supplier<String> schemaText) {
        try {
            return writeNow(schemaFile, schemaText);
        } catch (Throwable failure) {
            IrisLogging.reportError(failure);
            return FAILED_SCHEMA_TEXT;
        }
    }

    public static List<String> awaitTexts(List<CompletableFuture<String>> schemaWrites) {
        return schemaWrites.stream().map(SchemaFiles::awaitText).toList();
    }

    private static String awaitText(CompletableFuture<String> schemaWrite) {
        try {
            return schemaWrite.join();
        } catch (RuntimeException failure) {
            IrisLogging.reportError(failure);
            return FAILED_SCHEMA_TEXT;
        }
    }
}
