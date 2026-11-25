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

package art.arcane.iris.studio.bridge;

import art.arcane.iris.studio.scene.EditableSceneView.Creation;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

/** The files of one creatable view's entries in a pack: written from the view's starter, looked up where used, and trashed. */
final class SceneFiles {
    private static final Pattern KEY = Pattern.compile("[a-z0-9_-]+(/[a-z0-9_-]+)*");
    private static final String FILE_EXTENSION = ".json";
    // The pack watcher only reloads for .json, .iob and .png files, so a file being written is not read half done.
    static final String WRITING_SUFFIX = ".tmp";
    private static final String GENERATED_FOLDER_PREFIX = ".";
    // The pack watcher ignores the .iris folder, so trashing a file there reloads the pack once, for the removal.
    private static final String PACK_TRASH = ".iris/trash";
    private static final String DESKTOP_TRASH = "the trash";

    /** Where the desktop moves a file to its trash; absent a desktop it moves nothing. */
    @FunctionalInterface
    interface DesktopTrash {
        /** @return whether the desktop took {@code file} to its trash */
        boolean take(Path file);
    }

    private final Path packFolder;
    private final Creation creation;
    private final DesktopTrash desktopTrash;

    SceneFiles(Path packFolder, Creation creation, DesktopTrash desktopTrash) {
        this.packFolder = packFolder;
        this.creation = creation;
        this.desktopTrash = desktopTrash;
    }

    static DesktopTrash systemTrash() {
        return file -> Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)
                && Desktop.getDesktop().moveToTrash(file.toFile());
    }

    /** @return why {@code key} can't name an entry, or null when it can */
    static String refusalOfKey(String key) {
        if (key.isBlank()) {
            return "Give it a name";
        }
        return KEY.matcher(key).matches() ? null : "Use lower case letters, digits, - and _, with / between folders";
    }

    /**
     * Writes a new entry from the starter text at {@code key}.
     *
     * @throws IllegalArgumentException naming why, when {@code key} can't name a new entry or the starter is not JSON
     */
    Path create(String key) throws IOException {
        Path file = fileOf(key);
        if (Files.exists(file)) {
            throw new IllegalArgumentException(key + " already exists");
        }
        JsonText.parse(creation.starter());
        Files.createDirectories(file.getParent());
        Path written = file.resolveSibling(file.getFileName() + WRITING_SUFFIX);
        Files.writeString(written, creation.starter(), StandardCharsets.UTF_8);
        Files.move(written, file, StandardCopyOption.ATOMIC_MOVE);
        return file;
    }

    /**
     * The pack's files that name the entry at {@code key}, such as the biomes that place it, below the pack. Folders
     * starting with a dot hold what the studio generates, like the schema, and are left out.
     */
    List<String> usesOf(String key) throws IOException {
        String reference = "\"" + creation.referencePrefix() + key + "\"";
        Path own = fileOf(key);
        List<String> using = new ArrayList<>();
        Files.walkFileTree(packFolder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path folder, BasicFileAttributes attributes) {
                boolean generated = !folder.equals(packFolder) && folder.getFileName().toString().startsWith(GENERATED_FOLDER_PREFIX);
                return generated ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!file.equals(own) && file.getFileName().toString().endsWith(FILE_EXTENSION) && namesIn(file, reference)) {
                    using.add(packFolder.relativize(file).toString().replace('\\', '/'));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        using.sort(null);
        return using;
    }

    /**
     * Moves the entry at {@code key} to the desktop's trash, or where the desktop has none, to the pack's
     * {@code .iris/trash}; returns where it went, to tell.
     *
     * @throws NoSuchElementException when the entry has no file
     */
    String moveToTrash(String key) throws IOException {
        Path file = fileOf(key);
        if (!Files.isRegularFile(file)) {
            throw new NoSuchElementException("Entry " + key + " has no file at " + packFolder.relativize(file));
        }
        if (desktopTrash.take(file)) {
            return DESKTOP_TRASH;
        }
        Path trashed = packFolder.resolve(PACK_TRASH).resolve(key.replace('/', '_') + "-" + System.currentTimeMillis() + FILE_EXTENSION);
        Files.createDirectories(trashed.getParent());
        Files.move(file, trashed);
        return PACK_TRASH;
    }

    private Path fileOf(String key) {
        String refusal = refusalOfKey(key);
        if (refusal != null) {
            throw new IllegalArgumentException(refusal);
        }
        return packFolder.resolve(creation.folder()).resolve(key + FILE_EXTENSION);
    }

    private static boolean namesIn(Path file, String reference) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains(reference);
    }
}
