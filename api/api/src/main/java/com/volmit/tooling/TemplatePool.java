package com.volmit.tooling;

import com.volmit.geometry.BlockFace;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.ResourceId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * A jigsaw template pool the platform registers: what its entries are and where it falls back to.
 *
 * @param members         the entries in the order the platform lists them, every one of them, so the count is the
 *                        pool's own size
 * @param fallback        the pool used when none of the entries fits; empty when it has none
 * @param fallbackFailure why the fallback could not be read, when it could not; the fallback is then empty
 */
public record TemplatePool(List<Member> members, Optional<ResourceId> fallback, Optional<String> fallbackFailure) {
    public TemplatePool {
        members = List.copyOf(members);
        Objects.requireNonNull(fallback, "fallback");
        Objects.requireNonNull(fallbackFailure, "fallbackFailure");
    }

    /** One entry of a pool. */
    public sealed interface Member permits Absent, Unreadable, Element {
    }

    /** An entry that holds no element. */
    public record Absent() implements Member {
    }

    /** Which part of an entry could not be read. */
    public enum Part {
        /** The element and its weight. */
        ENTRY,
        /** Where the element's template is. */
        TEMPLATE_LOCATION
    }

    /**
     * An entry the platform could not read.
     *
     * @param part   what could not be read
     * @param detail why, as the platform reports it
     */
    public record Unreadable(Part part, String detail) implements Member {
        public Unreadable {
            Objects.requireNonNull(part, "part");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /**
     * One element of a pool with its weight.
     *
     * @param weight          how likely the element is among the entries; at least 1
     * @param type            the platform's name for the kind of element, such as {@code SinglePoolElement}, which tells an
     *                        empty element from one a tool cannot convert when {@code template} is empty
     * @param template        the template the element places; when the platform wraps several elements in a list, the
     *                        first one that places a template; empty for an element that places none
     * @param listLevels      how many list elements wrap the one {@code template} came from
     * @param omittedElements how many elements those lists held besides the one {@code template} came from
     * @param connectors      reads the jigsaw connectors of the element
     */
    public record Element(int weight, String type, Optional<ResourceId> template, int listLevels, int omittedElements,
                          ConnectorSource connectors) implements Member {
        public Element {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(template, "template");
            Objects.requireNonNull(connectors, "connectors");
        }
    }

    /** Reads the jigsaw connectors of one pool element. */
    @FunctionalInterface
    public interface ConnectorSource {
        /**
         * The connectors of the element, in the order the platform shuffles them with a seed.
         *
         * @param shuffleSeeds gives the seed to shuffle with; the platform asks only when it shuffles, so the caller's
         *                     random sequence advances once for each element that has connectors to shuffle
         * @throws StructureAccessException when the platform cannot read the connectors at all
         */
        Connectors read(LongSupplier shuffleSeeds);
    }

    /** Whether the platform could list an element's connectors. */
    public enum Availability {
        READ,
        /** The platform's version has no way to list them. */
        UNAVAILABLE,
        /** The platform answered with no list. */
        NO_COLLECTION
    }

    /**
     * The connectors of an element.
     *
     * @param availability whether {@code connectors} were listed
     * @param connectors   one entry for each jigsaw block, in the platform's shuffled order
     */
    public record Connectors(Availability availability, List<Connector> connectors) {
        public Connectors {
            Objects.requireNonNull(availability, "availability");
            connectors = List.copyOf(connectors);
        }
    }

    /** One jigsaw block of an element. */
    public sealed interface Connector permits JigsawConnector, UnreadableConnector {
    }

    /**
     * A jigsaw block the platform could not convert.
     *
     * @param detail why, as the platform reports it
     */
    public record UnreadableConnector(String detail) implements Connector {
        public UnreadableConnector {
            Objects.requireNonNull(detail, "detail");
        }
    }

    /**
     * A jigsaw block.
     *
     * @param position          where it is, from the element's corner
     * @param front             the side it connects through
     * @param top               the side its top faces, which fixes the roll of a rollable joint
     * @param pool              the pool it pulls its next piece from; empty when it names none
     * @param name              the name other blocks target
     * @param target            the name of the blocks it connects to
     * @param aligned           whether the joint keeps the pieces aligned rather than letting them roll
     * @param finalState        the block it turns into once it connects, as the platform writes block states; empty when
     *                          the block names none
     * @param selectionPriority which block is tried first
     * @param placementPriority which connected piece is expanded first
     */
    public record JigsawConnector(BlockPosition position, BlockFace front, BlockFace top, Optional<ResourceId> pool,
                                  String name, String target, boolean aligned, Optional<String> finalState,
                                  int selectionPriority, int placementPriority) implements Connector {
        public JigsawConnector {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(front, "front");
            Objects.requireNonNull(top, "top");
            Objects.requireNonNull(pool, "pool");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(finalState, "finalState");
        }
    }
}
