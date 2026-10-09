package com.volmit.vanilla;

import com.volmit.resource.ResourceId;
import java.util.List;

/** A module's say over the platform's own structures. Modules register it with
 * {@link com.volmit.FeatureRegistry#registerVanillaStructureHook}; the host asks every hook of the pack, in module id
 * order:
 *
 * <ul>
 *   <li>{@link #rule} before the platform starts one of its structures, and when it locates one;</li>
 *   <li>{@link #adjustment} before it places a start the platform made itself;</li>
 *   <li>{@link #starts} for every chunk, for the starts modules place where the platform would not;</li>
 *   <li>{@link #setup} before a world of the dimension exists, when the platform sets up its structures and when the
 *   host installs data packs.</li>
 * </ul>
 *
 * <p>The host asks {@link #rule} and {@link #adjustment} once per world, structure and pack snapshot and keeps the
 * answer, so they decide from the pack (its settings and resources) and never from where a start would be. Rules of
 * several modules combine with {@link VanillaStructureRule#and}: any module may block the platform's starts.
 * Adjustments combine with {@link VanillaStructureAdjustment#then}, setups with {@link VanillaStructureSetup#and}.
 * Every method may be called from any thread, concurrently.
 */
@FunctionalInterface
public interface VanillaStructureHook {
    /** What this module wants for the platform structure {@code structure}, such as {@code minecraft:village_plains},
     * in the world {@code context} describes. {@link VanillaStructureRule#GENERATE} when the module has no part in it.
     */
    VanillaStructureRule rule(ResourceId structure, VanillaStructureContext context);

    /** What this module changes about the platform's own starts of {@code structure}; {@code underground} says whether
     * the platform generates it in one of its underground steps (strongholds, mineshafts, trial chambers, ancient
     * cities). {@link VanillaStructureAdjustment#NONE} when the module has no part in it.
     */
    default VanillaStructureAdjustment adjustment(ResourceId structure, boolean underground, VanillaStructureContext context) {
        return VanillaStructureAdjustment.NONE;
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

    /** What this module changes about how the platform sets up its structures for the dimension {@code context}
     * describes. Protecting the platform's definitions in one dimension protects them in every data pack the host
     * installs, since those are shared by every world. {@link VanillaStructureSetup#UNCHANGED} when the module has no
     * part in it.
     */
    default VanillaStructureSetup setup(VanillaStructureContext context) {
        return VanillaStructureSetup.UNCHANGED;
    }
}
