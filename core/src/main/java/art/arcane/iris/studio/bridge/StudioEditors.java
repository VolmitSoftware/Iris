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

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.studio.scene.EditableSceneView;
import art.arcane.iris.studio.scene.GraphNode;
import art.arcane.iris.studio.scene.JsonPath;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Reads and edits the files of editable scene views' entries for the studio. Every edit is made on the text as it was
 * read, named by its revision, and splices only the values it changes, so the rest of the file stays as written.
 */
final class StudioEditors {
    // Only a studio world reloads its pack when a file changes; elsewhere an edit would show nothing until a restart.
    private static final String LOCKED_OUTSIDE_STUDIO = "Open the pack in a studio world to edit it";
    private static final String NOT_AN_OBJECT = "The file does not hold a JSON object";

    private final StudioScenes scenes;
    private final Engine engine;
    // A studio world generates from a copy of the pack; edits go to the pack itself, which the world reloads from.
    private final Path packFolder;
    private final Path loadedFolder;
    private final SceneFiles.DesktopTrash desktopTrash;

    StudioEditors(StudioScenes scenes, Engine engine, SceneFiles.DesktopTrash desktopTrash) {
        this.scenes = scenes;
        this.engine = engine;
        this.packFolder = engine.getPackSource().toAbsolutePath().normalize();
        this.loadedFolder = engine.getData().getDataFolder().toPath().toAbsolutePath().normalize();
        this.desktopTrash = desktopTrash;
    }

    /**
     * The entry's file as it is now.
     *
     * @throws NoSuchElementException when there is no such view, or the entry has no file of its own
     */
    EditorState read(String viewId, String key) throws IOException {
        Path file = fileOf(viewId, key);
        return stateOf(viewId, key, file, Files.readString(file, StandardCharsets.UTF_8));
    }

    /**
     * Makes {@code edit} and saves the file.
     *
     * @param revision the {@link EditorState#revision()} the edit was made on
     * @throws StaleEditException when the file changed since {@code revision}
     * @throws EditsLockedException when the world is not a studio world
     * @throws IllegalArgumentException naming why, when the edit does not fit the file
     */
    EditorState edit(String viewId, String key, String revision, GraphEdit edit) throws IOException {
        requireStudioWorld();
        Path file = fileOf(viewId, key);
        JsonSplicer splicer = new JsonSplicer(JsonText.parse(currentText(file, revision)));
        String edited = splicer.apply(edit.splices(splicer));
        JsonText.parse(edited);
        return save(viewId, key, file, edited);
    }

    /**
     * Replaces the whole file, as undoing and redoing do.
     *
     * @throws StaleEditException when the file changed since {@code revision}
     * @throws EditsLockedException when the world is not a studio world
     * @throws IllegalArgumentException when {@code text} is not JSON
     */
    EditorState write(String viewId, String key, String revision, String text) throws IOException {
        requireStudioWorld();
        Path file = fileOf(viewId, key);
        currentText(file, revision);
        JsonText.parse(text);
        return save(viewId, key, file, text);
    }

    /**
     * Writes a new entry {@code key} of the view from its starter text. The entry shows once the pack has reloaded.
     *
     * @throws EditsLockedException when the world is not a studio world
     * @throws IllegalArgumentException naming why, when {@code key} can't name a new entry
     * @throws NoSuchElementException when there is no such view, or its entries can't be created
     */
    void create(String viewId, String key) throws IOException {
        requireStudioWorld();
        files(viewId).create(key);
    }

    /**
     * The files of the pack that still name entry {@code key}, which a removal would leave without it.
     *
     * @throws NoSuchElementException when there is no such view, or its entries can't be removed
     */
    List<String> usesOf(String viewId, String key) throws IOException {
        return files(viewId).usesOf(key);
    }

    /**
     * Moves entry {@code key} to the trash and tells where it went.
     *
     * @throws EditsLockedException when the world is not a studio world
     * @throws NoSuchElementException when there is no such view, or the entry has no file
     */
    String delete(String viewId, String key) throws IOException {
        requireStudioWorld();
        return files(viewId).moveToTrash(key);
    }

    private SceneFiles files(String viewId) {
        EditableSceneView.Creation creation = scenes.editableView(viewId).creation()
                .orElseThrow(() -> new NoSuchElementException("The entries of " + viewId + " can't be created or removed"));
        return new SceneFiles(packFolder, creation, desktopTrash);
    }

    private void requireStudioWorld() {
        if (!engine.isStudio()) {
            throw new EditsLockedException(LOCKED_OUTSIDE_STUDIO);
        }
    }

    private String lockedReason() {
        return engine.isStudio() ? null : LOCKED_OUTSIDE_STUDIO;
    }

