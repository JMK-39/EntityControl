package dev.xyat.entitycontrol.modifier.client.gui.panel;

import dev.xyat.kineticcore.api.client.widget.KineticWidgets.Scroll;
import dev.xyat.entitycontrol.modifier.client.gui.EntityModifierScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractModifierScrollPanel<T> implements IModifierPanel {
    protected EntityModifierScreen parent;
    protected int x, y, w, h;
    protected EditBox searchBox;

    protected double scroll = 0D;
    protected int maxScroll = 0;
    protected boolean isDraggingScroll = false;
    protected final Scroll.State scrollState = new Scroll.State();
    protected String selectedEntityId = null;
    protected LivingEntity previewEntity = null;
    protected List<T> displayList = new ArrayList<>();
    protected static final int ROW_HEIGHT = 20;

    @Override
    public void init(EntityModifierScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x; this.y = y; this.w = w; this.h = h;

        // UX优化：内部搜索框剪短 (减少宽度的同时居中对齐)，高度微调为18增加精致感
        this.searchBox = new EditBox(parent.getFont(), x + 4, y + 4, w - 80, 18, Component.empty());
        this.searchBox.setResponder(this::updateSearch);
        parent.addPanelWidget(this.searchBox);
        initExtra();
    }

    protected void initExtra() {}

    @Override
    public void setVisible(boolean visible) {
        if (searchBox != null) searchBox.visible = visible;
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
        int visibleRows = listH / ROW_HEIGHT;
        maxScroll = Math.max(0, displayList.size() - visibleRows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    protected int getListHeight() {
        return h - 30; // 留出底部边距
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (selectedEntityId == null) return;
        if (searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            g.drawString(parent.getFont(), getSearchHint(), searchBox.getX() + 6, searchBox.getY() + 5, 0xFFAAAAAA, false);
        }

        int listY = y + 28; // 下移，不和搜索框贴太近
        int listH = getListHeight();

        // UX优化：列表背景框往内收2px，留出间距呼吸感
        g.fill(x + 2, listY, x + w - 2, listY + listH, 0x88000000);

        int visibleRows = listH / ROW_HEIGHT;
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        double smoothScroll = scrollState.follow(scroll, maxScroll, isDraggingScroll);
        int start = (int) Math.floor(smoothScroll + 1.0E-6D);
        int shift = (int) Math.round((smoothScroll - start) * ROW_HEIGHT);
        parent.enableCanvasScissor(g, x + 2, listY, x + w - 2, listY + listH);
        for (int i = 0; i <= visibleRows; i++) {
            int idx = start + i;
            if (idx >= displayList.size()) break;
            int rowY = listY + i * ROW_HEIGHT - shift;
            renderRow(g, displayList.get(idx), rowY, mx, my);
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int thumbH = Scroll.calculateThumbHeight(listH, visibleRows, displayList.size(), 15);
            // 滚动条也稍微往左移一点点
            Scroll.renderScrollbar(g, mx, my, x + w - 6, listY, 4, listH, thumbH, maxScroll, smoothScroll, isDraggingScroll);
        }

        renderExtra(g, mx, my);
    }

    protected abstract Component getSearchHint();
    protected abstract void renderRow(GuiGraphics g, T item, int rowY, int mx, int my);
    protected void renderExtra(GuiGraphics g, int mx, int my) {}

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (searchBox != null && !searchBox.isMouseOver(mx, my)) {
            searchBox.setFocused(false);
            if (parent.getFocused() == searchBox) parent.setFocused(null);
        }

        int listY = y + 28;
        int listH = getListHeight();
        if (btn == 0 && maxScroll > 0 && mx >= x + w - 8 && mx <= x + w - 2 && my >= listY && my < listY + listH) {
            isDraggingScroll = true;
            return true;
        }

        if (selectedEntityId != null && mx >= x + 2 && mx < x + w - 10 && my >= listY && my < listY + listH) {
            double smoothScroll = scrollState.follow(scroll, maxScroll, isDraggingScroll);
            int start = (int) Math.floor(smoothScroll + 1.0E-6D);
            int shift = (int) Math.round((smoothScroll - start) * ROW_HEIGHT);
            int idx = start + (int) Math.floor((my - listY + shift) / ROW_HEIGHT);
            if (idx >= 0 && idx < displayList.size()) {
                return onRowClicked(displayList.get(idx), mx, my);
            }
        }
        return false;
    }

    protected abstract boolean onRowClicked(T item, double mx, double my);

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (isDraggingScroll && maxScroll > 0) {
            int listH = getListHeight();
            int visibleRows = listH / ROW_HEIGHT;
            int thumbH = Scroll.calculateThumbHeight(listH, visibleRows, displayList.size(), 15);
            scroll = Scroll.calculateScrollOffset(my, y + 28, listH, thumbH, maxScroll);
            scrollState.snap(scroll, maxScroll);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0) isDraggingScroll = false;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= x && mx < x + w && my >= y && my < y + h) {
            scroll = scrollState.wheel(scroll, delta, 1.0D / 3.0D, maxScroll);
            return true;
        }
        return false;
    }
}
