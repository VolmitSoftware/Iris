package com.volmit.tooling;

import com.volmit.resource.ResourceId;
import java.util.List;
import java.util.Optional;

/**
 * The structures a platform registers and the templates it loads, for the tools that turn them into pack resources.
 * A command reaches it as {@link CommandContext#platformStructures()}, which is empty on a platform without it. Every
 * method may take a while and reads the running server, so a tool calls them from its worker thread and never
 * from the platform's main or region thread.
 *
 * <p>Each reader that cannot answer because the platform's registries cannot be read throws a
 * {@link StructureAccessException}; a reader that answers empty means the platform has no such thing.
 */
public interface PlatformStructures {
    /** The ids of the structure templates the platform can load, such as {@code minecraft:village/plains/houses/plains_small_house_1}, in id order. */
    List<ResourceId> templates();

    /** The template {@code id}; empty when the platform has none or it holds no palette. */
    Optional<StructureTemplate> template(ResourceId id);

    /** The ids of the structures the platform registers, in id order. */
    List<ResourceId> structures();

    /** The ids of the structure sets the platform registers, in id order. */
    List<ResourceId> structureSets();

    /** What {@code structure} says about itself; empty when it is no jigsaw structure or is not registered. */
    Optional<JigsawStructureInfo> jigsaw(ResourceId structure);

    /** The template pool {@code pool}; empty when it is not registered. */
    Optional<TemplatePool> pool(ResourceId pool);

    /** Whether the platform can generate a structure in a scratch area and read it back, which {@link #openCaptureArea} needs. */
    boolean supportsCapture();

    /**
     * Opens a throwaway area to generate structures in, for the structures that have no template. Close it when the
     * captures are done; it is removed then.
     *
     * @param sender where the platform reports what it does while it opens and removes the area
     * @return the area; empty when it could not be opened, which the platform has told the sender
     */
    Optional<CaptureArea> openCaptureArea(Sender sender);

    /** A throwaway area structures are generated in, one after another. */
    interface CaptureArea extends AutoCloseable {
        /**
         * Generates {@code structure} in the next free spot of the area and reads it back.
         *
         * @param seed    what the platform generates the structure with
         * @param maxSpan the widest and tallest box, in blocks, the platform reads back
         * @return the structure; empty when it placed nothing capturable here, such as one wider than {@code maxSpan}
         * @throws IllegalStateException when the platform could not place it, with the reason
         */
        Optional<CapturedStructure> capture(ResourceId structure, long seed, int maxSpan);

        /** Removes the area. */
        @Override
        void close();
    }
}
