package io.github.restor0o0.skystrata.client;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

class ValueSlider extends AbstractSliderButton {
    private final String key;
    private final int min, max, step;
    private final IntFunction<String> format;
    private final IntConsumer onChange;

    ValueSlider(int width, String key, int min, int max, int step, int current, IntFunction<String> format, IntConsumer onChange) {
        super(0, 0, width, 20, Component.empty(), Mth.clamp((current - min) / (double) (max - min), 0, 1));
        this.key = key;
        this.min = min;
        this.max = max;
        this.step = step;
        this.format = format;
        this.onChange = onChange;
        setTooltip(Tooltip.create(Component.translatable(key + ".tooltip")));
        updateMessage();
    }

    private int current() {
        return Mth.clamp(min + (int) Math.round(value * (max - min) / step) * step, min, max);
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.translatable(key, format.apply(current())));
    }

    @Override
    protected void applyValue() {
        onChange.accept(current());
    }
}
