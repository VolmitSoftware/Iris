package art.arcane.iris.generation.biome;

import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.value.Weighted;
import art.arcane.iris.pack.value.WeightedReference;
import art.arcane.iris.pack.value.WeightedReferenceAdapter;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.documentation.Description;
import com.google.gson.annotations.JsonAdapter;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("A biome in a list a noise picks from")
@Data
@JsonAdapter(IrisBiomeEntry.Adapter.class)
public class IrisBiomeEntry implements WeightedReference {
    @Required
    @RegistryListResource(IrisBiome.class)
    @Description("The biome (file name without .json)")
    private String biome;
    @MinNumber(Weighted.MIN_WEIGHT)
    @MaxNumber(Weighted.MAX_WEIGHT)
    @Description("A weight of two is just as if this entry were listed twice in a row, making the biome more common. The noise picks along the list in the order it is written.")
    private int weight = 1;
    private transient boolean writtenAsObject;

    public IrisBiomeEntry(String biome, int weight, boolean writtenAsObject) {
        this.biome = biome;
        this.weight = weight;
        this.writtenAsObject = writtenAsObject;
    }

    public static IrisBiomeEntry plain(String biome) {
        return new IrisBiomeEntry(biome, Weighted.MIN_WEIGHT, false);
    }

    public static KList<IrisBiomeEntry> plainList(String... biomes) {
        KList<IrisBiomeEntry> entries = new KList<>();
        for (String biome : biomes) {
            entries.add(plain(biome));
        }
        return entries;
    }

    @Override
    public String getReferenceKey() {
        return biome;
    }

    public static final class Adapter extends WeightedReferenceAdapter<IrisBiomeEntry> {
        public Adapter() {
            super("biome");
        }

        @Override
        protected IrisBiomeEntry create(String key, int weight, boolean writtenAsObject) {
            return new IrisBiomeEntry(key, weight, writtenAsObject);
        }
    }
}
