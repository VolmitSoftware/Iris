package art.arcane.iris.core.safeguard

import art.arcane.iris.BuildConstants
import art.arcane.iris.Iris
import art.arcane.iris.core.IrisSettings
import art.arcane.iris.util.common.format.C
import art.arcane.volmlib.util.format.Form
import org.bukkit.Bukkit
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class Mode(private val color: C) {
    STABLE(C.IRIS),
    WARNING(C.GOLD),
    UNSTABLE(C.RED);

    val id = name.lowercase()

    fun highest(m: Mode): Mode {
        return if (m.ordinal > ordinal) m else this
    }

    fun tag(subTag: String?): String {
        if (subTag == null || subTag.isBlank()) return wrap("Iris") + C.GRAY + ": "
        return wrap("Iris") + " " + wrap(subTag) + C.GRAY + ": "
    }

    private fun wrap(tag: String?): String {
        return C.BOLD.toString() + "" + C.DARK_GRAY + "[" + C.BOLD + color + tag + C.BOLD + C.DARK_GRAY + "]" + C.RESET
    }

    fun trySplash() {
        if (!IrisSettings.get().general.isSplashLogoStartup) return
        splash()
    }

    fun splash() {
        val padd = Form.repeat(" ", 8)
        val padd2 = Form.repeat(" ", 4)
        val version = Iris.instance.description.version
        val releaseTrain = getReleaseTrain(version)
        val serverVersion = getServerVersion()
        val startupDate = getStartupDate()
        val javaVersion = getJavaVersion()

        val splash = arrayOf(
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
            padd + C.GRAY + "                               " + C.DARK_GRAY + "@@@" + C.GRAY + "@@@@@@@@@@@@@@",
        )

        val info = arrayOf(
            "",
            padd2 + color + " Iris, " + C.AQUA + "Iris, Dimension Engine " + C.RED + "[" + releaseTrain + " RELEASE]",
            padd2 + C.GRAY + " Version: " + color + version,
            padd2 + C.GRAY + " By: " + color + "Volmit Software (Arcane Arts)",
            padd2 + C.GRAY + " Server: " + color + serverVersion,
            padd2 + C.GRAY + " Java: " + color + javaVersion + C.GRAY + " | Date: " + color + startupDate,
            padd2 + C.GRAY + " Commit: " + color + BuildConstants.COMMIT + C.GRAY + "/" + color + BuildConstants.ENVIRONMENT,
            "",
            "",
            "",
            "",
        )


        val builder = StringBuilder("\n\n")
        for (i in splash.indices) {
            builder.append(splash[i])
            if (i < info.size) {
                builder.append(info[i])
            }
            builder.append("\n")
        }

        Iris.info(builder.toString())
    }

    private fun getServerVersion(): String {
        var version = Bukkit.getVersion()
        val mcMarkerIndex = version.indexOf(" (MC:")
        if (mcMarkerIndex != -1) {
            version = version.substring(0, mcMarkerIndex)
        }
        return version
    }

    private fun getJavaVersion(): Int {
        var version = System.getProperty("java.version")
        if (version.startsWith("1.")) {
            version = version.substring(2, 3)
        } else {
            val dot = version.indexOf(".")
            if (dot != -1) {
                version = version.substring(0, dot)
            }
        }
        return version.toInt()
    }

    private fun getStartupDate(): String {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    private fun getReleaseTrain(version: String): String {
        var value = version
        val suffixIndex = value.indexOf("-")
        if (suffixIndex >= 0) {
            value = value.substring(0, suffixIndex)
        }
        val split = value.split('.')
        if (split.size >= 2) {
            return split[0] + "." + split[1]
        }
        return value
    }
}
