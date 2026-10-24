package art.arcane.iris.core.modules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * IrisApi's decoding contract (IrisApi DESIGN.md, "Decoding contract") for the module settings Gson: a member written as
 * {@code null} counts as left out, so the field keeps its initialiser, and an enum name the enum does not know is an
 * error naming the known ones. Together the host never writes {@code null} into a field it reads.
 */
final class DecodingContract {
    private static final List<String> LIBRARY_PACKAGES = List.of("java.", "javax.", "com.google.gson.");

    private DecodingContract() {
    }

    /** {@code builder} with the contract; register it before any factory that should see the JSON as written. */
    static GsonBuilder appliedTo(GsonBuilder builder) {
        return builder.registerTypeAdapterFactory(new NullMembersLeftOut())
                .registerTypeAdapterFactory(new KnownEnumNames());
    }

    private static boolean readsFields(Class<?> type) {
        if (type.isPrimitive() || type.isArray() || type.isEnum() || type.isInterface()) {
            return false;
        }
        String name = type.getName();
        return LIBRARY_PACKAGES.stream().noneMatch(name::startsWith);
    }

    private static final class NullMembersLeftOut implements TypeAdapterFactory {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (!readsFields(type.getRawType())) {
                return null;
            }
            TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
            TypeAdapter<JsonElement> trees = gson.getAdapter(JsonElement.class);
            return new TypeAdapter<T>() {
                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    delegate.write(out, value);
                }

                @Override
                public T read(JsonReader in) throws IOException {
                    JsonElement json = trees.read(in);
                    if (json.isJsonObject()) {
                        json.getAsJsonObject().entrySet().removeIf(member -> member.getValue().isJsonNull());
                    }
                    return delegate.fromJsonTree(json);
                }
            };
        }
    }

    private static final class KnownEnumNames implements TypeAdapterFactory {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            Class<? super T> raw = type.getRawType();
            if (!raw.isEnum()) {
                return null;
            }
            TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
            return new TypeAdapter<T>() {
                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    delegate.write(out, value);
                }

                @Override
                public T read(JsonReader in) throws IOException {
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        return null;
                    }
                    String name = in.nextString();
                    T value = delegate.fromJsonTree(new JsonPrimitive(name));
                    if (value == null) {
                        throw new JsonParseException("\"" + name + "\" is not one of " + knownNames(raw, delegate));
                    }
                    return value;
                }
            };
        }

        @SuppressWarnings("unchecked")
        private static <T> List<String> knownNames(Class<? super T> type, TypeAdapter<T> delegate) {
            return Arrays.stream(type.getEnumConstants())
                    .map(constant -> delegate.toJsonTree((T) constant).getAsString())
                    .toList();
        }
    }
}
