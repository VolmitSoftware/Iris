package art.arcane.iris.platform.bukkit.registry;

import org.bukkit.Particle;

import static art.arcane.iris.platform.bukkit.registry.RegistryUtil.find;

/**
 * Bukkit particle constants. Statically imported by {@code IrisEntity} (a Gson-registered pack
 * type), so this class is reachable from core on the modded loaders. The resolution below is
 * therefore guarded against the absent Bukkit class: without the guard the class initializer dies
 * with a NoClassDefFoundError and the class stays permanently erroneous for the rest of the JVM's
 * life. On Bukkit a genuinely missing registry key still throws, exactly as before.
 */
public class Particles {
    public static final Particle CRIT_MAGIC = resolve("crit_magic", "crit");
    public static final Particle REDSTONE = resolve("redstone", "dust");
    public static final Particle ITEM = resolve("item_crack", "item");

    private static Particle resolve(String... keys) {
        try {
            return find(Particle.class, keys);
        } catch (NoClassDefFoundError e) {
            // No org.bukkit.Particle on this platform. Every read of these constants is Bukkit-only, so null is
            // correct here. Narrower than LinkageError on purpose: a VerifyError, an IncompatibleClassChangeError or
            // an ExceptionInInitializerError from the registry itself is a real defect on a Bukkit server and must
            // not be silently turned into a null constant.
            return null;
        }
    }
}