    private static String currentText(Path file, String revision) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (!revisionOf(text).equals(revision)) {
            throw new StaleEditException();
        }
        return text;
    }

    // Written beside the file and moved over it, so the pack watcher never reads it half written.
    private EditorState save(String viewId, String key, Path file, String text) throws IOException {
        Path written = file.resolveSibling(file.getFileName() + SceneFiles.WRITING_SUFFIX);
        Files.writeString(written, text, StandardCharsets.UTF_8);
        Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return stateOf(viewId, key, file, text);
    }

    private Path fileOf(String viewId, String key) {
        Path loaded = scenes.editableView(viewId).fileOf(engine, key)
                .orElseThrow(() -> new NoSuchElementException("Entry " + key + " has no file to edit"))
                .toPath().toAbsolutePath().normalize();
        if (!loaded.startsWith(loadedFolder)) {
            throw new NoSuchElementException("Entry " + key + " has no file in the pack");
        }
        Path file = packFolder.resolve(loadedFolder.relativize(loaded));
        if (!Files.isRegularFile(file)) {
            throw new NoSuchElementException("Entry " + key + " has no file in the pack");
        }
        return file;
    }

    // A file that is no longer JSON, or that the view can't read as nodes, is still shown, with why.
    private EditorState stateOf(String viewId, String key, Path file, String text) {
        String fileName = packFolder.relativize(file).toString().replace('\\', '/');
        String revision = revisionOf(text);
        JsonText source;
        try {
            source = JsonText.parse(text);
        } catch (IllegalArgumentException notJson) {
            return EditorState.unreadable(fileName, revision, text, notJson.getMessage(), lockedReason());
        }
        if (!(source.toGson() instanceof JsonObject root)) {
            return EditorState.unreadable(fileName, revision, text, NOT_AN_OBJECT, lockedReason());
        }
        List<GraphNode> graph;
        try {
            graph = scenes.editableView(viewId).graph(engine, key, root);
        } catch (IllegalArgumentException refused) {
            return EditorState.unreadable(fileName, revision, text, String.valueOf(refused.getMessage()), lockedReason());
        }
        Set<Class<?>> types = new LinkedHashSet<>();
        List<NodeWire> roots = graph.stream().map(node -> NodeWire.of(node, types)).toList();
        return new EditorState(fileName, revision, text, roots, null, lockedReason(), SettingsTypes.reachableFrom(types));
    }

    static String revisionOf(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("The JVM has no SHA-256", missing);
        }
    }

    /**
     * An entry's file as the studio shows it.
     *
     * @param file     where it is below the pack folder
     * @param revision tells this text from any other, so an edit made on an older one is refused
     * @param roots    the file as nodes; null when it can't be read as nodes, and {@code problem} says why
     * @param locked   why the file can't be edited here; null when it can
     * @param types    the settings types the nodes show, by class name
     */
    record EditorState(String file, String revision, String text, List<NodeWire> roots, String problem, String locked,
                       Map<String, SettingsTypes.TypeWire> types) {
        static EditorState unreadable(String file, String revision, String text, String problem, String locked) {
            return new EditorState(file, revision, text, null, problem, locked, Map.of());
        }
    }

    /** A {@link GraphNode} as the studio reads it: paths as arrays of member names and indices, settings types by class name. */
    record NodeWire(String kind, String title, List<Object> path, int partId, List<String> roles, List<SettingsWire> settings,
                    List<LinkWire> children, List<Object> childList, boolean movable, boolean duplicable, List<AdditionWire> additions) {
        static NodeWire of(GraphNode node, Set<Class<?>> types) {
            List<SettingsWire> settings = node.settings().stream().map(section -> {
                types.add(section.type());
                return new SettingsWire(section.title(), stepsOf(section.path()), section.type().getName(), List.copyOf(section.shownAsNodes()));
            }).toList();
            List<LinkWire> children = node.children().stream().map(link -> new LinkWire(link.wire(), of(link.node(), types))).toList();
            GraphNode.Edits edits = node.edits();
            List<AdditionWire> additions = edits.additions().stream()
                    .map(addition -> new AdditionWire(addition.label(), stepsOf(addition.list()), addition.literal()))
                    .toList();
            return new NodeWire(node.kind(), node.title(), stepsOf(node.path()), node.part(), node.roles(), settings, children,
                    edits.childList().map(NodeWire::stepsOf).orElse(null), edits.movable(), node.path().isElement(), additions);
        }

        static List<Object> stepsOf(JsonPath path) {
            return path.steps().stream().<Object>map(step -> switch (step) {
                case JsonPath.Key(String name) -> name;
                case JsonPath.Index(int position) -> position;
            }).toList();
        }
    }

    record SettingsWire(String title, List<Object> path, String type, List<String> shownAsNodes) {
    }

    record LinkWire(String wire, NodeWire node) {
    }

    record AdditionWire(String label, List<Object> list, String literal) {
    }

    /** The file changed since the text an edit was made on, such as in a text editor. */
    static final class StaleEditException extends RuntimeException {
        StaleEditException() {
            super("The file changed elsewhere; it was read again");
        }
    }

    /** Edits are refused where the world would not show them, naming why. */
    static final class EditsLockedException extends RuntimeException {
        EditsLockedException(String reason) {
            super(reason);
        }
    }
}
