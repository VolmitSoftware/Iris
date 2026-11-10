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

package art.arcane.iris.core.studio.scene;

import art.arcane.iris.engine.framework.Engine;
import com.volmit.host.schema.PackSchema;
import com.volmit.tooling.EditableSceneView;
import com.volmit.tooling.GraphNode;
import com.volmit.tooling.JsonPath;
import com.volmit.tooling.JsonText;
import com.volmit.tooling.NodeGraph;
import com.volmit.tooling.SceneCreation;
import com.volmit.tooling.SceneFile;

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
 * The editable scene views of an engine's pack: an entry's file as nodes, and the edits the studio makes to it. The
 * studio reads and writes the file here, modules never do; a studio world reloads the pack when the file changes.
 */
public final class StudioEditors {
    private static final String FILE_EXTENSION = ".json";
    // The pack watcher ignores .tmp files.
    private static final String WRITING_SUFFIX = ".tmp";
    // Only a studio world reloads its pack when a file changes; elsewhere an edit would show nothing until a restart.
    private static final String LOCKED_OUTSIDE_STUDIO = "Open the pack in a studio world to edit it";

    private final ModuleViews<EditableSceneView> views;
    private final Path packFolder;
    private final PackSchema schema;
    private final boolean studioWorld;

    private StudioEditors(ModuleViews<EditableSceneView> views, Path packFolder, PackSchema schema, boolean studioWorld) {
        this.views = views;
        this.packFolder = packFolder;
        this.schema = schema;
        this.studioWorld = studioWorld;
    }

    public static StudioEditors of(Engine engine) {
        return new StudioEditors(ModuleViews.of(engine, EditableSceneView.class, "editable scene view"),
                engine.getData().getDataFolder().toPath().toAbsolutePath().normalize(), engine.getData().getModules().pack().schema(),
                engine.isStudio());
    }

    /** Whether {@code viewId} is a scene view whose entries can be edited as nodes. */
    public boolean edits(String viewId) {
        return views.infos().stream().anyMatch(info -> info.id().equals(viewId));
    }

