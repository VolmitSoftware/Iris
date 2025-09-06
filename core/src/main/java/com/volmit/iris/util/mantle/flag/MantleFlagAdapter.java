package com.volmit.iris.util.mantle.flag;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class MantleFlagAdapter extends TypeAdapter<MantleFlag> {
    private static final String CUSTOM = "CUSTOM:";
    private static final int CUSTOM_LENGTH = CUSTOM.length();

    @Override
    public void write(JsonWriter out, MantleFlag value) throws IOException {
        if (value == null) out.nullValue();
        else out.value(value.toString());
    }

    @Override
    public MantleFlag read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String s = in.nextString();
        if (s.startsWith(CUSTOM) && s.length() > CUSTOM_LENGTH)
            return MantleFlag.of(Integer.parseInt(s.substring(CUSTOM_LENGTH)));
        return ReservedFlag.valueOf(s);
    }
}
