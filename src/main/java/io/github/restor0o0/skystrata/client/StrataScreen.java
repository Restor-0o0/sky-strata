package io.github.restor0o0.skystrata.client;

import io.github.restor0o0.skystrata.Layer;
import io.github.restor0o0.skystrata.Strata;
import io.github.restor0o0.skystrata.StrataPreset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class StrataScreen extends Screen {
    private static final int MIN_HEIGHT = 384;

    private final CreateWorldScreen parent;
    private final int maxHeight;
    final List<Layer> layers;
    private LayerList list;
    private Button add;
    private Button done;

    public StrataScreen(CreateWorldScreen parent, WorldCreationContext context) {
        super(Component.translatable("skystrata.screen.title"));
        this.parent = parent;
        this.maxHeight = StrataPreset.maxHeight(context.worldgenLoadContext());
        Strata current = Strata.of(context.selectedDimensions().overworld());
        this.layers = new ArrayList<>((current != null ? current : Strata.DEFAULT).layers());
    }

    @Override
    protected void init() {
        list = addRenderableWidget(new LayerList(this, minecraft, width, height - 112, 32));
        int x = width / 2, y = height - 56;
        add = addRenderableWidget(Button.builder(Component.translatable("skystrata.button.add"), b -> {
            layers.add(Layer.Kind.ISLANDS.defaults());
            refresh();
        }).bounds(x - 155, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("skystrata.button.reset"), b -> {
            layers.clear();
            layers.addAll(Strata.DEFAULT.layers());
            refresh();
        }).bounds(x + 5, y, 150, 20).build());
        done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> apply()).bounds(x - 155, y + 24, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(x + 5, y + 24, 150, 20).build());
        refresh();
    }

    void set(int index, Layer layer, boolean rebuild) {
        layers.set(index, layer);
        if (rebuild) list.rebuild();
        update();
    }

    void remove(int index) {
        layers.remove(index);
        refresh();
    }

    private void refresh() {
        list.rebuild();
        update();
    }

    private void update() {
        add.active = layers.size() < Strata.MAX_LAYERS;
        done.active = fits();
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
        parent.getUiState().updateDimensions((access, dims) -> StrataPreset.apply(access, dims, strata));
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        Component info;
        if (fits()) {
            String tops = strata().placed().stream().map(p -> String.valueOf(p.top())).collect(Collectors.joining(" / "));
            info = Component.translatable("skystrata.info.height", worldHeight(), tops.isEmpty() ? "-" : tops);
        } else {
            info = Component.translatable("skystrata.info.too_tall", maxHeight + Strata.MIN_Y - 1);
        }
        graphics.drawCenteredString(font, info, width / 2, height - 72, fits() ? 0xA0A0A0 : 0xFF5555);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
