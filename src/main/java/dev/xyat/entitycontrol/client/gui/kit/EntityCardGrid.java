package dev.xyat.entitycontrol.client.gui.kit;

import dev.xyat.kineticcore.api.client.gui.layout.KineticLayout;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.widget.KineticEntityPreview;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * 生物 3D 模型卡片网格（纯绘制，不占用控件）：方形卡片只显示模型，名称等详情放在页面的悬停提示中。
 * 卡片间距 2，只渲染可见卡片；滚动条由 {@link EcPage#scrollArea} 统一处理拖拽与滚轮。
 * 每个键可以对应一个实际实体（附近生物）或实体类型 ID（规则）；两者都没有时绘制文字卡片（例如“默认规则”）。
 */
public final class EntityCardGrid {
    public static final int CARD_GAP = 2;
    private static final int SCROLLBAR_SPACE = 8;

    private final int cardSize;
    private final KineticEntityPreview preview = KineticEntityPreview.create();
    private final KineticScrollController scroll = new KineticScrollController();
    private List<String> keys = List.of();
    private KineticLayout.Rect area;
    private int columns = 1;

    private Function<String, Entity> entities = key -> null;
    private Function<String, String> types = key -> null;
    private Function<String, Component> fallback = Component::literal;
    private Predicate<String> selected = key -> false;
    private Predicate<String> modified = key -> false;
    private Predicate<String> error = key -> false;
    private Predicate<String> dimmed = key -> false;

    public EntityCardGrid(int cardSize) {
        this.cardSize = cardSize;
    }

    public int cardSize() {
        return cardSize;
    }

    /** 卡片使用的模型预览（旋转方向、速度等设置）。 */
    public KineticEntityPreview preview() {
        return preview;
    }

    public EntityCardGrid entities(Function<String, Entity> entities) {
        this.entities = entities;
        return this;
    }

    public EntityCardGrid types(Function<String, String> types) {
        this.types = types;
        return this;
    }

    public EntityCardGrid fallback(Function<String, Component> fallback) {
        this.fallback = fallback;
        return this;
    }

    public EntityCardGrid selected(Predicate<String> selected) {
        this.selected = selected;
        return this;
    }

    /** 修改过 / 已启用：绿色边框。 */
    public EntityCardGrid modified(Predicate<String> modified) {
        this.modified = modified;
        return this;
    }

    /** 数据有问题（例如实体 ID 未注册）：红色边框。 */
    public EntityCardGrid error(Predicate<String> error) {
        this.error = error;
        return this;
    }

    public EntityCardGrid dimmed(Predicate<String> dimmed) {
        this.dimmed = dimmed;
        return this;
    }

    /** 在页面 build 中调用：确定区域并登记滚动条。 */
    public void layout(EcPage page, KineticLayout.Rect area, List<String> keys) {
        this.area = area;
        columns = Math.max(1, (area.width() - SCROLLBAR_SPACE + CARD_GAP) / (cardSize + CARD_GAP));
        setKeys(keys);
        page.scrollArea(scroll, area);
    }

    /** 搜索 / 筛选后更新内容，不需要重建页面。 */
    public void setKeys(List<String> keys) {
        this.keys = List.copyOf(keys);
        if (area != null) {
            int rows = (this.keys.size() + columns - 1) / columns;
            scroll.update(rows, Math.max(1, (area.height() + CARD_GAP) / (cardSize + CARD_GAP)));
        }
    }

    /** 能完整显示的行数 / 列数（用于布局计算）。 */
    public static int fit(int length, int cardSize) {
        return Math.max(1, (length + CARD_GAP) / (cardSize + CARD_GAP));
    }

    /** 容纳 columns 列所需的宽度（含滚动条空间）。 */
    public static int widthFor(int columns, int cardSize) {
        return columns * cardSize + (columns - 1) * CARD_GAP + SCROLLBAR_SPACE;
    }

    private int pixelOffset() {
        return (int) Math.round(scroll.smoothOffset() * (cardSize + CARD_GAP));
    }

    private KineticLayout.Rect card(int index) {
        return new KineticLayout.Rect(area.x() + (index % columns) * (cardSize + CARD_GAP),
                area.y() + (index / columns) * (cardSize + CARD_GAP) - pixelOffset(), cardSize, cardSize);
    }

    public String keyAt(double mouseX, double mouseY) {
        if (area == null || !area.contains(mouseX, mouseY) || mouseX > area.right() - SCROLLBAR_SPACE) return null;
        for (int index = 0; index < keys.size(); index++) {
            if (card(index).contains(mouseX, mouseY)) return keys.get(index);
        }
        return null;
    }

    public void render(KineticGraphics graphics, int mouseX, int mouseY) {
        if (area == null) return;
        KineticTheme.Palette palette = KineticTheme.current();
        graphics.clipped(area.x(), area.y(), area.right(), area.bottom(), () -> {
            for (int index = 0; index < keys.size(); index++) {
                KineticLayout.Rect card = card(index);
                if (card.bottom() < area.y() || card.y() > area.bottom()) continue;
                String key = keys.get(index);
                boolean hovered = area.contains(mouseX, mouseY) && card.contains(mouseX, mouseY);
                KineticEntityPreview.drawCheckerboard(graphics, card.x(), card.y(), card.width(), card.height());
                Entity entity = entities.apply(key);
                String type = entity == null ? types.apply(key) : null;
                if (entity != null) {
                    preview.render(graphics, entity, "card:" + key, card.x() + 1, card.y() + 1, card.width() - 2,
                            card.height() - 2, hovered);
                } else if (type != null) {
                    preview.render(graphics, type, "card:" + key, card.x() + 1, card.y() + 1, card.width() - 2,
                            card.height() - 2, hovered);
                } else {
                    graphics.fill(card.x(), card.y(), card.right(), card.bottom(), 0xCC202020);
                    graphics.wrappedText(fallback.apply(key), card.x() + 3, card.y() + card.height() / 2 - 8,
                            card.width() - 6, palette.text());
                }
                if (dimmed.test(key)) {
                    graphics.fill(card.x(), card.y(), card.right(), card.bottom(), 0x99000000);
                }
                EcPage.stateBorder(graphics, card.x(), card.y(), card.width(), card.height(),
                        selected.test(key), hovered, error.test(key), modified.test(key));
            }
        });
    }

    public void reset() {
        scroll.setOffset(0);
    }

    public void clear() {
        preview.clear();
    }
}
