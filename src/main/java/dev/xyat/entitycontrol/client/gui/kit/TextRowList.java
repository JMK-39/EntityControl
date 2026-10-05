package dev.xyat.entitycontrol.client.gui.kit;

import dev.xyat.kineticcore.api.client.gui.layout.KineticLayout;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Predicate;

/**
 * 文字行列表（纯绘制）：无间隙条纹行，只有状态行带边框；悬停详情由页面的 {@code contentTooltips} 显示，
 * 滚动条由 {@link EcPage#scrollArea} 处理。只渲染可见行。
 */
public final class TextRowList {
    public static final int ROW = 14;
    // Plain text rows are packed without gaps, like Core's text-row lists.
    public static final int GAP = 0;
    private static final int SCROLLBAR_SPACE = 8;

    public record Row(String key, Component text) {
    }

    private final KineticScrollController scroll = new KineticScrollController();
    private List<Row> rows = List.of();
    private KineticLayout.Rect area;

    public void layout(EcPage page, KineticLayout.Rect area, List<Row> rows) {
        this.area = area;
        this.rows = List.copyOf(rows);
        scroll.update(this.rows.size(), Math.max(1, (area.height() + GAP) / (ROW + GAP)));
        page.scrollArea(scroll, area);
    }

    /** 搜索 / 筛选后更新内容，不需要重建页面（不会重复登记滚动条）。 */
    public void update(List<Row> rows) {
        update(rows, true);
    }

    /** 同上；{@code resetScroll} 为 false 时保留滚动位置（例如改名后刷新文字）。 */
    public void update(List<Row> rows, boolean resetScroll) {
        this.rows = List.copyOf(rows);
        if (resetScroll) scroll.setOffset(0);
        if (area != null) scroll.update(this.rows.size(), Math.max(1, (area.height() + GAP) / (ROW + GAP)));
    }

    private int pixelOffset() {
        return (int) Math.round(scroll.smoothOffset() * (ROW + GAP));
    }

    private KineticLayout.Rect row(int index) {
        return new KineticLayout.Rect(area.x(), area.y() + index * (ROW + GAP) - pixelOffset(),
                area.width() - SCROLLBAR_SPACE, ROW);
    }

    /** 鼠标下的行；没有时返回 null。 */
    public Row rowAt(double mouseX, double mouseY) {
        if (area == null || !area.contains(mouseX, mouseY)) return null;
        for (int index = 0; index < rows.size(); index++) {
            if (row(index).contains(mouseX, mouseY)) return rows.get(index);
        }
        return null;
    }

    /** 只区分选中（橘黄边框）与悬停（蓝色边框）。 */
    public void render(KineticGraphics graphics, int mouseX, int mouseY, Predicate<String> selected) {
        render(graphics, mouseX, mouseY, selected, key -> false, key -> false);
    }

    /**
     * Plain striped rows: only the hovered row, the current choice (yellow), chosen or modified rows (green) and rows
     * with errors (red) are outlined, and their text takes the same color.
     */
    public void render(KineticGraphics graphics, int mouseX, int mouseY, Predicate<String> selected,
                       Predicate<String> modified, Predicate<String> error) {
        if (area == null) return;
        KineticTheme.Palette palette = KineticTheme.current();
        graphics.clipped(area.x(), area.y(), area.right(), area.bottom(), () -> {
            for (int index = 0; index < rows.size(); index++) {
                KineticLayout.Rect rect = row(index);
                if (rect.bottom() < area.y() || rect.y() > area.bottom()) continue;
                Row row = rows.get(index);
                boolean hovered = area.contains(mouseX, mouseY) && rect.contains(mouseX, mouseY);
                boolean isSelected = selected.test(row.key());
                boolean isError = error.test(row.key());
                boolean isModified = modified.test(row.key());
                graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), (index & 1) == 0 ? palette.panel() : palette.panelAlt());
                if (isSelected || hovered || isError) {
                    KineticTheme.stateOutline(graphics, rect.x(), rect.y(), rect.width(), rect.height(), isSelected, hovered, isError);
                } else if (isModified) {
                    KineticTheme.indicatorOutline(graphics, rect.x(), rect.y(), rect.width(), rect.height(), KineticTheme.Indicator.SUCCESS);
                }
                int textColor = isError ? 0xFFFF5555 : isSelected ? 0xFFFFAA00 : isModified ? 0xFF55DD88 : palette.text();
                graphics.scrollingText(row.text(), rect.x() + 5, rect.y() + 3, rect.width() - 10, textColor, false);
            }
        });
    }
}
