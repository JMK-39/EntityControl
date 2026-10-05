package dev.xyat.entitycontrol.client.gui.kit;

import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.layout.KineticLayout;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.KineticButton;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** 通用多选列表：搜索（支持拼音）+ 勾选，顶栏“添加 (n)”确认。 */
public final class PickListPage extends EcPage {
    /** 一个候选项：写入的值 + 显示名称。 */
    public record Entry(String value, Component name) {
    }

    private final List<Entry> entries;
    private final Set<String> excluded;
    private final Consumer<List<String>> onPick;
    private final Set<String> picked = new LinkedHashSet<>();
    private final TextRowList list = new TextRowList();
    private String query = "";

    public PickListPage(Component title, List<Entry> entries, Collection<String> excluded, Consumer<List<String>> onPick) {
        super(title);
        this.entries = List.copyOf(entries);
        this.excluded = Set.copyOf(excluded);
        this.onPick = onPick;
    }

    @Override
    protected List<HeaderAction> headerActions() {
        return List.of(HeaderAction.button("confirm", confirmText(),
                KineticI18n.translatable("tip.entitycontrol.common.pick.confirm"), () -> {
                    onPick.accept(new ArrayList<>(picked));
                    navigateBack();
                }).enabled(!picked.isEmpty()));
    }

    private Component confirmText() {
        return KineticI18n.translatable("gui.entitycontrol.common.pick.confirm", picked.size());
    }

    private List<TextRowList.Row> rows() {
        List<TextRowList.Row> rows = new ArrayList<>();
        for (Entry entry : entries) {
            if (excluded.contains(entry.value())) continue;
            String name = entry.name().getString();
            if (!query.isBlank() && !KineticSearch.match(entry.value() + " " + name + " " + KineticSearch.pinyin(name), query)) {
                continue;
            }
            Component text = name.isBlank() || name.equals(entry.value()) ? Component.literal(entry.value())
                    : entry.name().copy().append("  ").append(Component.literal(entry.value()).withStyle(ChatFormatting.AQUA));
            rows.add(new TextRowList.Row(entry.value(), text));
        }
        return rows;
    }

    @Override
    protected void buildContent(KineticUi ui, KineticLayout.Rect body) {
        KineticLayout.Split split = takeRow(body);
        KineticLayout.Rect search = split.first();
        textInput(ui, new KineticLayout.Rect(search.x(), search.y(), Math.min(200, search.width()), H), query,
                KineticI18n.translatable("gui.entitycontrol.common.search"),
                KineticI18n.translatable("tip.entitycontrol.common.search"), value -> {
                    query = value;
                    list.update(rows());
                });
        list.layout(this, split.second(), rows());
    }

    @Override
    protected void renderContent(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Multi-select: picked rows are green; yellow stays the current choice.
        list.render(graphics, mouseX, mouseY, key -> false, picked::contains, key -> false);
    }

    @Override
    protected boolean contentClickCapture(MouseInput input) {
        TextRowList.Row row = list.rowAt(input.x(), input.y());
        if (row == null) return false;
        if (!picked.remove(row.key())) picked.add(row.key());
        KineticButton confirm = headerButton("confirm");
        if (confirm != null) {
            confirm.setText(confirmText());
            confirm.setEnabled(!picked.isEmpty());
        }
        return true;
    }
}
