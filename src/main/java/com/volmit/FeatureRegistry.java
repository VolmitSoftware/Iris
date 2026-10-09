package com.volmit;

import com.volmit.mantle.MantleCodec;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLifetime;
import com.volmit.object.ObjectGeneratorFactory;
import com.volmit.resource.ResourceKind;
import com.volmit.service.Service;
import com.volmit.stage.StageGraph;
import com.volmit.validation.Validator;
import com.volmit.vanilla.VanillaStructureHook;

/** Host implementation collects non-stage registrations before loading pack configuration.
 * Feature IDs must be unique. JSON fields must be unique per attachment target, including the
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

    <F extends IrisFeature> void registerObject(Class<F> featureType, ObjectGeneratorFactory<F> factory);

    /** Catalogs a settings type that no stage names in {@code when(...)}, such as one read only
     * through {@link com.volmit.config.FeatureSettings#at}, so the host decodes it and studio
     * describes it. Settings that a stage names are catalogued without this; registering them as
     * well changes nothing. The type's feature id must be this module's id or start with it and
     * {@code /}. Once registered, other modules may name the type in {@code when(...)} (see
     * {@link StageGraph}).
     */
    void registerSettings(Class<? extends IrisFeature> settingsType);

    /** Describes a class that Iris's own classes hold, such as the clamp of an object placement, so studio shows
     * its fields as this class declares them. Only built-in modules may register one: the host classes that hold
     * the class are Iris's, so a pack module's class could never be a field of one. The host reads the class
     * with its fields in pack JSON, and the class follows the rules of settings classes. Registering a class twice
     * changes nothing.
     */
    void registerHeldType(Class<?> type);

    /** Declares a kind of pack resource this module reads: the JSON files under the kind's folder, served through
     * {@link com.volmit.resource.PackResources} by the kind's type and described for studio from its class like
     * settings. The pack load fails when another kind has the same id, type or folder, or the folder is one the
     * host reads itself; the error names both owners.
     */
    <T> void registerResourceKind(ResourceKind<T> kind);

    /** Adds a check the host runs over the whole pack wherever it validates packs. */
    void registerValidator(Validator validator);

    /** Adds hooks the host calls when it loads the pack and starts or stops a world with it. */
    void registerLifecycle(ModuleLifecycle lifecycle);

    /** Provides {@code service}, an interface marked {@link Service}, with {@code provider}. A service whose providers
     * are {@link com.volmit.service.Providers#ONE} fails the pack load when a second module provides it, naming both.
     */
    <S> void registerService(Class<S> service, S provider);

    /** Lets this module decide over the platform's own structures in the pack's worlds: block them, leave them to the
     * platform, or place them itself.
     */
    void registerVanillaStructureHook(VanillaStructureHook hook);
}