    /**
     * The entry's file as it is now.
     *
     * @throws NoSuchElementException when there is no such view, or the entry has no file of its own
     */
    public EditorState read(String viewId, String key) throws IOException {
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
    public EditorState edit(String viewId, String key, String revision, GraphEdit edit) throws IOException {
        requireStudioWorld();
        Path file = fileOf(viewId, key);
        String text = currentText(file, revision);
        JsonSplicer splicer = new JsonSplicer(JsonText.parse(text));
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
    public EditorState write(String viewId, String key, String revision, String text) throws IOException {
        requireStudioWorld();
        Path file = fileOf(viewId, key);
        currentText(file, revision);
        JsonText.parse(text);
        return save(viewId, key, file, text);
    }

    /** Whether entries of {@code viewId} can be created and removed from the studio. */
    public boolean creates(String viewId) {
        return views.ask(viewId, "tell whether entries are created", view -> view.creation().isPresent());
    }

    /**
     * Writes a new entry {@code key} of the view from its starter text. The entry shows once the pack has reloaded.
     *
     * @throws EditsLockedException when the world is not a studio world
     * @throws IllegalArgumentException naming why, when {@code key} can't name a new entry
     * @throws NoSuchElementException when there is no such view, or its entries can't be created
     */
    public void create(String viewId, String key) throws IOException {
        requireStudioWorld();
        files(viewId).create(key);
    }

    /**
     * The files of the pack that still name entry {@code key}, which a removal would leave without it.
     *
     * @throws NoSuchElementException when there is no such view, or its entries can't be removed
     */
    public List<String> usesOf(String viewId, String key) throws IOException {
        return files(viewId).usesOf(key);
    }

    /**
     * Moves entry {@code key} to the trash and tells where it went.
     *
     * @throws EditsLockedException when the world is not a studio world
     * @throws NoSuchElementException when there is no such view, or the entry has no file
     */
    public String delete(String viewId, String key) throws IOException {
        requireStudioWorld();
        return files(viewId).moveToTrash(key);
    }

    private SceneFiles files(String viewId) {
        SceneCreation creation = views.ask(viewId, "tell how entries are created", EditableSceneView::creation)
                .orElseThrow(() -> new NoSuchElementException("The entries of " + viewId + " can't be created or removed"));
        return new SceneFiles(packFolder, creation, SceneFiles.systemTrash());
    }

    private void requireStudioWorld() {
        if (!studioWorld) {
            throw new EditsLockedException(LOCKED_OUTSIDE_STUDIO);
        }
    }

    private String lockedReason() {
        return studioWorld ? null : LOCKED_OUTSIDE_STUDIO;
    }

    private String currentText(Path file, String revision) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (!revisionOf(text).equals(revision)) {
            throw new StaleEditException();
        }
        return text;
    }

    // Written beside the file and moved over it, so the pack watcher never reads it half written.
    private EditorState save(String viewId, String key, Path file, String text) throws IOException {
        Path written = file.resolveSibling(file.getFileName() + WRITING_SUFFIX);
        Files.writeString(written, text, StandardCharsets.UTF_8);
        Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return stateOf(viewId, key, file, text);
    }

    private Path fileOf(String viewId, String key) {
        SceneFile sceneFile = views.ask(viewId, "find the file of " + key, view -> view.fileOf(views.world(), key))
                .orElseThrow(() -> new NoSuchElementException("Entry " + key + " has no file to edit"));
        if (!sceneFile.id().namespace().equals(views.world().resources().packId().namespace())) {
            throw new NoSuchElementException("Entry " + key + " is written in another pack, " + sceneFile.id().namespace());
        }
        Path file = packFolder.resolve(sceneFile.kind().folder()).resolve(sceneFile.id().path() + FILE_EXTENSION).normalize();
        if (!file.startsWith(packFolder) || !Files.isRegularFile(file)) {
            throw new NoSuchElementException("Entry " + key + " has no file at " + packFolder.relativize(file));
        }
        return file;
    }

    // A file that is no longer JSON, or that the view can't read as nodes, is still shown, with why.
    private EditorState stateOf(String viewId, String key, Path file, String text) {
        String fileName = packFolder.relativize(file).toString().replace('\\', '/');
        JsonText source;
        try {
            source = JsonText.parse(text);
        } catch (IllegalArgumentException notJson) {
            return new EditorState(fileName, revisionOf(text), text, null, notJson.getMessage(), lockedReason(), Map.of());
        }
        NodeGraph graph;
        try {
            graph = views.ask(viewId, "read " + key + " as nodes", view -> view.graph(views.world(), key, source));
        } catch (IllegalStateException refused) {
            Throwable reason = refused.getCause() == null ? refused : refused.getCause();
            return new EditorState(fileName, revisionOf(text), text, null, String.valueOf(reason.getMessage()), lockedReason(), Map.of());
        }
        Set<String> typeNames = new LinkedHashSet<>();
        List<NodeWire> roots = graph.roots().stream().map(root -> NodeWire.of(root, typeNames)).toList();
        return new EditorState(fileName, revisionOf(text), text, roots, null, lockedReason(), SchemaTypes.reachableFrom(schema, typeNames));
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
     * @param types    the settings types the nodes show, by name
     */
    public record EditorState(String file, String revision, String text, List<NodeWire> roots, String problem, String locked,
                              Map<String, SchemaTypes.TypeWire> types) {
    }

    /**
     * A {@link GraphNode} as the studio reads it: paths as arrays of member names and indices, a node that draws no
     * part as part {@code -1}, and settings types by their schema name.
     */
    public record NodeWire(String kind, String title, List<Object> path, int partId, List<String> roles, List<SettingsWire> settings,
                           List<LinkWire> children, List<Object> childList, boolean movable, boolean duplicable, List<AdditionWire> additions) {
        private static final int NO_PART = -1;

        static NodeWire of(GraphNode node, Set<String> typeNames) {
            List<SettingsWire> settings = node.settings().stream().map(section -> {
                typeNames.add(section.type().getName());
                return new SettingsWire(section.title(), stepsOf(section.path()), section.type().getName(), List.copyOf(section.shownAsNodes()));
            }).toList();
            List<LinkWire> children = node.children().stream().map(link -> new LinkWire(link.wire(), of(link.node(), typeNames))).toList();
            GraphNode.Edits edits = node.edits();
            List<AdditionWire> additions = edits.additions().stream()
                    .map(addition -> new AdditionWire(addition.label(), stepsOf(addition.list()), addition.literal()))
                    .toList();
            return new NodeWire(node.kind(), node.title(), stepsOf(node.path()), node.part().orElse(NO_PART), node.roles(), settings,
                    children, edits.childList().map(NodeWire::stepsOf).orElse(null), edits.movable(), node.path().isElement(), additions);
        }

        static List<Object> stepsOf(JsonPath path) {
            return path.steps().stream().<Object>map(step -> switch (step) {
                case JsonPath.Key(String name) -> name;
                case JsonPath.Index(int position) -> position;
            }).toList();
        }
    }

    public record SettingsWire(String title, List<Object> path, String type, List<String> shownAsNodes) {
    }

    public record LinkWire(String wire, NodeWire node) {
    }

    public record AdditionWire(String label, List<Object> list, String literal) {
    }

    /** The file changed since the text an edit was made on, such as in a text editor. */
    public static final class StaleEditException extends RuntimeException {
        StaleEditException() {
            super("The file changed elsewhere; it was read again");
        }
    }

    /** Edits are refused where the world would not show them, naming why. */
    public static final class EditsLockedException extends RuntimeException {
        EditsLockedException(String reason) {
            super(reason);
        }
    }
}
