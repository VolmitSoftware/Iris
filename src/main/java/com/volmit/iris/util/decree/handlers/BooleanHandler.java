package com.volmit.iris.util.decree.handlers;

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.decree.DecreeParameterHandler;
import com.volmit.iris.util.decree.exceptions.DecreeParsingException;
import com.volmit.iris.util.decree.exceptions.DecreeWhichException;

public class BooleanHandler implements DecreeParameterHandler<Boolean> {
    private static final KList<String> trues = new KList<>(
            "true",
            "yes",
            "t",
            "1"
    );
    private static final KList<String> falses = new KList<>(
            "false",
            "no",
            "f",
            "0"
    );

    /**
     * Should return the possible values for this type
     *
     * @return Possibilities for this type.
     */
    @Override
    public KList<Boolean> getPossibilities() {
        return null;
    }

    /**
     * Converting the type back to a string (inverse of the {@link #parse(String) parse} method)
     *
     * @param aBoolean The input of the designated type to convert to a String
     * @return The resulting string
     */
    @Override
    public String toString(Boolean aBoolean) {
        return aBoolean.toString();
    }

    /**
     * Should parse a String into the designated type
     *
     * @param in The string to parse
     * @return The value extracted from the string, of the designated type
     * @throws DecreeParsingException Thrown when the parsing fails (ex: "oop" translated to an integer throws this)
     * @throws DecreeWhichException   Thrown when multiple results are possible
     */
    @Override
    public Boolean parse(String in) throws DecreeParsingException, DecreeWhichException {
        if (trues.contains(in)){
            return true;
        }
        if (falses.contains(in)){
            return false;
        }
        throw new DecreeParsingException("Cannot convert \"" + in + "\" to a boolean (" + trues.toString(", ") + " / " + falses.toString(", ") + ")");
    }

    /**
     * Returns whether a certain type is supported by this handler<br>
     *
     * @param type The type to check
     * @return True if supported, false if not
     */
    @Override
    public boolean supports(Class<?> type) {
        return type.equals(boolean.class) || type.equals(Boolean.class);
    }
}
