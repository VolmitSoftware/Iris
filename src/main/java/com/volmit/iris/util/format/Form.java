/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.util.format;

import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RollingSequence;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Form {
    private static final String[] NAMES = new String[]{"Thousand", "Million", "Billion", "Trillion", "Quadrillion", "Quintillion", "Sextillion", "Septillion", "Octillion", "Nonillion", "Decillion", "Undecillion", "Duodecillion", "Tredecillion", "Quattuordecillion", "Quindecillion", "Sexdecillion", "Septendecillion", "Octodecillion", "Novemdecillion", "Vigintillion",};
    private static final BigInteger THOUSAND = BigInteger.valueOf(1000);
    private static final NavigableMap<BigInteger, String> MAP;
    private static NumberFormat NF;
    private static DecimalFormat DF;

    static {
        MAP = new TreeMap<>();
        for (int i = 0; i < NAMES.length; i++) {
            MAP.put(THOUSAND.pow(i + 1), NAMES[i]);
        }
    }

    public static String getNumberSuffixThStRd(int day) {
        if (day >= 11 && day <= 13) {
            return Form.f(day) + "th";
        }
        return switch (day % 10) {
            case 1 -> Form.f(day) + "st";
            case 2 -> Form.f(day) + "nd";
            case 3 -> Form.f(day) + "rd";
            default -> Form.f(day) + "th";
        };
    }

    private static void instantiate() {
        if (NF == null) {
            NF = NumberFormat.getInstance(Locale.US);
        }
    }

    /**
     * Scroll text
     *
     * @param smx      the text
     * @param viewport the viewport length
     * @param time     the timeline value
     */
    public static String scroll(String smx, int viewport, long time) {
        String src = Form.repeat(" ", viewport) + smx + Form.repeat(" ", viewport);
        int len = src.length();
        int walk = (int) (time % (len - viewport));
        String base = src.substring(walk, M.min(walk + viewport, len - 1));
        base = base.length() < viewport ? base + Form.repeat(" ", (viewport - base.length()) - 3) : base;

        return base;
    }

    /**
     * Capitalize the first letter
     *
     * @param s the string
     * @return the capitalized string
     */
    public static String capitalize(String s) {
        StringBuilder roll = new StringBuilder();
        boolean f = true;

        for (Character i : s.trim().toCharArray()) {
            if (f) {
                roll.append(Character.toUpperCase(i));
                f = false;
            } else {
                roll.append(i);
            }
        }

        return roll.toString();
    }

    /**
     * Capitalize all words in the string
     *
     * @param s the string
     * @return the capitalized string
     */
    public static String capitalizeWords(String s) {
        StringBuilder rollx = new StringBuilder();

        for (String i : s.trim().split(" ")) {
            rollx.append(" ").append(capitalize(i.trim()));
        }

        return rollx.substring(1);
    }

    /**
     * Hard word wrap
     *
     * @param s   the words
     * @param len the length per line
     * @return the wrapped string
     */
    public static String wrap(String s, int len) {
        return wrap(s, len, null, false);
    }

    /**
     * Soft Word wrap
     *
     * @param s   the string
     * @param len the length to wrap
     * @return the wrapped string
     */
    public static String wrapWords(String s, int len) {
        return wrap(s, len, null, true);
    }

    /**
     * Wrap words
     *
     * @param s          the string
     * @param len        the wrap length
     * @param newLineSep the new line seperator
     * @param soft       should it be soft wrapped or hard wrapped?
     * @return the wrapped words
     */
    public static String wrap(String s, int len, String newLineSep, boolean soft) {
        return wrap(s, len, newLineSep, soft, " ");
    }

    public static String hardWrap(String s, int len) {
        StringBuilder ss = new StringBuilder();

        for (int i = 0; i < s.length(); i += len) {
            if (i + len > s.length()) {
                ss.append(s, i, s.length());
                break;
            }

            ss.append(s, i, i + len).append("\n");
        }

        return ss.toString();
    }

    public static List<String> hardWrapList(String s, int len) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < s.length(); i += len) {
            if (i + len > s.length()) {
                l.add(s.substring(i));
                break;
            }

            l.add(s.substring(i, i + len));
        }

        return l;
    }


    /**
     * Wrap words
     *
     * @param s          the string
     * @param len        the length
     * @param newLineSep the new line seperator
     * @param soft       soft or hard wrapping
     * @param regex      the regex
     * @return the wrapped string
     */
    public static String wrap(String s, int len, String newLineSep, boolean soft, String regex) {
        if (s == null) {
            return null;
        } else {
            if (newLineSep == null) {
                newLineSep = "\n";
            }

            if (len < 1) {
                len = 1;
            }

            if (regex.trim().equals("")) {
                regex = " ";
            }

            Pattern arg4 = Pattern.compile(regex);
            int arg5 = s.length();
            int arg6 = 0;
            StringBuilder arg7 = new StringBuilder(arg5 + 32);

            while (arg6 < arg5) {
                int arg8 = -1;
                Matcher arg9 = arg4.matcher(s.substring(arg6, Math.min(arg6 + len + 1, arg5)));
                if (arg9.find()) {
                    if (arg9.start() == 0) {
                        arg6 += arg9.end();
                        continue;
                    }

                    arg8 = arg9.start();
                }

                if (arg5 - arg6 <= len) {
                    break;
                }

                while (arg9.find()) {
                    arg8 = arg9.start() + arg6;
                }

                if (arg8 >= arg6) {
                    arg7.append(s, arg6, arg8);
                    arg7.append(newLineSep);
                    arg6 = arg8 + 1;
                } else if (soft) {
                    arg7.append(s, arg6, len + arg6);
                    arg7.append(newLineSep);
                    arg6 += len;
                } else {
                    arg9 = arg4.matcher(s.substring(arg6 + len));
                    if (arg9.find()) {
                        arg8 = arg9.start() + arg6 + len;
                    }

                    if (arg8 >= 0) {
                        arg7.append(s, arg6, arg8);
                        arg7.append(newLineSep);
                        arg6 = arg8 + 1;
                    } else {
                        arg7.append(s.substring(arg6));
                        arg6 = arg5;
                    }
                }
            }

            arg7.append(s.substring(arg6));
            return arg7.toString();
        }
    }

    /**
     * Returns a fancy duration up to Years
     *
     * @param duration the duration in ms
     * @return the fancy duration
     */
    public static String duration(RollingSequence rollingSequence, long duration) {
        String suffix = "Millisecond";
        double phantom = duration;
        int div = 1000;

        if (phantom > div) {
            phantom /= div;
            suffix = "Second";
            div = 60;

            if (phantom > div) {
                phantom /= div;
                suffix = "Minute";

                if (phantom > div) {
                    phantom /= div;
                    suffix = "Hour";
                    div = 24;

                    if (phantom > 24) {
                        phantom /= div;
                        suffix = "Day";
                        div = 7;

                        if (phantom > div) {
                            phantom /= div;
                            suffix = "Week";
                            div = 4;

                            if (phantom > div) {
                                phantom /= div;
                                suffix = "Month";
                                div = 12;

                                //noinspection IfStatementWithIdenticalBranches
                                if (phantom > div) {
                                    phantom /= div;
                                    suffix = "Year";
                                    return Form.fd(phantom, 0) + " " + suffix + ((int) phantom == 1 ? "" : "s");
                                } else {
                                    return Form.fd(phantom, 0) + " " + suffix + ((int) phantom == 1 ? "" : "s");
                                }
                            } else {
                                return Form.fd(phantom, 0) + " " + suffix + ((int) phantom == 1 ? "" : "s");
                            }
                        } else {
                            return Form.fd(phantom, 0) + " " + suffix + ((int) phantom == 1 ? "" : "s");
                        }
                    } else {
                        return Form.fd(phantom, 0) + " " + suffix + ((int) phantom == 1 ? "" : "s");
                    }
                } else {
                    return Form.fd(phantom, 0) + " " + suffix + ((int) phantom == 1 ? "" : "s");
                }
            } else {
                return Form.fd(phantom, 0) + " " + suffix + ((int) phantom == 1 ? "" : "s");
            }
        } else {
            return "Under a Second";
        }
    }

    /**
     * Fixes the minute issue with formatting
     *
     * @param c the calendar
     * @return the minute string
     */
    public static String fmin(Calendar c) {
        String s = c.get(Calendar.MINUTE) + "";
        if (s.length() == 1) {
            return "0" + s;
        }

        return s;
    }

    /**
     * Get a fancy time stamp
     *
     * @param time the stamp in time (ago)
     * @return the fancy stamp in time (ago)
     */
    public static String ago(long time) {
        long current = M.ms();

        if (time > current - TimeUnit.SECONDS.toMillis(30) && time < current) {
            return "Just Now";
        } else if (time > current - TimeUnit.SECONDS.toMillis(60) && time < current) {
            return "Seconds Ago";
        } else if (time > current - TimeUnit.MINUTES.toMillis(10) && time < current) {
            return "Minutes Ago";
        } else {
            Calendar now = Calendar.getInstance();
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(time);
            boolean sameYear = now.get(Calendar.YEAR) == c.get(Calendar.YEAR);
            boolean sameDay = now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR);

            if (sameDay) {
                int h = c.get(Calendar.HOUR);
                h = h == 0 ? 12 : h;

                return "Today at " + h + ":" + fmin(c) + " " + (c.get(Calendar.AM_PM) == Calendar.PM ? "PM" : "AM");
            } else if (sameYear) {
                boolean yesterday = now.get(Calendar.DAY_OF_YEAR) - 1 == c.get(Calendar.DAY_OF_YEAR);

                if (yesterday) {
                    int h = c.get(Calendar.HOUR);
                    h = h == 0 ? 12 : h;

                    return "Yesterday at " + h + ":" + fmin(c) + " " + (c.get(Calendar.AM_PM) == Calendar.PM ? "PM" : "AM");
                } else {
                    int h = c.get(Calendar.HOUR);
                    h = h == 0 ? 12 : h;
                    String dow = switch (c.get(Calendar.DAY_OF_WEEK)) {
                        case Calendar.SUNDAY -> "Sunday";
                        case Calendar.MONDAY -> "Monday";
                        case Calendar.TUESDAY -> "Tuesday";
                        case Calendar.WEDNESDAY -> "Wednesday";
                        case Calendar.THURSDAY -> "Thursday";
                        case Calendar.FRIDAY -> "Friday";
                        case Calendar.SATURDAY -> "Saturday";
                        default -> "Error Day";
                    };

                    String monthName = "Error Month";
                    int month = c.get(Calendar.MONTH);

                    switch (month) {
                        case Calendar.JANUARY -> monthName = "Jan";
                        case Calendar.FEBRUARY -> monthName = "Feb";
                        case Calendar.MARCH -> monthName = "Mar";
                        case Calendar.APRIL -> monthName = "Apr";
                        case Calendar.MAY -> monthName = "May";
                        case Calendar.JUNE -> monthName = "Jun";
                        case Calendar.JULY -> monthName = "Jul";
                        case Calendar.AUGUST -> monthName = "Aug";
                        case Calendar.SEPTEMBER -> monthName = "Sep";
                        case Calendar.OCTOBER -> monthName = "Oct";
                        case Calendar.NOVEMBER -> monthName = "Nov";
                        case Calendar.DECEMBER -> monthName = "Dec";
                    }

                    int dayOfMonth = c.get(Calendar.DAY_OF_MONTH);
                    String suffix = numberSuffix(dayOfMonth);

                    return dow + ", " + monthName + " " + suffix + " at " + h + ":" + fmin(c) + " " + (c.get(Calendar.AM_PM) == Calendar.PM ? "PM" : "AM");
                }
            } else {
                int h = c.get(Calendar.HOUR);
                h = h == 0 ? 12 : h;
                String dow = switch (c.get(Calendar.DAY_OF_WEEK)) {
                    case Calendar.SUNDAY -> "Sunday";
                    case Calendar.MONDAY -> "Monday";
                    case Calendar.TUESDAY -> "Tuesday";
                    case Calendar.WEDNESDAY -> "Wednesday";
                    case Calendar.THURSDAY -> "Thursday";
                    case Calendar.FRIDAY -> "Friday";
                    case Calendar.SATURDAY -> "Saturday";
                    default -> "Error Day";
                };

                String monthName = "Error Month";
                int month = c.get(Calendar.MONTH);

                switch (month) {
                    case Calendar.JANUARY -> monthName = "Jan";
                    case Calendar.FEBRUARY -> monthName = "Feb";
                    case Calendar.MARCH -> monthName = "Mar";
                    case Calendar.APRIL -> monthName = "Apr";
                    case Calendar.MAY -> monthName = "May";
                    case Calendar.JUNE -> monthName = "Jun";
                    case Calendar.JULY -> monthName = "Jul";
                    case Calendar.AUGUST -> monthName = "Aug";
                    case Calendar.SEPTEMBER -> monthName = "Sep";
                    case Calendar.OCTOBER -> monthName = "Oct";
                    case Calendar.NOVEMBER -> monthName = "Nov";
                    case Calendar.DECEMBER -> monthName = "Dec";
                }

                int dayOfMonth = c.get(Calendar.DAY_OF_MONTH);
                String suffix = numberSuffix(dayOfMonth);
                int year = c.get(Calendar.YEAR);

                return year + ", " + dow + ", " + monthName + " " + suffix + " at " + h + ":" + fmin(c) + " " + (c.get(Calendar.AM_PM) == Calendar.PM ? "PM" : "AM");
            }
        }
    }

    /**
     * Get the suffix for a number i.e. 1st 2nd 3rd
     *
     * @param i the number
     * @return the suffix
     */
    public static String numberSuffix(int i) {
        String[] sufixes = new String[]{"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th"};
        return switch (i % 100) {
            case 11, 12, 13 -> i + "th";
            default -> i + sufixes[i % 10];
        };
    }

    /**
     * Get a high accuracy but limited range duration (accurate up to a couple
     * minutes)
     *
     * @param ms   the milliseconds (double)
     * @param prec the precision (decimal format)
     * @return the formatted string
     */
    public static String duration(double ms, int prec) {
        if (ms < 1000.0) {
            return Form.f(ms, prec) + "ms";
        }

        if (ms / 1000.0 < 60.0) {
            return Form.f(ms / 1000.0, prec) + "s";
        }

        if (ms / 1000.0 / 60.0 < 60.0) {
            return Form.f(ms / 1000.0 / 60.0, prec) + "m";
        }

        if (ms / 1000.0 / 60.0 / 60.0 < 24.0) {
            return Form.f(ms / 1000.0 / 60.0 / 60.0, prec) + " hours";
        }

        if (ms / 1000.0 / 60.0 / 60.0 / 24.0 < 7) {
            return Form.f(ms / 1000.0 / 60.0 / 24.0, prec) + " days";
        }

        return Form.f(ms, prec) + "ms";
    }

    public static String duration(long ms) {
        return duration(ms, 0);
    }

    /**
     * Get a duration from milliseconds up to days
     *
     * @param ms   the ms
     * @param prec the precision (decimal format)
     * @return the formatted string
     */
    public static String duration(long ms, int prec) {
        if (ms < 1000.0) {
            return Form.f(ms, prec) + "ms";
        }

        if (ms / 1000.0 < 60.0) {
            return Form.f(ms / 1000.0, prec) + " seconds";
        }

        if (ms / 1000.0 / 60.0 < 60.0) {
            return Form.f(ms / 1000.0 / 60.0, prec) + " minutes";
        }

        if (ms / 1000.0 / 60.0 / 60.0 < 24.0) {
            return Form.f(ms / 1000.0 / 60.0 / 60.0, prec) + " hours";
        }

        if (ms / 1000.0 / 60.0 / 60.0 / 24.0 < 7) {
            return Form.f(ms / 1000.0 / 60.0 / 60.0 / 24.0, prec) + " days";
        }

        return Form.f(ms, prec) + "ms";
    }

    /**
     * Format a big value
     *
     * @param i the number
     * @return the full value in string
     */
    public static String b(int i) {
        return b(new BigInteger(String.valueOf(i)));
    }

    /**
     * Format a big value
     *
     * @param i the number
     * @return the full value in string
     */
    public static String b(long i) {
        return b(new BigInteger(String.valueOf(i)));
    }

    /**
     * Format a big value
     *
     * @param i the number
     * @return the full value in string
     */
    public static String b(double i) {
        return b(new BigInteger(String.valueOf((long) i)));
    }

    /**
     * Format a big number
     *
     * @param number the big number
     * @return the value in string
     */
    public static String b(BigInteger number) {
        Entry<BigInteger, String> entry = MAP.floorEntry(number);
        if (entry == null) {
            return "Nearly nothing";
        }

        BigInteger key = entry.getKey();
        BigInteger d = key.divide(THOUSAND);
        BigInteger m = number.divide(d);
        float f = m.floatValue() / 1000.0f;
        float rounded = ((int) (f * 100.0)) / 100.0f;

        if (rounded % 1 == 0) {
            return ((int) rounded) + " " + entry.getValue();
        }

        return rounded + " " + entry.getValue();
    }

    /**
     * Calculate a fancy string representation of a file size. Adds a suffix of B,
     * KB, MB, GB, or TB
     *
     * @param s the size (in bytes)
     * @return the string
     */
    public static String fileSize(long s) {
        return ofSize(s, 1000);
    }

    /**
     * ":", "a", "b", "c" -> a:b:c
     *
     * @param splitter the splitter that goes in between
     * @param strings  the strings
     * @return the result
     */
    public static String split(String splitter, String... strings) {
        StringBuilder b = new StringBuilder();

        for (String i : strings) {
            b.append(splitter);
            b.append(i);
        }

        return b.substring(splitter.length());
    }

    /**
     * Calculate a fancy string representation of a file size. Adds a suffix of B,
     * KB, MB, GB, or TB
     *
     * @param s the size (in bytes)
     * @return the string
     */
    public static String memSize(long s) {
        return ofSize(s, 1024);
    }

    public static String memSize(long s, int dec) {
        return ofSize(s, 1024, dec);
    }

    /**
     * Get the timestamp of the time t (ms since 1970)
     *
     * @param t the time
     * @return the stamp
     */
    @SuppressWarnings("deprecation")
    public static String stamp(long t) {
        Date d = new Date(t);
        return d.getMonth() + "-" + d.getDate() + "-" + (d.getYear() + 1900) + " " + d.getHours() + "h " + d.getMinutes() + "m " + d.getSeconds() + "s ";
    }

    @SuppressWarnings("deprecation")
    public static String stampTime(long t) {
        Date d = new Date(t);

        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + ":" + forceDoubleDigit(d.getMinutes()) + ":" + forceDoubleDigit(d.getSeconds());
    }

    public static String forceDoubleDigit(int dig) {
        if (dig < 10) {
            return "0" + dig;
        }

        return dig + "";
    }

    @SuppressWarnings("deprecation")
    public static String stampDay(long t) {
        Date d = new Date(t);
        return d.getMonth() + "-" + d.getDate() + "-" + (d.getYear() + 1900);
    }

    /**
     * Calculate a fancy string representation of a size in B, KB, MB, GB, or TB
     * with a special divisor. The divisor decides how much goes up in the suffix
     * chain.
     *
     * @param s   the size (in bytes)
     * @param div the divisor
     * @return the string
     */
    public static String ofSize(long s, int div) {
        double d = (double) s;
        String sub = "Bytes";

        if (d > div - 1) {
            d /= div;
            sub = "KB";

            if (d > div - 1) {
                d /= div;
                sub = "MB";

                if (d > div - 1) {
                    d /= div;
                    sub = "GB";

                    if (d > div - 1) {
                        d /= div;
                        sub = "TB";
                    }
                }
            }
        }

        if (sub.equals("GB") || sub.equals("TB")) {
            return Form.f(d, 1) + sub;
        } else {
            return Form.f(d, 0) + sub;
        }
    }

    /**
     * Calculate a fancy string representation of a size in B, KB, MB, GB, or TB
     * with a special divisor. The divisor decides how much goes up in the suffix
     * chain.
     *
     * @param s   the size (in bytes)
     * @param div the divisor
     * @param dec the decimal places
     * @return the string
     */
    public static String ofSize(long s, int div, int dec) {
        double d = (double) s;
        String sub = "Bytes";

        if (d > div - 1) {
            d /= div;
            sub = "KB";

            if (d > div - 1) {
                d /= div;
                sub = "MB";

                if (d > div - 1) {
                    d /= div;
                    sub = "GB";

                    if (d > div - 1) {
                        d /= div;
                        sub = "TB";
                    }
                }
            }
        }

        return Form.f(d, dec) + " " + sub;
    }

    /**
     * Calculate a fancy string representation of a size in Grams, KG, MG, GG, TG
     * with a special divisor. The divisor decides how much goes up in the suffix
     * chain.
     *
     * @param s   the size (in bytes)
     * @param div the divisor
     * @param dec the decimal places
     * @return the string
     */
    public static String ofSizeMetricWeight(long s, int div, int dec) {
        boolean neg = s < 0;
        if (neg) {
            s = -s;
        }
        double d = (double) s;
        String sub = "Grams";

        if (d > div - 1) {
            d /= div;
            sub = "KG";

            if (d > div - 1) {
                d /= div;
                sub = "MG";

                if (d > div - 1) {
                    d /= div;
                    sub = "GG";

                    if (d > div - 1) {
                        d /= div;
                        sub = "TG";
                    }
                }
            }
        }

        return (neg ? "-" : "") + Form.f(d, dec) + " " + sub;
    }

    /**
     * Trim a string to a length, then append ... at the end if it extends the limit
     *
     * @param s the string
     * @param l the limit
     * @return the modified string
     */
    public static String trim(String s, int l) {
        if (s.length() <= l) {
            return s;
        }

        return s.substring(0, l) + "...";
    }

    /**
     * Get a class name into a configuration/filename key For example,
     * PhantomController.class is converted to phantom-controller
     *
     * @param clazz the class
     * @return the string representation
     */
    public static String cname(String clazz) {
        StringBuilder codeName = new StringBuilder();

        for (Character i : clazz.toCharArray()) {
            if (Character.isUpperCase(i)) {
                codeName.append("-").append(Character.toLowerCase(i));
            } else {
                codeName.append(i);
            }
        }

        if (codeName.toString().startsWith("-")) {
            codeName = new StringBuilder(codeName.substring(1));
        }

        return codeName.toString();
    }

    /**
     * Get a formatted representation of the memory given in megabytes
     *
     * @param mb the megabytes
     * @return the string representation with suffixes
     */
    public static String mem(long mb) {
        if (mb < 1024) {
            return f(mb) + " MB";
        } else {
            return f(((double) mb / (double) 1024), 1) + " GB";
        }
    }

    /**
     * Get a formatted representation of the memory given in kilobytes
     *
     * @return the string representation with suffixes
     */
    public static String memx(long kb) {
        if (kb < 1024) {
            return fd(kb, 2) + " KB";
        } else {
            double mb = (double) kb / 1024.0;

            if (mb < 1024) {
                return fd(mb, 2) + " MB";
            } else {
                double gb = mb / 1024.0;

                return fd(gb, 2) + " GB";
            }
        }
    }

    /**
     * Format a long. Changes -10334 into -10,334
     *
     * @param i the number
     * @return the string representation of the number
     */
    public static String f(long i) {
        instantiate();
        return NF.format(i);
    }

    /**
     * Format a number. Changes -10334 into -10,334
     *
     * @param i the number
     * @return the string representation of the number
     */
    public static String f(int i) {
        instantiate();
        return NF.format(i);
    }

    /**
     * Formats a double's decimals to a limit
     *
     * @param i the double
     * @param p the number of decimal places to use
     * @return the formated string
     */
    public static String f(double i, int p) {
        String form = "#";

        if (p > 0) {
            form = form + "." + repeat("#", p);
        }

        DF = new DecimalFormat(form);

        return DF.format(i).replaceAll("\\Q,\\E", ".");
    }

    /**
     * Formats a double's decimals to a limit, however, this will add zeros to the
     * decimal places that dont need to be placed down. 2.4343 formatted with 6
     * decimals gets returned as 2.434300
     *
     * @param i the double
     * @param p the number of decimal places to use
     * @return the formated string
     */
    public static String fd(double i, int p) {
        String form = "0";

        if (p > 0) {
            form = form + "." + repeat("0", p);
        }

        DF = new DecimalFormat(form);

        return DF.format(i);
    }

    /**
     * Formats a float's decimals to a limit
     *
     * @param i the float
     * @param p the number of decimal places to use
     * @return the formated string
     */
    public static String f(float i, int p) {
        String form = "#";

        if (p > 0) {
            form = form + "." + repeat("#", p);
        }

        DF = new DecimalFormat(form);

        return DF.format(i);
    }

    /**
     * Formats a double's decimals (one decimal point)
     *
     * @param i the double
     */
    public static String f(double i) {
        return f(i, 1);
    }

    /**
     * Formats a float's decimals (one decimal point)
     *
     * @param i the float
     */
    public static String f(float i) {
        return f(i, 1);
    }

    /**
     * Get a percent representation of a double and decimal places (0.53) would
     * return 53%
     *
     * @param i the double
     * @param p the number of decimal points
     * @return a string
     */
    public static String pc(double i, int p) {
        return f(i * 100.0, p) + "%";
    }

    /**
     * Get a percent representation of a float and decimal places (0.53) would
     * return 53%
     *
     * @param i the float
     * @param p the number of decimal points
     * @return a string
     */
    public static String pc(float i, int p) {
        return f(i * 100, p) + "%";
    }

    /**
     * Get a percent representation of a double and zero decimal places (0.53) would
     * return 53%
     *
     * @param i the double
     * @return a string
     */
    public static String pc(double i) {
        return f(i * 100, 0) + "%";
    }

    /**
     * Get a percent representation of a float and zero decimal places (0.53) would
     * return 53%
     *
     * @param i the double
     * @return a string
     */
    public static String pc(float i) {
        return f(i * 100, 0) + "%";
    }

    /**
     * Get a percent as the percent of i out of "of" with custom decimal places
     *
     * @param i  the percent out of
     * @param of of of
     * @param p  the decimal places
     * @return the string
     */
    public static String pc(int i, int of, int p) {
        return f(100.0 * (((double) i) / ((double) of)), p) + "%";
    }

    /**
     * Get a percent as the percent of i out of "of"
     *
     * @param i  the percent out of
     * @param of of of
     * @return the string
     */
    public static String pc(int i, int of) {
        return pc(i, of, 0);
    }

    /**
     * Get a percent as the percent of i out of "of" with custom decimal places
     *
     * @param i  the percent out of
     * @param of of of
     * @param p  the decimal places
     * @return the string
     */
    public static String pc(long i, long of, int p) {
        return f(100.0 * (((double) i) / ((double) of)), p) + "%";
    }

    /**
     * Get a percent as the percent of i out of "of"
     *
     * @param i  the percent out of
     * @param of of of
     * @return the string
     */
    public static String pc(long i, long of) {
        return pc(i, of, 0);
    }

    /**
     * Milliseconds to seconds (double)
     *
     * @param ms the milliseconds
     * @return a formatted string to milliseconds
     */
    public static String msSeconds(long ms) {
        return f((double) ms / 1000.0);
    }

    /**
     * Milliseconds to seconds (double) custom decimals
     *
     * @param ms the milliseconds
     * @param p  number of decimal points
     * @return a formatted string to milliseconds
     */
    public static String msSeconds(long ms, int p) {
        return f((double) ms / 1000.0, p);
    }

    /**
     * nanoseconds to seconds (double)
     *
     * @return a formatted string to nanoseconds
     */
    public static String nsMs(long ns) {
        return f((double) ns / 1000000.0);
    }

    /**
     * nanoseconds to seconds (double) custom decimals
     *
     * @param p number of decimal points
     * @return a formatted string to nanoseconds
     */
    public static String nsMs(long ns, int p) {
        return f((double) ns / 1000000.0, p);
    }

    /**
     * nanoseconds to seconds (double) custom decimals
     *
     * @param p number of decimal points
     * @return a formatted string to nanoseconds
     */
    public static String nsMsd(long ns, int p) {
        return fd((double) ns / 1000000.0, p);
    }

    /**
     * Get roman numeral representation of the int
     *
     * @param num the int
     * @return the numerals
     */
    public static String toRoman(int num) {
        LinkedHashMap<String, Integer> roman_numerals = new LinkedHashMap<>();

        roman_numerals.put("M", 1000);
        roman_numerals.put("CM", 900);
        roman_numerals.put("D", 500);
        roman_numerals.put("CD", 400);
        roman_numerals.put("C", 100);
        roman_numerals.put("XC", 90);
        roman_numerals.put("L", 50);
        roman_numerals.put("XL", 40);
        roman_numerals.put("X", 10);
        roman_numerals.put("IX", 9);
        roman_numerals.put("V", 5);
        roman_numerals.put("IV", 4);
        roman_numerals.put("I", 1);

        StringBuilder res = new StringBuilder();

        for (Map.Entry<String, Integer> entry : roman_numerals.entrySet()) {
            int matches = num / entry.getValue();

            res.append(repeat(entry.getKey(), matches));
            num = num % entry.getValue();
        }

        return res.toString();
    }

    /**
     * Get the number representation from roman numerals.
     *
     * @param number the roman number
     * @return the int representation
     */
    public static int fromRoman(String number) {
        if (number.isEmpty()) {
            return 0;
        }

        number = number.toUpperCase();

        if (number.startsWith("M")) {
            return 1000 + fromRoman(number.substring(1));
        }

        if (number.startsWith("CM")) {
            return 900 + fromRoman(number.substring(2));
        }

        if (number.startsWith("D")) {
            return 500 + fromRoman(number.substring(1));
        }

        if (number.startsWith("CD")) {
            return 400 + fromRoman(number.substring(2));
        }

        if (number.startsWith("C")) {
            return 100 + fromRoman(number.substring(1));
        }

        if (number.startsWith("XC")) {
            return 90 + fromRoman(number.substring(2));
        }

        if (number.startsWith("L")) {
            return 50 + fromRoman(number.substring(1));
        }

        if (number.startsWith("XL")) {
            return 40 + fromRoman(number.substring(2));
        }

        if (number.startsWith("X")) {
            return 10 + fromRoman(number.substring(1));
        }

        if (number.startsWith("IX")) {
            return 9 + fromRoman(number.substring(2));
        }

        if (number.startsWith("V")) {
            return 5 + fromRoman(number.substring(1));
        }

        if (number.startsWith("IV")) {
            return 4 + fromRoman(number.substring(2));
        }

        if (number.startsWith("I")) {
            return 1 + fromRoman(number.substring(1));
        }

        return 0;
    }

    /**
     * Repeat a string
     *
     * @param s the string
     * @param n the amount of times to repeat
     * @return the repeated string
     */
    @SuppressWarnings("StringRepeatCanBeUsed")
    public static String repeat(String s, int n) {
        if (s == null) {
            return null;
        }

        final StringBuilder sb = new StringBuilder();

        for (int i = 0; i < n; i++) {
            sb.append(s);
        }

        return sb.toString();
    }
}
