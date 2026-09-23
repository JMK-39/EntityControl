package dev.xyat.entitycontrol.modifier.client.gui.panel;

import dev.xyat.entitycontrol.modifier.client.gui.EntityModifierScreen;
import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticEditBox;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractModifierScrollPanel<T> implements IModifierPanel {
    protected EntityModifierScreen parent;
    protected int x, y, w, h;
    protected KineticEditBox searchBox;

    protected double scroll;
    protected int maxScroll;
    protected boolean isDraggingScroll;
    protected final KineticScroll.State scrollState = new KineticScroll.State();
    protected String selectedEntityId;
    protected LivingEntity previewEntity;
    protected List<T> displayList = new ArrayList<>();
    protected static final int ROW_HEIGHT = 20;

    @Override
    public void init(EntityModifierScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;

        this.searchBox = KineticWidgets.createTextField(
                parent.getFont(),
                x + 4,
                y + 4,
                parent.panelSearchWidth(w),
                Component.empty(),
                getSearchHint(),
                null,
                null
        );
        this.searchBox.setResponder(this::updateSearch);
        parent.addControl(this.searchBox, null);
        initExtra();
    }

    protected void initExtra() {
    }

    @Override
    public void setVisible(boolean visible) {
        if (searchBox != null) searchBox.setVisible(visible);
    }

    @Override
    public void onEntitySelected(String entityId, LivingEntity previewEntity) {
        this.selectedEntityId = entityId;
        this.previewEntity = previewEntity;
        updateSearch(this.searchBox.getValue());
    }

    protected abstract void updateSearch(String query);

    protected void refreshScroll() {
        int listH = getListHeight();
        int visibleRows = listH / rowStride();
        maxScroll = Math.max(0, displayList.size() - visibleRows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    protected int getListHeight() {
        return h - 30;
    }

    protected int listTopOffset() {
        return 28;
    }

    protected int rowStride() {
        return ROW_HEIGHT;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (selectedEntityId == null) return;

        int listY = y + listTopOffset();
        int listH = getListHeight();
        int stride = rowStride();
        GuiTheme.surface(graphics, x + 2, listY, w - 4, listH, GuiTheme.Surface.PANEL_ALT);

        int visibleRows = listH / stride;
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        double smoothScroll = scrollState.follow(scroll, maxScroll, isDraggingScroll);
        int start = (int) Math.floor(smoothScroll + 1.0E-6D);
        int shift = (int) Math.round((smoothScroll - start) * stride);

        parent.enableUiScissor(graphics, x + 2, listY, x + w - 2, listY + listH);
        try {
            // A partial bottom row also needs its successor pre-rendered so it slides in smoothly.
            int renderedRows = (listH + stride - 1) / stride + 1;
            for (int i = 0; i < renderedRows; i++) {
                int index = start + i;
                if (index >= displayList.size()) break;
                int rowY = listY + i * stride - shift;
                renderRow(graphics, displayList.get(index), rowY, mouseX, mouseY);
            }
        } finally {
            parent.disableUiScissor(graphics);
        }

        if (maxScroll > 0) {
            int thumbHeight = KineticScroll.stateThumbHeight(listH, visibleRows, displayList.size(), 15);
            KineticScroll.renderScrollbarState(
                    graphics,
                    mouseX,
                    mouseY,
                    x + w - 6,
                    listY,
                    4,
                    listH,
                    thumbHeight,
                    maxScroll,
                    smoothScroll,
                    isDraggingScroll
            );
        }

        renderExtra(graphics, mouseX, mouseY);
    }

    protected abstract Component getSearchHint();

    protected abstract void renderRow(GuiGraphics graphics, T item, int rowY, int mouseX, int mouseY);

    protected void renderExtra(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchBox != null && !searchBox.isMouseOver(mouseX, mouseY) && parent.focusedControl() == searchBox) {
            parent.clearControlFocus();
        }

        int listY = y + listTopOffset();
        int listH = getListHeight();
        if (KineticMouseButtons.isPrimary(button) && maxScroll > 0
                && mouseX >= x + w - 8 && mouseX <= x + w - 2
                && mouseY >= listY && mouseY < listY + listH) {
            isDraggingScroll = true;
            return true;
        }

        if (selectedEntityId != null
                && mouseX >= x + 2 && mouseX < x + w - 10
                && mouseY >= listY && mouseY < listY + listH) {
            double smoothScroll = scrollState.follow(scroll, maxScroll, isDraggingScroll);
            int start = (int) Math.floor(smoothScroll + 1.0E-6D);
            int stride = rowStride();
            int shift = (int) Math.round((smoothScroll - start) * stride);
            int offset = (int) Math.floor(mouseY - listY + shift);
            if (offset % stride >= ROW_HEIGHT) return false;
            int index = start + offset / stride;
            if (index >= 0 && index < displayList.size()) {
                return onRowClicked(displayList.get(index), mouseX, mouseY, button);
            }
        }
        return false;
    }

    protected abstract boolean onRowClicked(T item, double mouseX, double mouseY, int button);

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingScroll && maxScroll > 0) {
            int listH = getListHeight();
            int visibleRows = listH / rowStride();
            int thumbHeight = KineticScroll.stateThumbHeight(listH, visibleRows, displayList.size(), 15);
            scroll = KineticScroll.stateOffsetFromPointer(mouseY, y + listTopOffset(), listH, thumbHeight, maxScroll);
            scrollState.snap(scroll, maxScroll);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (KineticMouseButtons.isPrimary(button)) isDraggingScroll = false;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
            scroll = scrollState.wheel(scroll, delta, 1.0D, maxScroll);
            return true;
        }
        return false;
    }
}
