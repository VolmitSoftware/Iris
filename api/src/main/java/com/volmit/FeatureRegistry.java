package com.volmit;

import com.volmit.mantle.MantleCodec;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLifetime;
import com.volmit.resource.ResourceKind;
import com.volmit.service.Service;
import com.volmit.stage.StageGraph;
import com.volmit.validation.Validator;
import java.util.Set;

/** Host implementation collects non-stage registrations before loading pack configuration.
 * Feature IDs must be unique. JSON keys must be unique per {@link com.volmit.config.Level}, including the
 * settings types stages name in {@code when(...)}. Feature IDs, mantle keys and resource kind ids a module
 * registers are its module id or start with it and {@code /}, such as {@code iris:snow} or {@code iris:snow/depth}.
 */
public interface FeatureRegistry {
    /** Registers a layer before pack loading. Null arguments throw NullPointerException;
     * duplicate IDs and negative codec versions throw IllegalArgumentException.
     * Only the owner registers a shared layer; consumers refer to its key. The host must
     * preserve unknown persisted layers or fail explicitly, never silently discard them.
     * GENERATION layers also require codecs.
     */
    <T> void registerMantle(MantleKey<T> key, MantleLifetime lifetime, MantleCodec<T> codec);

    /** Catalogs settings types that no stage names in {@code when(...)}, such as ones read only
     * through {@link com.volmit.config.FeatureSettings#at}, so the host decodes them and studio
     * describes them: {@code registerSettings(WindSettings.class, GustSettings.class)}. Settings that a
     * stage names are catalogued without this; registering them as well changes nothing. Each type's
     * feature id must be this module's id or start with it and {@code /}. Once registered, other
     * modules may name the types in {@code when(...)} (see {@link StageGraph}).
     */
    void registerSettings(Class<? extends IrisFeature>... settingsTypes);

    /** Declares a kind of pack resource this module reads: the JSON files under the kind's folder, served through
     * {@link com.volmit.resource.PackResources} by the kind's type and described for studio from its class like
     * settings. The pack load fails when another kind has the same id, type or folder, or the folder is one the
     * host reads itself; the error names both owners.
     */
    <T> void registerResourceKind(ResourceKind<T> kind);

    /**
     * Declares the type packs write their object placements as, the JSON object that holds
     * {@link com.volmit.config.Level#PLACEMENT}: settings other modules declare on a placement are keys of that same
     * object, next to the type's own fields. So no settings key at that level may be one of {@code jsonFields}, the
     * names the type's JSON uses, and studio describes {@code type} with those settings added. A pack has one placement
     * type; a second declaration, from this module or another, fails the pack load naming both.
     */
    void registerPlacementType(Class<?> type, Set<String> jsonFields);

    /** Adds a check the host runs over the whole pack wherever it validates packs. */
    void registerValidator(Validator validator);

    /** Adds hooks the host calls when it loads the pack and starts or stops a world with it. */
    void registerLifecycle(ModuleLifecycle lifecycle);

    /** Provides {@code service}, an interface marked {@link Service}, with {@code provider}. A service whose providers
     * are {@link com.volmit.service.Providers#ONE} fails the pack load when a second module provides it, naming both.
     */
    <S> void registerService(Class<S> service, S provider);
}
