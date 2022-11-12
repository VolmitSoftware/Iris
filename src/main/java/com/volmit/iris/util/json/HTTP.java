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

package com.volmit.iris.util.json;


import java.util.Iterator;

/**
 * Convert an HTTP header to a JSONObject and back.
 *
 * @author JSON.org
 * @version 2014-05-03
 */
public class HTTP {

    /**
     * Carriage return/line feed.
     */
    public static final String CRLF = "\r\n";

    /**
     * Convert an HTTP header string into a JSONObject. It can be a request
     * header or a response header. A request header will contain
     *
     * <pre>
     * {
     *    Method: "POST" (for example),
     *    "Request-URI": "/" (for example),
     *    "HTTP-Version": "HTTP/1.1" (for example)
     * }
     * </pre>
     * <p>
     * A response header will contain
     *
     * <pre>
     * {
     *    "HTTP-Version": "HTTP/1.1" (for example),
     *    "Status-Code": "200" (for example),
     *    "Reason-Phrase": "OK" (for example)
     * }
     * </pre>
     * <p>
     * In addition, the other parameters in the header will be captured, using
     * the HTTP field names as JSON names, so that
     *
     * <pre>
     *    Date: Sun, 26 May 2002 18:06:04 GMT
     *    Cookie: Q=q2=PPEAsg--; B=677gi6ouf29bn&b=2&f=s
     *    Cache-Control: no-cache
     * </pre>
     * <p>
     * become
     *
     * <pre>
     * {...
     *    Date: "Sun, 26 May 2002 18:06:04 GMT",
     *    Cookie: "Q=q2=PPEAsg--; B=677gi6ouf29bn&b=2&f=s",
     *    "Cache-Control": "no-cache",
     * ...}
     * </pre>
     * <p>
     * It does no further checking or conversion. It does not parse dates. It
     * does not do '%' transforms on URLs.
     *
     * @param string An HTTP header string.
     * @return A JSONObject containing the elements and attributes of the XML
     * string.
     */
    public static JSONObject toJSONObject(String string) throws JSONException {
        JSONObject jo = new JSONObject();
        HTTPTokener x = new HTTPTokener(string);
        String token;

        token = x.nextToken();
        if (token.toUpperCase().startsWith("HTTP")) {

            // Response

            jo.put("HTTP-Version", token);
            jo.put("Status-Code", x.nextToken());
            jo.put("Reason-Phrase", x.nextTo('\0'));
            x.next();

        } else {

            // Request

            jo.put("Method", token);
            jo.put("Request-URI", x.nextToken());
            jo.put("HTTP-Version", x.nextToken());
        }

        // Fields

        while (x.more()) {
            String name = x.nextTo(':');
            x.next(':');
            jo.put(name, x.nextTo('\0'));
            x.next();
        }
        return jo;
    }

    /**
     * Convert a JSONObject into an HTTP header. A request header must contain
     *
     * <pre>
     * {
     *    Method: "POST" (for example),
     *    "Request-URI": "/" (for example),
     *    "HTTP-Version": "HTTP/1.1" (for example)
     * }
     * </pre>
     * <p>
     * A response header must contain
     *
     * <pre>
     * {
     *    "HTTP-Version": "HTTP/1.1" (for example),
     *    "Status-Code": "200" (for example),
     *    "Reason-Phrase": "OK" (for example)
     * }
     * </pre>
     * <p>
     * Any other members of the JSONObject will be output as HTTP fields. The
     * result will end with two CRLF pairs.
     *
     * @param jo A JSONObject
     * @return An HTTP header string.
     * @throws JSONException if the object does not contain enough information.
     */
    public static String toString(JSONObject jo) throws JSONException {
        Iterator<String> keys = jo.keys();
        String string;
        StringBuilder sb = new StringBuilder();
        if (jo.has("Status-Code") && jo.has("Reason-Phrase")) {
            sb.append(jo.getString("HTTP-Version"));
            sb.append(' ');
            sb.append(jo.getString("Status-Code"));
            sb.append(' ');
            sb.append(jo.getString("Reason-Phrase"));
        } else if (jo.has("Method") && jo.has("Request-URI")) {
            sb.append(jo.getString("Method"));
            sb.append(' ');
            sb.append('"');
            sb.append(jo.getString("Request-URI"));
            sb.append('"');
            sb.append(' ');
            sb.append(jo.getString("HTTP-Version"));
        } else {
            throw new JSONException("Not enough material for an HTTP header.");
        }
        sb.append(CRLF);
        while (keys.hasNext()) {
            string = keys.next();
            if (!"HTTP-Version".equals(string) && !"Status-Code".equals(string) && !"Reason-Phrase".equals(string) && !"Method".equals(string) && !"Request-URI".equals(string) && !jo.isNull(string)) {
                sb.append(string);
                sb.append(": ");
                sb.append(jo.getString(string));
                sb.append(CRLF);
            }
        }
        sb.append(CRLF);
        return sb.toString();
    }
}
