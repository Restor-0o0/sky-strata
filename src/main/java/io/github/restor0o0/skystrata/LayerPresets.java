package io.github.restor0o0.skystrata;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.github.restor0o0.skystrata.Layer.Kind;
import java.util.List;
import java.util.Optional;

public record LayerPresets(String name, Strata strata) {
    public static final List<LayerPresets> ALL = List.of(
            new LayerPresets("classic", Strata.DEFAULT),
            new LayerPresets("archipelago", of(
                    new Layer(Kind.ISLANDS, 30, 70, 0.5f, 1.5f),
                    new Layer(Kind.ISLANDS, 40, 60, 0.45f, 1f),
                    new Layer(Kind.SHARDS, 20, 77, 0.3f, 1f))),
            new LayerPresets("tower", of(
                    Kind.CONTINENTS.defaults(),
                    Kind.ISLANDS.defaults(),
                    new Layer(Kind.ISLANDS, 40, 70, 0.4f, 0.75f),
                    new Layer(Kind.SHARDS, 30, 77, 0.26f, 1f),
                    new Layer(Kind.SHARDS, 30, 77, 0.2f, 0.5f))),
            new LayerPresets("giants", of(new Layer(Kind.CONTINENTS, 32, 160, 0.6f, 1.5f))),
            new LayerPresets("shattered", of(
                    new Layer(Kind.SHARDS, 40, 77, 0.35f, 1f),
                    new Layer(Kind.SHARDS, 20, 77, 0.3f, 1.5f),
                    new Layer(Kind.SHARDS, 20, 77, 0.25f, 0.75f))));

    public static final Codec<LayerPresets> CODEC = Codec.STRING.comapFlatMap(
            name -> byName(name).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown preset " + name + ", expected one of "
                    + ALL.stream().map(LayerPresets::name).toList())),
            LayerPresets::name);

    private static Strata of(Layer... layers) {
        return new Strata(List.of(layers));
    }

    public static Optional<LayerPresets> byName(String name) {
        return ALL.stream().filter(p -> p.name.equals(name)).findFirst();
    }

    public static int indexOf(List<Layer> layers) {
        for (int i = 0; i < ALL.size(); i++) if (ALL.get(i).strata().layers().equals(layers)) return i;
        return -1;
    }
}
