package io.github.restor0o0.skystrata;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

public record Layer(Kind kind, int gap, int thickness, float density, float size) {
    public static final int MIN_GAP = -64, MAX_GAP = 256, MIN_THICKNESS = 16, MAX_THICKNESS = 320;
    public static final float MIN_SIZE = 0.25f, MAX_SIZE = 4f;

    public static final Codec<Layer> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kind.CODEC.fieldOf("kind").forGetter(Layer::kind),
            Codec.intRange(MIN_GAP, MAX_GAP).fieldOf("gap").forGetter(Layer::gap),
            Codec.intRange(MIN_THICKNESS, MAX_THICKNESS).fieldOf("thickness").forGetter(Layer::thickness),
            Codec.floatRange(0, 1).fieldOf("density").forGetter(Layer::density),
            Codec.floatRange(MIN_SIZE, MAX_SIZE).optionalFieldOf("size", 1f).forGetter(Layer::size)
    ).apply(i, Layer::new));

    public Layer withKind(Kind kind) {
        return new Layer(kind, gap, thickness, density, size);
    }

    public Layer withGap(int gap) {
        return new Layer(kind, gap, thickness, density, size);
    }

    public Layer withThickness(int thickness) {
        return new Layer(kind, gap, thickness, density, size);
    }

    public Layer withDensity(float density) {
        return new Layer(kind, gap, thickness, density, size);
    }

    public Layer withSize(float size) {
        return new Layer(kind, gap, thickness, density, size);
    }

    public enum Kind implements StringRepresentable {
        CONTINENTS("continents"),
        ISLANDS("islands"),
        SHARDS("shards");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public Layer defaults() {
            return switch (this) {
                case CONTINENTS -> new Layer(this, 32, 160, 0.54f, 1f);
                case ISLANDS -> new Layer(this, 50, 70, 0.45f, 1f);
                case SHARDS -> new Layer(this, 10, 77, 0.26f, 1f);
            };
        }
    }
}
