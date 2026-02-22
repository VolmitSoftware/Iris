package art.arcane.iris.core.safeguard;

import art.arcane.iris.BuildConstants;
import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.util.common.format.C;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Bukkit;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public enum Mode {
    STABLE(C.IRIS),
    WARNING(C.GOLD),
    UNSTABLE(C.RED);

    private final C color;
    private final String id;

    Mode(C color) {
        this.color = color;
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
        String padd = Form.repeat(" ", 8);
        String padd2 = Form.repeat(" ", 4);
        String version = Iris.instance.getDescription().getVersion();
        String releaseTrain = getReleaseTrain(version);
        String serverVersion = getServerVersion();
        String startupDate = getStartupDate();
        int javaVersion = getJavaVersion();

        String[] splash = new String[]{
                padd + C.GRAY + "   @@@@@@@@@@@@@@" + C.DARK_GRAY + "@@@",
                padd + C.GRAY + " @@&&&&&&&&&" + C.DARK_GRAY + "&&&&&&" + color + "   .(((()))).                     ",
                padd + C.GRAY + "@@@&&&&&&&&" + C.DARK_GRAY + "&&&&&" + color + "  .((((((())))))).                  ",
                padd + C.GRAY + "@@@&&&&&" + C.DARK_GRAY + "&&&&&&&" + color + "  ((((((((()))))))))               " + C.GRAY + " @",
                padd + C.GRAY + "@@@&&&&" + C.DARK_GRAY + "@@@@@&" + color + "    ((((((((-)))))))))              " + C.GRAY + " @@",
                padd + C.GRAY + "@@@&&" + color + "            ((((((({ }))))))))           " + C.GRAY + " &&@@@",
                padd + C.GRAY + "@@" + color + "               ((((((((-)))))))))    " + C.DARK_GRAY + "&@@@@@" + C.GRAY + "&&&&@@@",
                padd + C.GRAY + "@" + color + "                ((((((((()))))))))  " + C.DARK_GRAY + "&&&&&" + C.GRAY + "&&&&&&&@@@",
                padd + C.GRAY + "" + color + "                  '((((((()))))))'  " + C.DARK_GRAY + "&&&&&" + C.GRAY + "&&&&&&&&@@@",
                padd + C.GRAY + "" + color + "                     '(((())))'   " + C.DARK_GRAY + "&&&&&&&&" + C.GRAY + "&&&&&&&@@",
                padd + C.GRAY + "                               " + C.DARK_GRAY + "@@@" + C.GRAY + "@@@@@@@@@@@@@@"
        };

        String[] info = new String[]{
                "",
                padd2 + color + " Iris, " + C.AQUA + "Dimension Engine " + C.RED + "[" + releaseTrain + " RELEASE]",
                padd2 + C.GRAY + " Version: " + color + version,
                padd2 + C.GRAY + " By: " + color + "Volmit Software (Arcane Arts)",
                padd2 + C.GRAY + " Server: " + color + serverVersion,
                padd2 + C.GRAY + " Java: " + color + javaVersion + C.GRAY + " | Date: " + color + startupDate,
                padd2 + C.GRAY + " Commit: " + color + BuildConstants.COMMIT + C.GRAY + "/" + color + BuildConstants.ENVIRONMENT,
                "",
                "",
                "",
                ""
        };

        StringBuilder builder = new StringBuilder("\n\n");
        for (int i = 0; i < splash.length; i++) {
            builder.append(splash[i]);
            if (i < info.length) {
                builder.append(info[i]);
            }
            builder.append("\n");
        }

        Iris.info(builder.toString());
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

    private int getJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf('.');
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        return Integer.parseInt(version);
    }

    private String getStartupDate() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String getReleaseTrain(String version) {
        String value = version;
        int suffixIndex = value.indexOf('-');
        if (suffixIndex >= 0) {
            value = value.substring(0, suffixIndex);
        }
        String[] split = value.split("\\.");
        if (split.length >= 2) {
            return split[0] + "." + split[1];
        }
        return value;
    }
}
