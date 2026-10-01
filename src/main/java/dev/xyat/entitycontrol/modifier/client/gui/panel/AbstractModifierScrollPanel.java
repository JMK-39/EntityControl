package dev.xyat.entitycontrol.modifier.client.gui.panel;

import dev.xyat.entitycontrol.modifier.client.gui.EntityModifierScreen;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.KineticTextField;
import dev.xyat.kineticcore.api.client.gui.widget.list.KineticRowList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractModifierScrollPanel<T> implements IModifierPanel {
    protected static final int ROW_HEIGHT = 20;

    protected EntityModifierScreen parent;
    protected int x;
    protected int y;
    protected int w;
    protected int h;
    protected KineticTextField searchBox;
    protected KineticRowList<T> rowList;
    protected String selectedEntityId;
    protected LivingEntity previewEntity;
    protected List<T> displayList = new ArrayList<>();
    private boolean visible;

    @Override
    public final void build(EntityModifierScreen parent, KineticUi ui, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;

        searchBox = ui.textField(x, y, parent.panelSearchWidth(w))
                .label(getSearchHint())
                .placeholder(getSearchHint())
                .value(searchValue())
                .onChange(this::updateSearch)
                .firstShownTextAsDefault().build();

        rowList = ui.add(new PanelRowList(
                x,
                y + listTopOffset(),
                w,
                getListHeight(),
                rowStride()
        ));
        rowList.setItems(displayList);
        initExtra(ui);
        setVisible(visible);
    }

    protected void initExtra(KineticUi ui) {
    }

    protected String searchValue() {
        return searchBox == null ? "" : searchBox.textValue();
    }

    protected final void refreshRows() {
        if (rowList != null) {
            rowList.setItems(displayList);
        }
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        if (searchBox != null) searchBox.setControlVisible(visible);
        if (rowList != null) rowList.setControlVisible(visible);
    }

    protected final boolean isVisible() {
        return visible;
    }

    @Override
    public void onEntitySelected(String entityId, LivingEntity previewEntity) {
        this.selectedEntityId = entityId;
        this.previewEntity = previewEntity;
        updateSearch(searchBox == null ? "" : searchBox.textValue());
    }

    protected abstract void updateSearch(String query);

    protected int getListHeight() {
        return h - listTopOffset();
    }

    protected int listTopOffset() {
        return 20;
    }

    protected int rowStride() {
        return ROW_HEIGHT + 2;
    }

    @Override
    public void render(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible || selectedEntityId == null) return;
        renderExtra(graphics, mouseX, mouseY);
    }

    protected abstract Component getSearchHint();

    protected abstract void renderRow(KineticGraphics graphics, T item, int index, int rowX, int rowY,
                                      int rowWidth, int rowHeight, int mouseX, int mouseY,
                                      boolean hovered, boolean selected);

    protected Component rowTooltip(T item) {
        return null;
    }

    protected void renderExtra(KineticGraphics graphics, int mouseX, int mouseY) {
    }

    protected abstract boolean onRowClicked(T item, int index, MouseInput input);

    protected void onListSelectionRestored(T item, int index) {
    }

    protected final void restoreRowSelection(T item) {
        if (rowList == null || item == null) return;
        int index = displayList.indexOf(item);
        if (index >= 0) {
            rowList.setSelectedIndex(index);
            rowList.scrollTo(index);
            onListSelectionRestored(item, index);
        }
    }

    private final class PanelRowList extends KineticRowList<T> {
        private PanelRowList(int x, int y, int width, int height, int rowHeight) {
            super(x, y, width, height, rowHeight);
        }

        @Override
        protected void renderRow(KineticGraphics graphics, T item, int index, int x, int y, int width, int height,
                                 boolean hovered, boolean selected) {
            AbstractModifierScrollPanel.this.renderRow(
                    graphics, item, index, x, y, width, height, mouseX(), mouseY(), hovered, selected
            );
        }

        @Override
        protected boolean onRowClick(T item, int index, MouseInput input) {
            return AbstractModifierScrollPanel.this.onRowClicked(item, index, input);
        }

        @Override
        protected Component rowTooltip(T item, int index) {
            return AbstractModifierScrollPanel.this.rowTooltip(item);
        }
    }
}
