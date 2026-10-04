package dev.xyat.entitycontrol.client.gui.kit;

import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.layout.KineticLayout;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.ui.NumberType;
import dev.xyat.kineticcore.api.client.gui.widget.KineticButton;
import dev.xyat.kineticcore.api.client.gui.widget.KineticDropdown;
import dev.xyat.kineticcore.api.client.gui.widget.KineticNumberField;
import dev.xyat.kineticcore.api.client.gui.widget.KineticTextField;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 实体控制所有界面的统一外框与排版规范。
 *
 * <pre>
 * ┌ 外框（距画布边缘 6）───────────────────────────────────────┐
 * │ [‹ 返回]            标题（居中）          [操作] [更多 ▾] │  ← 顶栏，控件距边框 6
 * │ ─────────────────────────────────────────────────────── │
 * │ 内容区 body()：面板之间间距 4，面板内边距 6，控件间距 4     │
 * │ 状态行（可选）                                            │
 * └──────────────────────────────────────────────────────────┘
 * </pre>
 *
 * 子类实现 {@link #buildContent}，只在 {@link #body()} 内布局；标签行统一由 {@link Form} / {@link ScrollForm}
 * 注册，悬停标签文字即显示说明。
 */
public abstract class EcPage extends KineticPage {
    /** 画布边缘到外框。 */
    public static final int OUTER = 6;
    /** 边框到内容的内边距。 */
    public static final int PAD = 6;
    /** 控件、面板之间的最小间距。 */
    public static final int GAP = 4;
    /** 标准控件高度。 */
    public static final int H = CONTROL_HEIGHT;
    /** 表单行步进。 */
    public static final int ROW = H + GAP;
    /** 文本行步进。 */
    public static final int LINE = 11;
    /** 选择 / 按钮类控件的标准宽度：不必占满整行，把空间留给内容。 */
    public static final int CONTROL_WIDTH = 112;
    /** 数值输入框的标准宽度。 */
    public static final int NUMBER_WIDTH = 52;
    /** 带滚动条的设置列表所需的额外宽度（内边距 + 滚动条）。 */
    public static final int FORM_EXTRA = PAD * 2 + 8;
    /** 按钮的最小宽度。 */
    public static final int MIN_BUTTON = 40;

    /** 放下这些文字所需的按钮宽度（取最长的一条，含内边距）。 */
    public static int fitWidth(List<? extends Component> texts) {
        int need = MIN_BUTTON;
        for (Component text : texts) {
            if (text != null) need = Math.max(need, KineticText.width(text) + 20);
        }
        return need;
    }

    /**
     * 统一的状态边框（使用 KineticCore 主题色）：选中 = 橘黄，悬停 = 蓝色，数据有问题 = 红色，
     * 修改过 / 已启用 = 绿色，其余为普通白框。不再使用勾选角标或色条。
     */
    public static void stateBorder(KineticGraphics graphics, int x, int y, int width, int height,
                                   boolean selected, boolean hovered, boolean error, boolean modified) {
        if (selected || hovered || error) {
            KineticTheme.stateOutline(graphics, x, y, width, height, selected, hovered, error);
        } else if (modified) {
            KineticTheme.indicatorOutline(graphics, x, y, width, height, KineticTheme.Indicator.SUCCESS);
        } else {
            KineticTheme.stateOutline(graphics, x, y, width, height, false, false, false);
        }
    }

    /** 按文字截短按钮 / 开关：宽度取最长文字所需，不超过原宽度（左对齐）。 */
    public static KineticLayout.Rect fit(KineticLayout.Rect rect, Component... texts) {
        return new KineticLayout.Rect(rect.x(), rect.y(), Math.min(rect.width(), fitWidth(List.of(texts))), rect.height());
    }

    /** 设置列表的标签列宽度：按最长的标签计算（含内边距），控件紧跟在标签后面。 */
    public static int labelColumn(List<? extends Component> labels, int max) {
        int widest = 0;
        for (Component label : labels) widest = Math.max(widest, KineticText.width(label));
        return Math.max(80, Math.min(max, widest + PAD + GAP * 3));
    }

    /** 截成标准控件宽度（左对齐）。 */
    public static KineticLayout.Rect compact(KineticLayout.Rect rect) {
        return new KineticLayout.Rect(rect.x(), rect.y(), Math.min(rect.width(), CONTROL_WIDTH), rect.height());
    }

    /** 截成数值输入框宽度（左对齐）。 */
    public static KineticLayout.Rect number(KineticLayout.Rect rect) {
        return new KineticLayout.Rect(rect.x(), rect.y(), Math.min(rect.width(), NUMBER_WIDTH), rect.height());
    }

    /** 数值输入框提示：先说明含义，再给出可填范围。 */
    public static Component rangeTip(Component meaning, Object min, Object max) {
        return meaning.copy().append("\n").append(KineticI18n.translatable("gui.entitycontrol.common.range", min, max));
    }

    private KineticLayout.Rect frame;
    private KineticLayout.Rect header;
    private KineticLayout.Rect body;
    private KineticLayout.Rect titleRect;
    private final List<Label> labels = new ArrayList<>();
    private final List<ScrollForm> forms = new ArrayList<>();
    private final List<ScrollArea> areas = new ArrayList<>();
    private final List<Section> sections = new ArrayList<>();
    private final Map<String, KineticButton> headerButtons = new HashMap<>();

    protected EcPage(Component title) {
        super(title);
        useCanvas(CANVAS_WIDTH, CANVAS_HEIGHT, 0);
        setPausesGame(false);
    }

    // ------------------------------------------------------------------ 子类钩子

    /** 在 {@link #body()} 内创建控件。 */
    protected abstract void buildContent(KineticUi ui, KineticLayout.Rect body);

    /** 顶栏居中显示的标题；默认为页面标题。 */
    protected Component headerTitle() {
        return title();
    }

    /** 顶栏右侧的操作按钮（从左到右）。 */
    protected List<HeaderAction> headerActions() {
        return List.of();
    }

    /** 绘制面板等背景（在控件下方）。 */
    protected void renderContent(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    /** 需要盖在控件上方的内容。 */
    protected void renderOverlay(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    /** 返回 true 表示已显示提示。 */
    protected boolean contentTooltips(int mouseX, int mouseY) {
        return false;
    }

    protected boolean contentClickCapture(MouseInput input) {
        return false;
    }

    protected boolean contentClick(MouseInput input) {
        return false;
    }

    protected boolean contentScroll(ScrollInput input) {
        return false;
    }

    /** “返回”按钮与 Esc 的行为；默认直接回到上级页面。返回 true 表示已处理（例如弹出确认框）。 */
    protected boolean onBack() {
        return false;
    }

    // ------------------------------------------------------------------ 布局

    @Override
    protected final void build(KineticUi ui) {
        labels.clear();
        forms.clear();
        areas.clear();
        sections.clear();
        headerButtons.clear();

        frame = new KineticLayout.Rect(OUTER, OUTER, width() - OUTER * 2, height() - OUTER * 2);
        header = new KineticLayout.Rect(frame.x() + PAD, frame.y() + PAD, frame.width() - PAD * 2, H);
        // 不设底部状态行：说明全部放在悬停提示里，结果用提示弹窗反馈。
        int bottom = frame.bottom() - PAD;
        int bodyTop = header.bottom() + GAP * 2 + 1;
        body = new KineticLayout.Rect(frame.x() + PAD, bodyTop, frame.width() - PAD * 2, Math.max(0, bottom - bodyTop));

        Component backText = KineticI18n.translatable("gui.entitycontrol.common.back");
        int backWidth = Math.min(buttonWidth(backText, 56), Math.min(CONTROL_WIDTH, header.width() / 4));
        ui.button(header.x(), header.y(), backWidth)
                .text(backText)
                .tooltip(KineticI18n.translatable("tip.entitycontrol.common.back"))
                .onClick(this::close)
                .build();

        int right = header.right();
        List<HeaderAction> actions = headerActions();
        List<Component> actionTexts = new ArrayList<>(actions.size());
        int[] preferredWidths = new int[actions.size()];
        long preferredTotal = 0;
        for (int index = 0; index < actions.size(); index++) {
            HeaderAction action = actions.get(index);
            Component text = action.menu() == null ? action.text() : action.text().copy().append(" ▾");
            actionTexts.add(text);
            preferredWidths[index] = action.width() > 0 ? action.width() : buttonWidth(text, 56);
            preferredTotal += preferredWidths[index];
        }
        // All actions share one budget after reserving Back, a title viewport and its two gaps.
        int titleReserve = Math.min(CONTROL_WIDTH, header.width() / 4);
        int titleGap = Math.min(GAP * 2, Math.max(0, (header.width() - backWidth - titleReserve) / 2));
        int actionSpace = Math.max(0, header.width() - backWidth - titleReserve - titleGap * 2);
        int actionGap = actions.size() > 1 ? Math.min(GAP, actionSpace / (actions.size() - 1)) : 0;
        int actionBudget = actionSpace - actionGap * Math.max(0, actions.size() - 1);
        int actionCap = actions.isEmpty() ? 0 : actionBudget / actions.size();
        for (int index = actions.size() - 1; index >= 0; index--) {
            HeaderAction action = actions.get(index);
            Component text = actionTexts.get(index);
            int width = preferredTotal <= actionBudget ? preferredWidths[index]
                    : Math.min(preferredWidths[index], actionCap);
            int x = right - width;
            KineticButton button = ui.button(x, header.y(), width)
                    .text(text)
                    .tooltip(action.tooltip())
                    .enabled(action.enabled())
                    .onClick(action.menu() == null
                            ? action.action()
                            : () -> openContextMenu(x, header.bottom() + 2, action.menu().get()))
                    .build();
            if (action.id() != null) {
                headerButtons.put(action.id(), button);
            }
            right = x - actionGap;
        }
        int titleLeft = header.x() + backWidth + titleGap;
        int titleRight = right + (actions.isEmpty() ? 0 : actionGap) - titleGap;
        int side = Math.max(titleLeft - header.x(), header.right() - titleRight);
        int centeredWidth = header.width() - side * 2;
        titleRect = centeredWidth > 0
                ? new KineticLayout.Rect(header.x() + side, header.y(), centeredWidth, H)
                : new KineticLayout.Rect(titleLeft, header.y(), Math.max(0, titleRight - titleLeft), H);

        buildContent(ui, body);
    }

    public final KineticLayout.Rect body() {
        return body;
    }

    /** 带标题的面板：登记后由页面绘制，返回标题下方的内容区域（已去掉内边距）。 */
    public final KineticLayout.Rect section(KineticLayout.Rect rect, Component title) {
        sections.add(new Section(rect, title));
        KineticLayout.Rect inner = inner(rect);
        return new KineticLayout.Rect(inner.x(), inner.y() + 10 + GAP, inner.width(), Math.max(0, inner.height() - 10 - GAP));
    }

    private record Section(KineticLayout.Rect rect, Component title) {
    }

    public final KineticLayout.Rect frame() {
        return frame;
    }

    /** 顶栏按钮（按 {@link HeaderAction#id()}）。 */
    protected final KineticButton headerButton(String id) {
        return headerButtons.get(id);
    }

    /** 按文字宽度计算按钮宽度。 */
    public static int buttonWidth(Component text, int minimum) {
        return Math.max(minimum, KineticText.width(text) + 16);
    }

    /** 两列等分。 */
    public static KineticLayout.Split halves(KineticLayout.Rect area) {
        return KineticLayout.splitHorizontal(area, (area.width() - GAP) / 2, GAP);
    }

    /** 左栏固定宽度，右栏占满。 */
    public static KineticLayout.Split leftColumn(KineticLayout.Rect area, int leftWidth) {
        return KineticLayout.splitHorizontal(area, leftWidth, GAP);
    }

    /** 面板内部（去掉内边距）。 */
    public static KineticLayout.Rect inner(KineticLayout.Rect panel) {
        return panel.inset(PAD, PAD);
    }

    /** 从区域顶部切出一行控件高度，返回 [行, 剩余]。 */
    public static KineticLayout.Split takeRow(KineticLayout.Rect area) {
        return KineticLayout.splitVertical(area, H, GAP);
    }

    /** 从区域顶部切出指定高度。 */
    public static KineticLayout.Split takeTop(KineticLayout.Rect area, int height) {
        return KineticLayout.splitVertical(area, Math.min(height, area.height()), GAP);
    }

    /** 从区域底部切出指定高度，返回 [上方剩余, 底部]。 */
    public static KineticLayout.Split takeBottom(KineticLayout.Rect area, int height) {
        int top = Math.max(0, area.height() - height - GAP);
        return KineticLayout.splitVertical(area, top, GAP);
    }

    // ------------------------------------------------------------------ 标签与表单

    /** 注册一个标签（背景层绘制，悬停显示说明）。rect 高度按控件行计算，文字垂直居中。 */
    public final void label(KineticLayout.Rect rect, Component text, Component tooltip) {
        labels.add(new Label(rect, text, lines(tooltip), KineticTheme.current().text()));
    }

    /** 注册一个次要说明文字标签。 */
    public final void mutedLabel(KineticLayout.Rect rect, Component text, Component tooltip) {
        labels.add(new Label(rect, text, lines(tooltip), KineticTheme.current().text()));
    }

    private static List<Component> lines(Component tooltip) {
        return tooltip == null ? List.of() : List.of(tooltip);
    }

    /** 以左侧标签 + 右侧控件的方式逐行排列。 */
    public final Form form(KineticLayout.Rect area, int labelWidth) {
        return new Form(area, labelWidth);
    }

    /** 常规表单：标签在左，控件在右，每行步进 {@link #ROW}。 */
    public final class Form {
        private final KineticLayout.Rect area;
        private final int labelWidth;
        private int y;

        private Form(KineticLayout.Rect area, int labelWidth) {
            this.area = area;
            this.labelWidth = Math.max(0, Math.min(area.width() - 24, labelWidth));
            this.y = area.y();
        }

        /** 带标签的一行，返回控件区域。 */
        public KineticLayout.Rect row(Component text, Component tooltip) {
            label(new KineticLayout.Rect(area.x(), y, labelWidth - GAP, H), text, tooltip);
            KineticLayout.Rect control = new KineticLayout.Rect(area.x() + labelWidth, y, area.width() - labelWidth, H);
            y += ROW;
            return control;
        }

        /** 无标签整行。 */
        public KineticLayout.Rect full() {
            KineticLayout.Rect control = new KineticLayout.Rect(area.x(), y, area.width(), H);
            y += ROW;
            return control;
        }

        /** 仅文字的一行（小标题）。 */
        public void heading(Component text, Component tooltip) {
            label(new KineticLayout.Rect(area.x(), y, area.width(), H), text, tooltip);
            y += ROW;
        }

        public void gap(int pixels) {
            y += pixels;
        }

        public int y() {
            return y;
        }

        /** 表单剩余区域。 */
        public KineticLayout.Rect remaining() {
            return new KineticLayout.Rect(area.x(), y, area.width(), Math.max(0, area.bottom() - y));
        }
    }

    /**
     * 可滚动的设置列表：每行左侧标签、右侧控件、行底分隔线。对象需作为页面字段保留，以便重建后保持滚动位置。
     */
    public static final class ScrollForm {
        public static final int ROW_HEIGHT = H + 8;
        private final KineticScrollController scroll = new KineticScrollController();
        private final List<Label> rowLabels = new ArrayList<>();
        private KineticLayout.Rect area;
        private int rows;
        private int labelWidth;

        /** 在 build 中调用：登记区域与行数，返回滚动视口（控件用未滚动坐标创建）。 */
        public KineticUi attach(EcPage page, KineticUi ui, KineticLayout.Rect area, int rows, int labelWidth) {
            this.area = area;
            this.rows = rows;
            this.labelWidth = labelWidth;
            rowLabels.clear();
            scroll.update(rows, Math.max(1, area.height() / ROW_HEIGHT));
            page.forms.add(this);
            return ui.scrollViewport(area.x(), area.y(), area.right(), area.bottom(),
                    () -> scroll.smoothOffset() * ROW_HEIGHT);
        }

        /** 第 index 行的控件区域（未滚动坐标）。 */
        public KineticLayout.Rect control(int index) {
            int x = area.x() + labelWidth;
            return new KineticLayout.Rect(x, rowTop(index) + 4, contentRight() - PAD - x, H);
        }

        /** 第 index 行的标签。 */
        public void label(int index, Component text, Component tooltip) {
            label(index, text, lines(tooltip));
        }

        /** 第 index 行的标签（多行说明）。 */
        public void label(int index, Component text, List<Component> tooltip) {
            rowLabels.add(new Label(new KineticLayout.Rect(area.x() + PAD, rowTop(index) + 4,
                    labelWidth - PAD - GAP, H), text, tooltip, KineticTheme.current().text()));
        }

        public void reset() {
            scroll.setOffset(0);
        }

        private int rowTop(int index) {
            return area.y() + index * ROW_HEIGHT;
        }

        private int contentRight() {
            return scroll.canScroll() ? area.right() - 8 : area.right();
        }

        private int pixelOffset() {
            return (int) Math.round(scroll.smoothOffset() * ROW_HEIGHT);
        }

        private void render(KineticGraphics graphics, int mouseX, int mouseY) {
            KineticTheme.Palette palette = KineticTheme.current();
            int offset = pixelOffset();
            graphics.clipped(area.x(), area.y(), area.right(), area.bottom(), () -> {
                for (int index = 0; index < rows; index++) {
                    int top = rowTop(index) - offset;
                    if (top + ROW_HEIGHT < area.y() || top > area.bottom()) {
                        continue;
                    }
                    graphics.hLine(area.x() + PAD, contentRight() - PAD - 1, top + ROW_HEIGHT - 1,
                            (palette.border() & 0x00FFFFFF) | 0x80000000);
                }
                for (Label label : rowLabels) {
                    KineticLayout.Rect rect = label.rect().move(0, -offset);
                    graphics.scrollingText(label.text(), rect.x(), rect.y() + 4, rect.width(), label.color(), false);
                }
            });
            scroll.render(graphics, mouseX, mouseY, area.right() - 4, area.y(), 4, area.height(), 16);
        }

        private Label hovered(int mouseX, int mouseY) {
            if (area == null || !area.contains(mouseX, mouseY)) {
                return null;
            }
            int offset = pixelOffset();
            for (Label label : rowLabels) {
                if (label.rect().move(0, -offset).contains(mouseX, mouseY)) {
                    return label;
                }
            }
            return null;
        }
    }

    /** 顶栏按钮；{@code menu} 不为空时点击展开下拉菜单。 */
    public record HeaderAction(String id, Component text, Component tooltip, Runnable action,
                               Supplier<List<KineticOverlays.MenuItem>> menu, boolean enabled, int width) {
        public static HeaderAction button(String id, Component text, Component tooltip, Runnable action) {
            return new HeaderAction(id, text, tooltip, action, null, true, 0);
        }

        public static HeaderAction menu(Component text, Component tooltip,
                                        Supplier<List<KineticOverlays.MenuItem>> items) {
            return new HeaderAction(null, text, tooltip, () -> { }, items, true, 0);
        }

        /** 通用“更多”菜单。 */
        public static HeaderAction more(Supplier<List<KineticOverlays.MenuItem>> items) {
            return menu(KineticI18n.translatable("gui.entitycontrol.common.more"),
                    KineticI18n.translatable("tip.entitycontrol.common.more"), items);
        }

        public HeaderAction enabled(boolean value) {
            return new HeaderAction(id, text, tooltip, action, menu, value, width);
        }
    }

    /** 自绘区域的滚动条：在 build 中登记后，页面负责绘制滚动条、拖拽与滚轮。 */
    public final void scrollArea(KineticScrollController scroll, KineticLayout.Rect rect) {
        areas.add(new ScrollArea(scroll, rect));
    }

    private record ScrollArea(KineticScrollController scroll, KineticLayout.Rect rect) {
    }

    public record Label(KineticLayout.Rect rect, Component text, List<Component> tooltip, int color) {
    }

    /** 在指定位置展开菜单（供页面内的组件使用）。 */
    public final void openMenu(double x, double y, List<KineticOverlays.MenuItem> items) {
        openContextMenu(x, y, items);
    }

    /** 显示物品提示（供页面内的组件使用）。 */
    public final void itemTooltip(net.minecraft.world.item.ItemStack stack) {
        showItemTooltip(stack);
    }

    /** 下拉选项简写。 */
    public static KineticDropdown.Option option(String value, Component text, Component tooltip) {
        return new KineticDropdown.Option(value, text, tooltip);
    }

    /**
     * 选择按钮：显示当前选项的本地化名称，点击展开菜单，菜单里只显示本地化名称（不显示内部值）。
     * 用于替代 KineticCore 的 dropdown（其弹出菜单固定以内部值为主文字，适合 ID 列表而不适合枚举选项）。
     */
    public final ChoiceBuilder choice(KineticUi ui, int x, int y, int width, List<KineticDropdown.Option> options) {
        return new ChoiceBuilder(ui, x, y, width, options);
    }

    public final class ChoiceBuilder {
        private final KineticUi ui;
        private final int x;
        private final int y;
        private final int width;
        private final List<KineticDropdown.Option> options;
        private String selected;
        private Component tooltip;
        private boolean enabled = true;
        private java.util.function.Consumer<String> onChange = ignored -> { };

        private ChoiceBuilder(KineticUi ui, int x, int y, int width, List<KineticDropdown.Option> options) {
            this.ui = ui;
            this.x = x;
            this.y = y;
            this.width = width;
            this.options = List.copyOf(options);
        }

        public ChoiceBuilder selected(String value) {
            this.selected = value;
            return this;
        }

        public ChoiceBuilder tooltip(Component tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public ChoiceBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public ChoiceBuilder onChange(java.util.function.Consumer<String> onChange) {
            this.onChange = onChange == null ? ignored -> { } : onChange;
            return this;
        }

        public KineticButton build() {
            String[] current = {selected};
            KineticDropdown.Option initial = find(current[0]);
            if (initial == null && !options.isEmpty() && selected == null) {
                initial = options.get(0);
                current[0] = initial.value();
            }
            // 按钮宽度取最长选项所需（不超过给定宽度），菜单宽度同样按内容计算，不占满整行。
            List<Component> labels = new ArrayList<>(options.size());
            for (KineticDropdown.Option option : options) labels.add(choiceText(option));
            int buttonWidth = Math.min(width, fitWidth(labels));
            int menuWidth = Math.max(buttonWidth, fitWidth(labels) + 12);
            KineticButton[] self = new KineticButton[1];
            var builder = ui.button(x, y, buttonWidth)
                    .text(choiceText(initial))
                    .enabled(enabled && !options.isEmpty())
                    .onClick(() -> {
                        List<KineticOverlays.MenuItem> items = new ArrayList<>(options.size());
                        for (KineticDropdown.Option option : options) {
                            Component text = display(option);
                            items.add(KineticOverlays.MenuItem.toggle(text,
                                    option.tooltip().getString().isBlank() ? text : option.tooltip(),
                                    option.value().equals(current[0]), () -> {
                                        current[0] = option.value();
                                        self[0].setText(choiceText(option));
                                        onChange.accept(option.value());
                                    }));
                        }
                        openContextMenu(self[0].controlX(), self[0].controlY() + self[0].controlHeight() + 2, items,
                                menuWidth);
                    });
            if (tooltip != null) {
                builder.tooltip(tooltip);
            }
            self[0] = builder.build();
            return self[0];
        }

        private KineticDropdown.Option find(String value) {
            if (value == null) {
                return null;
            }
            for (KineticDropdown.Option option : options) {
                if (option.value().equals(value)) {
                    return option;
                }
            }
            return null;
        }
    }

    /** 开关菜单中的一项：名称、提示、读取与写入。 */
    public record ToggleOption(Component label, Component tooltip, java.util.function.BooleanSupplier getter,
                               java.util.function.Consumer<Boolean> setter) {
    }

    /**
     * 多个开关合并为一个按钮：按钮显示“名称（n/m）▾”，点击展开可逐项勾选的菜单。
     * 勾选后就地更新按钮文字并调用 {@code onChange}，不重建页面（输入框焦点、滚动位置都保留）。
     */
    public final KineticButton toggleMenu(KineticUi ui, KineticLayout.Rect rect, Component title, Component tooltip,
                                          List<ToggleOption> options, boolean enabled, Runnable onChange) {
        List<ToggleOption> copy = List.copyOf(options);
        List<Component> labels = new ArrayList<>(copy.size());
        for (ToggleOption option : copy) labels.add(option.label());
        // 按钮宽度按“名称（m/m）▾”计算，菜单宽度按最长的选项计算（勾选标记另留空间）。
        int buttonWidth = Math.min(rect.width(), fitWidth(List.of(KineticI18n.translatable(
                "gui.entitycontrol.common.toggle_summary", title, copy.size(), copy.size()).append(" ▾"))));
        int menuWidth = Math.max(buttonWidth, fitWidth(labels) + 12);
        KineticButton[] self = new KineticButton[1];
        var builder = ui.button(rect.x(), rect.y(), buttonWidth)
                .text(toggleSummary(title, copy))
                .enabled(enabled && !copy.isEmpty())
                .onClick(() -> {
                    List<KineticOverlays.MenuItem> items = new ArrayList<>(copy.size());
                    for (ToggleOption option : copy) {
                        boolean value = option.getter().getAsBoolean();
                        items.add(KineticOverlays.MenuItem.toggle(option.label(),
                                option.tooltip() == null ? option.label() : option.tooltip(), value, () -> {
                                    option.setter().accept(!value);
                                    self[0].setText(toggleSummary(title, copy));
                                    if (onChange != null) onChange.run();
                                }));
                    }
                    openContextMenu(self[0].controlX(), self[0].controlY() + self[0].controlHeight() + 2, items,
                            menuWidth);
                });
        if (tooltip != null) {
            builder.tooltip(tooltip);
        }
        self[0] = builder.build();
        return self[0];
    }

    private static Component toggleSummary(Component title, List<ToggleOption> options) {
        int on = 0;
        for (ToggleOption option : options) {
            if (option.getter().getAsBoolean()) on++;
        }
        return KineticI18n.translatable("gui.entitycontrol.common.toggle_summary", title, on, options.size()).append(" ▾");
    }

    private static Component display(KineticDropdown.Option option) {
        return option.translation().getString().isBlank() ? Component.literal(option.value()) : option.translation();
    }

    private static Component choiceText(KineticDropdown.Option option) {
        return option == null ? Component.literal("▾") : display(option).copy().append(" ▾");
    }

    // ------------------------------------------------------------------ 输入控件

    /**
     * 小数输入框。{@code value} 为 null 时显示为空；清空输入框时回调 null（表示“不设置”），
     * 其它内容按范围截取后回调。提示显示“含义 + 可填范围”。
     */
    public final KineticNumberField decimalField(KineticUi ui, KineticLayout.Rect rect, Double value, double min, double max,
                                                 Component tip, Consumer<Double> onChange) {
        KineticNumberField field = ui.numberField(rect.x(), rect.y(), rect.width(), NumberType.DECIMAL)
                .label(Component.empty())
                .allowNegative(min < 0)
                .range(min, max)
                .tooltip(rangeTip(tip, NumberType.DECIMAL.format(min), NumberType.DECIMAL.format(max)))
                .build();
        field.setTextValue(value == null ? "" : NumberType.DECIMAL.format(value));
        field.setDefaultText(field.textValue());
        field.onTextChange(text -> {
            String trimmed = text == null ? "" : text.trim();
            if (trimmed.isEmpty()) {
                onChange.accept(null);
                return;
            }
            try {
                double parsed = Double.parseDouble(trimmed);
                if (Double.isFinite(parsed)) onChange.accept(Math.max(min, Math.min(max, parsed)));
            } catch (NumberFormatException ignored) {
            }
        });
        return field;
    }

    /** 整数输入框，规则同 {@link #decimalField}。 */
    public final KineticNumberField intField(KineticUi ui, KineticLayout.Rect rect, Integer value, int min, int max,
                                             Component tip, Consumer<Integer> onChange) {
        KineticNumberField field = ui.numberField(rect.x(), rect.y(), rect.width(), NumberType.INT)
                .label(Component.empty())
                .allowNegative(min < 0)
                .range(min, max)
                .tooltip(rangeTip(tip, min, max))
                .build();
        field.setTextValue(value == null ? "" : Integer.toString(value));
        field.setDefaultText(field.textValue());
        field.onTextChange(text -> {
            String trimmed = text == null ? "" : text.trim();
            if (trimmed.isEmpty()) {
                onChange.accept(null);
                return;
            }
            try {
                onChange.accept(Math.max(min, Math.min(max, Integer.parseInt(trimmed))));
            } catch (NumberFormatException ignored) {
            }
        });
        return field;
    }

    /** 单行文本输入框。 */
    public final KineticTextField textInput(KineticUi ui, KineticLayout.Rect rect, String value, Component placeholder,
                                            Component tip, Consumer<String> onChange) {
        var builder = ui.textField(rect.x(), rect.y(), rect.width())
                .label(Component.empty())
                .maxLength(256)
                .value(value == null ? "" : value)
                .onChange(text -> onChange.accept(text == null ? "" : text))
                .firstShownTextAsDefault();
        if (placeholder != null) builder.placeholder(placeholder);
        if (tip != null) builder.tooltip(tip);
        return builder.build();
    }

    /** 普通按钮：宽度按文字计算，不超过 rect 宽度。 */
    public final KineticButton actionButton(KineticUi ui, KineticLayout.Rect rect, Component text, Component tip,
                                            boolean enabled, Runnable action) {
        KineticLayout.Rect fitted = fit(rect, text);
        var builder = ui.button(fitted.x(), fitted.y(), fitted.width()).text(text).enabled(enabled).onClick(action);
        if (tip != null) builder.tooltip(tip);
        return builder.build();
    }

    /**
     * 点击展开菜单的按钮（文字后自动加 ▾），宽度按文字计算；菜单宽度按最长的菜单项计算，
     * 锚定在按钮实际位置（滚动区域内也正确）。
     */
    public final KineticButton menuButton(KineticUi ui, KineticLayout.Rect rect, Component text, Component tip,
                                          boolean enabled, Supplier<List<KineticOverlays.MenuItem>> items) {
        Component label = text.copy().append(" ▾");
        KineticLayout.Rect fitted = fit(rect, label);
        KineticButton[] self = new KineticButton[1];
        var builder = ui.button(fitted.x(), fitted.y(), fitted.width())
                .text(label)
                .enabled(enabled)
                .onClick(() -> {
                    List<KineticOverlays.MenuItem> entries = items.get();
                    List<Component> labels = new ArrayList<>(entries.size());
                    for (KineticOverlays.MenuItem entry : entries) if (entry.label() != null) labels.add(entry.label());
                    openContextMenu(self[0].controlX(), self[0].controlY() + self[0].controlHeight() + 2, entries,
                            Math.max(self[0].controlWidth(), fitWidth(labels) + 12));
                });
        if (tip != null) builder.tooltip(tip);
        self[0] = builder.build();
        return self[0];
    }

    /** 确认对话框（取消不做任何事）。 */
    public final void confirm(Component title, Component message, Component confirmText, Runnable onConfirm) {
        openDialog(title, message, confirmText, KineticI18n.translatable("gui.entitycontrol.common.cancel"), onConfirm, () -> {
        });
    }

    /** 在 build 之外刷新（例如子页面返回后）。 */
    public final void refresh() {
        if (isAttached()) rebuild();
    }

    // ------------------------------------------------------------------ 渲染

    @Override
    protected final void renderBackground(@NotNull KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (frame == null) {
            return;
        }
        KineticTheme.Palette palette = KineticTheme.current();
        KineticTheme.canvasBackground(graphics, width(), height());
        KineticTheme.panel(graphics, frame.x(), frame.y(), frame.width(), frame.height());
        graphics.hLine(frame.x() + PAD, frame.right() - PAD - 1, header.bottom() + GAP, palette.border());
        graphics.scrollingTextCentered(headerTitle(), titleRect.x() + titleRect.width() / 2, titleRect.y() + 4,
                titleRect.width(), palette.text(), false);
        for (Section section : sections) {
            KineticLayout.Rect rect = section.rect();
            KineticTheme.panelAlt(graphics, rect.x(), rect.y(), rect.width(), rect.height());
            graphics.scrollingText(section.title(), rect.x() + PAD, rect.y() + PAD, rect.width() - PAD * 2,
                    palette.text(), false);
        }

        renderContent(graphics, mouseX, mouseY, partialTick);

        for (Label label : labels) {
            graphics.scrollingText(label.text(), label.rect().x(), label.rect().y() + (label.rect().height() - 8) / 2,
                    label.rect().width(), label.color(), false);
        }
        for (ScrollForm form : forms) {
            form.render(graphics, mouseX, mouseY);
        }
        for (ScrollArea area : areas) {
            area.scroll().render(graphics, mouseX, mouseY, area.rect().right() - 4, area.rect().y(), 4, area.rect().height(), 16);
        }
    }

    @Override
    protected final void renderForeground(@NotNull KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderOverlay(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected final void renderTooltips(int mouseX, int mouseY) {
        if (contentTooltips(mouseX, mouseY)) {
            return;
        }
        for (ScrollForm form : forms) {
            Label label = form.hovered(mouseX, mouseY);
            if (label != null) {
                showLabelTooltip(label);
                return;
            }
        }
        for (Label label : labels) {
            if (!label.tooltip().isEmpty() && label.rect().contains(mouseX, mouseY)) {
                showLabelTooltip(label);
                return;
            }
        }
    }

    private void showLabelTooltip(Label label) {
        if (label.tooltip().isEmpty()) {
            return;
        }
        List<Component> lines = new ArrayList<>(label.tooltip().size() + 1);
        lines.add(label.text());
        lines.addAll(label.tooltip());
        showTooltip(lines, 260);
    }

    /** 供子类显示多行提示。 */
    public final void tooltipLines(List<Component> lines) {
        showTooltip(lines, 260);
    }

    // ------------------------------------------------------------------ 输入

    @Override
    protected final boolean onMouseClickCapture(MouseInput input) {
        for (ScrollForm form : forms) {
            if (form.area != null && form.scroll.beginDrag(input.x(), input.y(), input.button(),
                    form.area.right() - 4, form.area.y(), 4, form.area.height(), 16, 4)) {
                return true;
            }
        }
        for (ScrollArea area : areas) {
            if (area.scroll().beginDrag(input.x(), input.y(), input.button(), area.rect().right() - 4, area.rect().y(), 4,
                    area.rect().height(), 16, 4)) {
                return true;
            }
        }
        return contentClickCapture(input);
    }

    @Override
    protected final boolean onMouseClick(MouseInput input) {
        return contentClick(input);
    }

    @Override
    protected final boolean onMouseDrag(MouseDragInput input) {
        for (ScrollForm form : forms) {
            if (form.area != null && form.scroll.drag(input.y(), form.area.y(), form.area.height(), 16)) {
                return true;
            }
        }
        for (ScrollArea area : areas) {
            if (area.scroll().drag(input.y(), area.rect().y(), area.rect().height(), 16)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected final boolean onMouseRelease(MouseInput input) {
        boolean released = false;
        for (ScrollForm form : forms) {
            released |= form.scroll.release(input.button());
        }
        for (ScrollArea area : areas) {
            released |= area.scroll().release(input.button());
        }
        return released;
    }

    @Override
    protected final boolean onMouseScroll(ScrollInput input) {
        if (contentScroll(input)) {
            return true;
        }
        for (ScrollForm form : forms) {
            if (form.area != null && form.area.contains(input.x(), input.y()) && form.scroll.canScroll()) {
                return form.scroll.scroll(input.deltaY());
            }
        }
        for (ScrollArea area : areas) {
            if (area.rect().contains(input.x(), input.y()) && area.scroll().canScroll()) {
                return area.scroll().scroll(input.deltaY());
            }
        }
        return false;
    }

    @Override
    protected final boolean onCloseRequested() {
        return onBack();
    }
}
