package art.arcane.iris.generation.terrain;

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
@Description("A region in the dimension's region list")
@Data
@JsonAdapter(IrisRegionEntry.Adapter.class)
public class IrisRegionEntry implements WeightedReference {
    @Required
    @RegistryListResource(IrisRegion.class)
    @Description("The region (file name without .json)")
    private String region;
    @MinNumber(Weighted.MIN_WEIGHT)
    @MaxNumber(Weighted.MAX_WEIGHT)
    @Description("A weight of two is just as if this entry were listed twice in a row, making the region more common. The noise picks along the list in the order it is written.")
    private int weight = 1;
    private transient boolean writtenAsObject;

    public IrisRegionEntry(String region, int weight, boolean writtenAsObject) {
        this.region = region;
        this.weight = weight;
        this.writtenAsObject = writtenAsObject;
    }

    public static IrisRegionEntry plain(String region) {
        return new IrisRegionEntry(region, Weighted.MIN_WEIGHT, false);
    }

    public static KList<IrisRegionEntry> plainList(String... regions) {
        KList<IrisRegionEntry> entries = new KList<>();
        for (String region : regions) {
            entries.add(plain(region));
        }
        return entries;
    }

    @Override
    public String getReferenceKey() {
        return region;
    }

    public static final class Adapter extends WeightedReferenceAdapter<IrisRegionEntry> {
        public Adapter() {
            super("region");
        }

        @Override
        protected IrisRegionEntry create(String key, int weight, boolean writtenAsObject) {
            return new IrisRegionEntry(key, weight, writtenAsObject);
        }
    }
}
