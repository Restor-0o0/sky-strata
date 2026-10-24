package io.github.restor0o0.skystrata.client;

import io.github.restor0o0.skystrata.Layer;
import io.github.restor0o0.skystrata.LayerPresets;
import io.github.restor0o0.skystrata.Strata;
import io.github.restor0o0.skystrata.StrataPreset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class StrataScreen extends Screen {
    private static final int MIN_HEIGHT = 384;
    private static final int LIST_WIDTH = 150, EDITOR_WIDTH = 200, GAP = 10, LIGHT_WIDTH = 160;

    private final CreateWorldScreen parent;
    private final int maxHeight;
    final List<Layer> layers;
    private final List<AbstractWidget> editor = new ArrayList<>();
    private int selected;
    private boolean islandLight;
    private LayerList list;
    private Button preset, add, remove, up, down, done;
    private int editorX, editorY;

    public StrataScreen(CreateWorldScreen parent, WorldCreationContext context) {
        super(Component.translatable("skystrata.screen.title"));
        this.parent = parent;
        this.maxHeight = StrataPreset.maxHeight(context.worldgenLoadContext());
        Strata current = Strata.of(context.selectedDimensions().overworld());
        this.layers = new ArrayList<>((current != null ? current : Strata.DEFAULT).layers());
        this.islandLight = StrataPreset.islandLight(context.selectedDimensions());
    }

    @Override
    protected void init() {
        int left = width / 2 - (LIST_WIDTH + GAP + EDITOR_WIDTH) / 2;
        int top = 50, bottom = height - 52;
        editorX = left + LIST_WIDTH + GAP;
        editorY = top;

        int presetWidth = LIST_WIDTH + GAP + EDITOR_WIDTH - LIGHT_WIDTH - 5;
        preset = addRenderableWidget(Button.builder(Component.empty(), b -> nextPreset()).bounds(left, 24, presetWidth, 20).build());
        addRenderableWidget(CycleButton.onOffBuilder(islandLight)
                .withTooltip(v -> Tooltip.create(Component.translatable("skystrata.island_light.tooltip")))
                .create(left + presetWidth + 5, 24, LIGHT_WIDTH, 20, Component.translatable("skystrata.island_light"), (b, v) -> islandLight = v));
        list = addRenderableWidget(new LayerList(this, minecraft, left, top, LIST_WIDTH, bottom - top - 24));

        int y = bottom - 20;
        add = addRenderableWidget(small("+", "skystrata.button.add", left, y, b -> {
            layers.add(Layer.Kind.ISLANDS.defaults());
            refresh(layers.size() - 1);
        }));
        remove = addRenderableWidget(small("−", "skystrata.button.remove", left + 38, y, b -> {
            layers.remove(selected);
            refresh(Math.min(selected, layers.size() - 1));
        }));
        up = addRenderableWidget(small("↑", "skystrata.button.up", left + 76, y, b -> move(-1)));
        down = addRenderableWidget(small("↓", "skystrata.button.down", left + 114, y, b -> move(1)));

        int x = width / 2;
        done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> apply()).bounds(x - 155, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(x + 5, height - 28, 150, 20).build());
        refresh(layers.isEmpty() ? -1 : Math.min(Math.max(selected, 0), layers.size() - 1));
    }

    private static Button small(String label, String tooltip, int x, int y, Button.OnPress onPress) {
        return Button.builder(Component.literal(label), onPress).bounds(x, y, 34, 20)
                .tooltip(Tooltip.create(Component.translatable(tooltip))).build();
    }

    private void nextPreset() {
        int next = (LayerPresets.indexOf(layers) + 1) % LayerPresets.ALL.size();
        layers.clear();
        layers.addAll(LayerPresets.ALL.get(next).strata().layers());
        refresh(0);
    }

    private void move(int dir) {
        Collections.swap(layers, selected, selected + dir);
        refresh(selected + dir);
    }

    private void refresh(int select) {
        selected = select;
        list.rebuild(select);
        buildEditor();
        update();
    }

    void select(int index) {
        selected = index;
        buildEditor();
        update();
    }

    private void set(Layer layer, boolean rebuildEditor) {
        layers.set(selected, layer);
        list.rebuild(selected);
        if (rebuildEditor) buildEditor();
        update();
    }

    private void buildEditor() {
        editor.forEach(this::removeWidget);
        editor.clear();
        if (selected < 0 || selected >= layers.size()) return;
        Layer l = layers.get(selected);
        CycleButton<Layer.Kind> kind = CycleButton.builder((Layer.Kind k) -> Component.translatable("skystrata.kind." + k.getSerializedName()), l.kind())
                .withValues(Layer.Kind.values())
                .create(0, 0, EDITOR_WIDTH, 20, Component.translatable("skystrata.layer.kind"),
                        (b, k) -> set(k.defaults().withGap(layer().gap()), true));
        kind.setTooltip(Tooltip.create(Component.translatable("skystrata.layer.kind.tooltip")));
        editor.add(kind);
        if (l.kind() == Layer.Kind.CONTINENTS) {
            CycleButton<Layer.Landing> landing = CycleButton.builder((Layer.Landing v) -> Component.translatable("skystrata.landing." + v.getSerializedName()), l.landing())
                    .withValues(Layer.Landing.values())
                    .create(0, 0, EDITOR_WIDTH, 20, Component.translatable("skystrata.layer.landing"),
                            (b, v) -> set(layer().withLanding(v), true));
            landing.setTooltip(Tooltip.create(Component.translatable("skystrata.layer.landing.tooltip")));
            editor.add(landing);
        }
        editor.add(new ValueSlider(EDITOR_WIDTH, "skystrata.layer.gap", Layer.MIN_GAP, Layer.MAX_GAP, 1, l.gap(), String::valueOf,
                v -> set(layer().withGap(v), false)));
        editor.add(new ValueSlider(EDITOR_WIDTH, "skystrata.layer.thickness", Layer.MIN_THICKNESS, Layer.MAX_THICKNESS, 1, l.thickness(),
                String::valueOf, v -> set(layer().withThickness(v), false)));
        editor.add(new ValueSlider(EDITOR_WIDTH, "skystrata.layer.density", 0, 100, 1, Math.round(l.density() * 100), v -> v + "%",
                v -> set(layer().withDensity(v / 100f), false)));
        editor.add(new ValueSlider(EDITOR_WIDTH, "skystrata.layer.size", Math.round(Layer.MIN_SIZE * 100), Math.round(Layer.MAX_SIZE * 100), 5,
                Math.round(l.size() * 100), v -> v + "%", v -> set(layer().withSize(v / 100f), false)));
        if (l.kind() == Layer.Kind.CONTINENTS && l.landing() != Layer.Landing.FLOATING) {
            editor.add(new ValueSlider(EDITOR_WIDTH, "skystrata.layer.taper", 0, 100, 1, Math.round(l.taper() * 100), v -> v + "%",
                    v -> set(layer().withTaper(v / 100f), false)));
        }
        for (int i = 0; i < editor.size(); i++) {
            editor.get(i).setPosition(editorX, editorY + i * 22);
            addRenderableWidget(editor.get(i));
        }
    }

    private Layer layer() {
        return layers.get(selected);
    }

    private void update() {
        boolean has = selected >= 0 && selected < layers.size();
        add.active = layers.size() < Strata.MAX_LAYERS;
        remove.active = has;
        up.active = has && selected > 0;
        down.active = has && selected < layers.size() - 1;
        done.active = fits();
        int p = LayerPresets.indexOf(layers);
        Component name = Component.translatable(p < 0 ? "skystrata.preset.custom" : "skystrata.preset." + LayerPresets.ALL.get(p).name());
        preset.setMessage(Component.translatable("skystrata.button.preset", name));
        preset.setTooltip(p < 0 ? null : Tooltip.create(Component.translatable("skystrata.preset." + LayerPresets.ALL.get(p).name() + ".tooltip")));
    }

    private Strata strata() {
        return new Strata(layers);
    }

    private int worldHeight() {
        return Math.max(MIN_HEIGHT, strata().requiredHeight());
    }

    private boolean fits() {
        return worldHeight() <= maxHeight;
    }

    private void apply() {
        Strata strata = strata();
        parent.getUiState().updateDimensions((access, dims) -> StrataPreset.apply(access, dims, strata, islandLight));
        minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        if (layers.isEmpty()) {
            graphics.centeredText(font, Component.translatable("skystrata.info.empty"), editorX + EDITOR_WIDTH / 2, editorY + 6, 0xFFA0A0A0);
        }
        Component info;
        if (fits()) {
            String tops = strata().placed().stream().map(p -> String.valueOf(p.top())).collect(Collectors.joining(" / "));
            info = Component.translatable("skystrata.info.height", worldHeight(), tops.isEmpty() ? "-" : tops);
        } else {
            info = Component.translatable("skystrata.info.too_tall", maxHeight + Strata.MIN_Y - 1);
        }
        graphics.centeredText(font, info, width / 2, height - 44, fits() ? 0xFFA0A0A0 : 0xFFFF5555);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
