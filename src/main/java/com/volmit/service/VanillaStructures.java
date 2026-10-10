package com.volmit.service;

import com.volmit.resource.ResourceId;
import com.volmit.vanilla.PlatformStart;
import com.volmit.vanilla.StartMemo;
import com.volmit.vanilla.StartReach;
import com.volmit.vanilla.StructureFit;
import com.volmit.vanilla.VanillaStructureContext;
import com.volmit.vanilla.VanillaStructureRule;
import com.volmit.vanilla.VanillaStructureSetup;
import com.volmit.vanilla.VanillaStructureStart;
import com.volmit.vanilla.VanillaStructureStartContext;
import java.util.List;
import java.util.Optional;

/** A module's say over the platform's own structures, the ones Minecraft, data packs and mods register, in the pack's
 * worlds. The host asks every provider, in module id order:
 *
 * <ul>
 *   <li>{@link #rule} before the platform starts one of its structures, and when it locates one;</li>
 *   <li>{@link #starts} for every chunk, for the starts modules place where the platform would not;</li>
 *   <li>{@link #shift} before it publishes a start the platform made itself, and {@link #reach} before it publishes any
 *   start;</li>
 *   <li>{@link #beforePieces} and {@link #afterPieces} around the platform placing the starts' pieces in a chunk;</li>
 *   <li>{@link #setup} before a world of the dimension exists, when the platform sets up its structures and when the
 *   host installs data packs.</li>
 * </ul>
 *
 * <p>The host asks {@link #rule} once per world, structure and pack snapshot and keeps the answer, so a rule is decided
 * from the pack (its settings and resources) and never from where a start would be. Rules combine with
 * {@link VanillaStructureRule#and}: any module may block the platform's starts. Shifts add up; reaches are all covered;
 * setups combine with {@link VanillaStructureSetup#and}. Every method may be called from any thread, concurrently, but
 * one chunk's {@link StructureFit} is used on one thread.
 */
@Service(Providers.ALL)
public interface VanillaStructures {
    /** What this module wants for the platform structure {@code structure}, such as {@code minecraft:village_plains},
     * in the world {@code context} describes. {@link VanillaStructureRule#GENERATE} when the module has no part in it.
     */
    default VanillaStructureRule rule(ResourceId structure, VanillaStructureContext context) {
        return VanillaStructureRule.GENERATE;
    }

    /** The platform structures this module starts in chunk {@code chunkX, chunkZ}: starts the platform generates from
     * that chunk, wherever its own placement rules would put them. At most one start per structure and chunk counts;
     * the host keeps the first, in module id order. Decided from the world and the chunk only, so the same chunk always
     * gets the same starts: the host asks again when it locates structures and when it checks a start it finds in
     * the world.
     */
    default List<VanillaStructureStart> starts(int chunkX, int chunkZ, VanillaStructureStartContext context) {
        return List.of();
    }

    /** How many blocks the platform's own start {@code start} moves up (negative: down) before the host publishes it.
     * Decided from the start's place and the terrain only, so asking again for the same start gives the same answer:
     * the host asks again when it predicts where the platform's starts end up. The host keeps the start inside the
     * world, moving it less when it would leave it. 0 when the module has no part in it.
     */
    default int shift(PlatformStart start, VanillaStructureContext context) {
        return 0;
    }

    /** How far around its pieces this module changes the terrain for a start of {@code structure}: a start the module
     * placed itself, with the {@code memo} it gave it, or one of the platform's own starts (no memo; {@code underground}
     * says whether the platform generates the structure in one of its underground steps). The platform hands the start to
     * every chunk the reach covers, so {@link #beforePieces} sees it there. {@link StartReach#PLATFORM} when the module
     * changes no more than the platform's own terrain adaptation would.
     */
    default StartReach reach(ResourceId structure, boolean underground, Optional<StartMemo> memo,
                             VanillaStructureContext context) {
        return StartReach.PLATFORM;
    }

    /** Called once per chunk before the platform places any piece of {@code fit}'s starts there: the module fits the
     * terrain to the starts it cares about, writing only inside {@link StructureFit#area()}.
     */
    default void beforePieces(StructureFit fit) {
    }

    /** Called after the platform placed the pieces of {@code placed}, one of {@code fit}'s starts, in the chunk, before
     * it places the next start's; the last call of a chunk is for the last of {@link StructureFit#starts()}.
     */
    default void afterPieces(StructureFit fit, StructureFit.Start placed) {
    }

    /** What this module changes about how the platform sets up its structures for the dimension {@code context}
     * describes. Protecting the platform's definitions in one dimension protects them in every data pack the host
     * installs, since those are shared by every world. {@link VanillaStructureSetup#UNCHANGED} when the module has no
     * part in it.
     */
    default VanillaStructureSetup setup(VanillaStructureContext context) {
        return VanillaStructureSetup.UNCHANGED;
    }
}
