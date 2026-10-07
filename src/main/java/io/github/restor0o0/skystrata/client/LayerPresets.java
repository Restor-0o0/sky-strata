package io.github.restor0o0.skystrata.client;

import io.github.restor0o0.skystrata.Layer;
import io.github.restor0o0.skystrata.Layer.Kind;
import io.github.restor0o0.skystrata.Strata;
import java.util.List;

record LayerPresets(String name, Strata strata) {
    static final List<LayerPresets> ALL = List.of(
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

    private static Strata of(Layer... layers) {
        return new Strata(List.of(layers));
    }

    static int indexOf(List<Layer> layers) {
        for (int i = 0; i < ALL.size(); i++) if (ALL.get(i).strata().layers().equals(layers)) return i;
        return -1;
    }
}
