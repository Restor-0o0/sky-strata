package io.github.restor0o0.skystrata.client;

import io.github.restor0o0.skystrata.Layer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

class LayerList extends ContainerObjectSelectionList<LayerList.Entry> {
    private static final int ROW_WIDTH = 400;

    private final StrataScreen screen;

    LayerList(StrataScreen screen, Minecraft minecraft, int width, int height, int y) {
        super(minecraft, width, height, y, 50);
        this.screen = screen;
    }

    void rebuild() {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < screen.layers.size(); i++) entries.add(new Entry(i));
        replaceEntries(entries);
    }

    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }

    @Override
    protected int getScrollbarPosition() {
        return width / 2 + ROW_WIDTH / 2 + 8;
    }

    class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        private final int index;
        private final CycleButton<Layer.Kind> kind;
        private final ValueSlider gap, thickness, density, size;
        private final Button remove;
        private final List<AbstractWidget> widgets;

        Entry(int index) {
            this.index = index;
            Layer l = layer();
            kind = CycleButton.builder((Layer.Kind k) -> Component.translatable("skystrata.kind." + k.getSerializedName()))
                    .withValues(Layer.Kind.values()).withInitialValue(l.kind()).displayOnlyValue()
                    .create(0, 0, 118, 20, Component.translatable("skystrata.layer.kind"),
                            (b, k) -> screen.set(index, k.defaults().withGap(layer().gap()), true));
            kind.setTooltip(Tooltip.create(Component.translatable("skystrata.layer.kind.tooltip")));
            gap = new ValueSlider(130, "skystrata.layer.gap", Layer.MIN_GAP, Layer.MAX_GAP, 1, l.gap(), String::valueOf,
                    v -> screen.set(index, layer().withGap(v), false));
            thickness = new ValueSlider(128, "skystrata.layer.thickness", Layer.MIN_THICKNESS, Layer.MAX_THICKNESS, 1, l.thickness(),
                    String::valueOf, v -> screen.set(index, layer().withThickness(v), false));
            density = new ValueSlider(186, "skystrata.layer.density", 0, 100, 1, Math.round(l.density() * 100), v -> v + "%",
                    v -> screen.set(index, layer().withDensity(v / 100f), false));
            size = new ValueSlider(170, "skystrata.layer.size", Math.round(Layer.MIN_SIZE * 100), Math.round(Layer.MAX_SIZE * 100), 5,
                    Math.round(l.size() * 100), v -> v + "%", v -> screen.set(index, layer().withSize(v / 100f), false));
            remove = Button.builder(Component.literal("✕"), b -> screen.remove(index)).size(20, 20)
                    .tooltip(Tooltip.create(Component.translatable("skystrata.button.remove"))).build();
            widgets = List.of(kind, gap, thickness, density, size, remove);
        }

        private Layer layer() {
            return screen.layers.get(index);
        }

        @Override
        public void render(GuiGraphics graphics, int idx, int top, int left, int width, int height, int mouseX, int mouseY,
                           boolean hovering, float partialTick) {
            graphics.drawString(minecraft.font, (index + 1) + ".", left, top + 6, 0xFFFFFF);
            place(kind, left + 16, top);
            place(gap, left + 138, top);
            place(thickness, left + 272, top);
            place(density, left + 16, top + 24);
            place(size, left + 206, top + 24);
            place(remove, left + 380, top + 24);
            for (AbstractWidget w : widgets) w.render(graphics, mouseX, mouseY, partialTick);
        }

        private static void place(AbstractWidget w, int x, int y) {
            w.setPosition(x, y);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return widgets;
        }
    }
}
