package dev.xyat.entitycontrol.client.gui.kit;

import dev.xyat.kineticcore.api.client.gui.layout.KineticLayout;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.IntPredicate;

/**
 * 物品格子网格（纯绘制）：格子间距 2，悬停详情由页面的 {@code contentTooltips} 显示，
 * 滚动条由 {@link EcPage#scrollArea} 处理。只渲染可见格子。
 */
public final class ItemSlotGrid {
    public static final int SLOT = 22;
    public static final int GAP = 2;
    private static final int SCROLLBAR_SPACE = 8;

    private final KineticScrollController scroll = new KineticScrollController();
    private List<ItemStack> stacks = List.of();
    private KineticLayout.Rect area;
    private int columns = 1;

    public void layout(EcPage page, KineticLayout.Rect area, List<ItemStack> stacks) {
        this.area = area;
        columns = Math.max(1, (area.width() - SCROLLBAR_SPACE + GAP) / (SLOT + GAP));
        this.stacks = List.copyOf(stacks);
        int rows = (this.stacks.size() + columns - 1) / columns;
        scroll.update(rows, Math.max(1, (area.height() + GAP) / (SLOT + GAP)));
        page.scrollArea(scroll, area);
    }

    /** 搜索 / 筛选后更新内容，不重建页面（不会重复登记滚动条）。 */
    public void update(List<ItemStack> stacks) {
        this.stacks = List.copyOf(stacks);
        scroll.setOffset(0);
        if (area != null) {
            int rows = (this.stacks.size() + columns - 1) / columns;
            scroll.update(rows, Math.max(1, (area.height() + GAP) / (SLOT + GAP)));
        }
    }

    private int pixelOffset() {
        return (int) Math.round(scroll.smoothOffset() * (SLOT + GAP));
    }

    private KineticLayout.Rect slot(int index) {
        return new KineticLayout.Rect(area.x() + (index % columns) * (SLOT + GAP),
                area.y() + (index / columns) * (SLOT + GAP) - pixelOffset(), SLOT, SLOT);
    }

    public int indexAt(double mouseX, double mouseY) {
        if (area == null || !area.contains(mouseX, mouseY)) return -1;
        for (int index = 0; index < stacks.size(); index++) {
            if (slot(index).contains(mouseX, mouseY)) return index;
        }
        return -1;
    }

    /** {@code error}：数据有问题的格子（红框）。 */
    public void render(KineticGraphics graphics, int mouseX, int mouseY, int selected, IntPredicate error) {
        render(graphics, mouseX, mouseY, index -> index == selected, error, index -> false);
    }

    public void render(KineticGraphics graphics, int mouseX, int mouseY, int selected, IntPredicate error, IntPredicate modified) {
        render(graphics, mouseX, mouseY, index -> index == selected, error, modified);
    }

    /**
     * 多选版本：{@code selected} 为真的格子画选中框。
     * 选中 = 橘黄，悬停 = 蓝色，数据有问题 = 红色，修改过 / 已启用 = 绿色（见 {@link EcPage#stateBorder}）。
     */
    public void render(KineticGraphics graphics, int mouseX, int mouseY, IntPredicate selected, IntPredicate error,
                       IntPredicate modified) {
        if (area == null) return;
        graphics.clipped(area.x(), area.y(), area.right(), area.bottom(), () -> {
            for (int index = 0; index < stacks.size(); index++) {
                KineticLayout.Rect slot = slot(index);
                if (slot.bottom() < area.y() || slot.y() > area.bottom()) continue;
                boolean hovered = area.contains(mouseX, mouseY) && slot.contains(mouseX, mouseY);
                KineticTheme.itemSlot(graphics, slot.x(), slot.y(), SLOT, hovered);
                graphics.item(stacks.get(index), slot.x() + 3, slot.y() + 3);
                EcPage.stateBorder(graphics, slot.x(), slot.y(), SLOT, SLOT, selected.test(index), hovered,
                        error.test(index), modified.test(index));
            }
        });
    }
}
