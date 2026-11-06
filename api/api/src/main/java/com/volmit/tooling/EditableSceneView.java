package com.volmit.tooling;

import com.volmit.world.PackWorld;

import java.util.Optional;

/**
 * A scene view whose entries are each written in one file of the pack, which the studio shows as a graph of nodes and
 * rewrites as they are edited. The studio reads and writes the file; the view only tells it which file an entry is and
 * how that file's text reads as nodes.
 */
public interface EditableSceneView extends SceneView {
    /** The file entry {@code key} is written in; empty when it has none of its own. */
    Optional<SceneFile> fileOf(PackWorld world, String key);

    /**
     * {@code text}, the file of entry {@code key}, as nodes. The pack may not have read this text yet, so a node whose
     * part the scene does not show yet has no {@link GraphNode#part()}.
     *
     * @throws IllegalArgumentException when {@code text} does not hold what this view edits
     */
    NodeGraph graph(PackWorld world, String key, JsonText text);
}
