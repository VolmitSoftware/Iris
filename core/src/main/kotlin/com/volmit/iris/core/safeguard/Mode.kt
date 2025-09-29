package com.volmit.iris.core.safeguard

import com.volmit.iris.BuildConstants
import com.volmit.iris.Iris
import com.volmit.iris.core.IrisSettings
import com.volmit.iris.util.format.C
import com.volmit.iris.util.format.Form

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
            "",
            "",
            "",
            "",
            padd2 + color + " Iris",
            padd2 + C.GRAY + " by " + color + "Volmit Software",
            padd2 + C.GRAY + " v" + color + Iris.instance.description.version,
            padd2 + C.GRAY + " c" + color + BuildConstants.COMMIT + C.GRAY + "/" + color + BuildConstants.ENVIRONMENT,
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
}