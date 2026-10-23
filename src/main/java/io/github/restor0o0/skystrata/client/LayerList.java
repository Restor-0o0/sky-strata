package io.github.restor0o0.skystrata.client;

import io.github.restor0o0.skystrata.Strata;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

class LayerList extends ObjectSelectionList<LayerList.Entry> {
    private final StrataScreen screen;

    LayerList(StrataScreen screen, Minecraft minecraft, int x, int y, int width, int height) {
        super(minecraft, width, height, y, 16);
        this.screen = screen;
        setX(x);
    }

    void rebuild(int selected) {
        List<Strata.Placed> placed = new Strata(screen.layers).placed();
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < placed.size(); i++) entries.add(new Entry(i, placed.get(i)));
        replaceEntries(entries);
        super.setSelected(selected >= 0 && selected < entries.size() ? entries.get(selected) : null);
        if (getSelected() != null) scrollToEntry(getSelected());
    }

    int selectedIndex() {
        Entry e = getSelected();
        return e == null ? -1 : e.index;
    }

    @Override
    public void setSelected(@Nullable Entry entry) {
        Entry old = getSelected();
        super.setSelected(entry);
        if (entry != old) screen.select(entry == null ? -1 : entry.index);
    }

    @Override
    public int getRowWidth() {
        return width - 14;
    }

    @Override
    protected int scrollBarX() {
        return getX() + width - 6;
    }

    class Entry extends ObjectSelectionList.Entry<Entry> {
        private final int index;
        private final Component name;
        private final String range;

        Entry(int index, Strata.Placed p) {
            this.index = index;
            this.name = Component.literal((index + 1) + ". ")
                    .append(Component.translatable("skystrata.kind." + p.layer().kind().getSerializedName()));
            this.range = p.base() + "–" + p.top();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int top = getContentY() + 1;
            graphics.text(minecraft.font, name, getContentX() + 2, top, 0xFFFFFFFF);
            graphics.text(minecraft.font, range, getContentRight() - 2 - minecraft.font.width(range), top, 0xFFA0A0A0);
        }

        @Override
        public Component getNarration() {
            return Component.translatable("narrator.select", name);
        }
    }
}
