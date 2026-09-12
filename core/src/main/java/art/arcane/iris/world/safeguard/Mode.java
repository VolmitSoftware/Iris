package art.arcane.iris.world.safeguard;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.diagnostics.splash.IrisSplashComposer;
import art.arcane.iris.diagnostics.splash.IrisSplashRenderer;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.localization.C;
import org.bukkit.Bukkit;

import java.util.Locale;

public enum Mode {
    STABLE(C.IRIS, C.AQUA),
    WARNING(C.GOLD, C.YELLOW),
    UNSTABLE(C.RED, C.GOLD);

    private final C color;
    private final C glow;
    private final String id;

    Mode(C color, C glow) {
        this.color = color;
        this.glow = glow;
        this.id = name().toLowerCase(Locale.ROOT);
    }

    public String getId() {
        return id;
    }

    public Mode highest(Mode mode) {
        if (mode.ordinal() > ordinal()) {
            return mode;
        }
        return this;
    }

    public String tag(String subTag) {
        if (subTag == null || subTag.isBlank()) {
            return wrap("Iris") + C.GRAY + ": ";
        }
        return wrap("Iris") + " " + wrap(subTag) + C.GRAY + ": ";
    }

    public void trySplash() {
        if (!IrisSettings.get().getGeneral().isSplashLogoStartup()) {
            return;
        }
        splash();
    }

    public void splash() {
        String version = BukkitPlatform.plugin().getDescription().getVersion();
        String[] splash = IrisSplashRenderer.render(this::splashTone);
        String[] info = IrisSplashComposer.composeInfo(version, getServerVersion(), infoStyle());
        IrisLogging.info(IrisSplashComposer.compose(splash, info));
    }

    private IrisSplashComposer.InfoStyle infoStyle() {
        return new IrisSplashComposer.InfoStyle() {
            @Override
            public String linePrefix() {
                return " ".repeat(4);
            }

            @Override
            public String title(String text) {
                return color + text;
            }

            @Override
            public String subtitle(String text) {
                return C.AQUA + text;
            }

            @Override
            public String tag(String text) {
                return C.RED + text;
            }

            @Override
            public String label(String text) {
                return C.GRAY + text;
            }

            @Override
            public String value(String text) {
                return color + text;
            }
        };
    }

    private String splashTone(char glyph) {
        return switch (glyph) {
            case '=' -> C.GRAY.toString();
            case '*' -> glow.toString();
            case '%', '@' -> color.toString();
            case '(', ')' -> C.WHITE.toString();
            default -> C.DARK_GRAY.toString();
        };
    }

    private String wrap(String tag) {
        return C.BOLD.toString() + C.DARK_GRAY + "[" + C.BOLD + color + tag + C.BOLD + C.DARK_GRAY + "]" + C.RESET;
    }

    private String getServerVersion() {
        String version = Bukkit.getVersion();
        int marker = version.indexOf(" (MC:");
        if (marker != -1) {
            return version.substring(0, marker);
        }
        return version;
    }
}
