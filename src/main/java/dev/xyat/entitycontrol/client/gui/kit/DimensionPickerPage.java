package dev.xyat.entitycontrol.client.gui.kit;

import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.layout.KineticLayout;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/** A compact dimension window: plain multi-select rows and a local draft until Apply is pressed. */
public final class DimensionPickerPage extends EcPage {
    private final List<String> dimensions;
    private final Set<String> picked;
    private final Consumer<List<String>> onApply;
    private final TextRowList rows = new TextRowList();
    private String query = "";

    public DimensionPickerPage(Component title, Collection<String> known, Collection<String> selected, Consumer<List<String>> onApply) {
        super(title);
        Set<String> available = new TreeSet<>(known);
        available.addAll(selected);
        dimensions = List.copyOf(available);
        picked = new LinkedHashSet<>(selected);
        this.onApply = onApply;
    }

    @Override
    protected KineticLayout.Rect frameBounds() {
        int w = Math.min(360, width() - OUTER * 2);
        int h = Math.min(240, height() - OUTER * 2);
        return new KineticLayout.Rect((width() - w) / 2, (height() - h) / 2, w, h);
    }

    @Override
    protected List<HeaderAction> headerActions() {
        return List.of(new HeaderAction("apply", KineticI18n.translatable("gui.entitycontrol.common.dimensions.apply"),
                KineticI18n.translatable("tip.entitycontrol.common.dimensions.apply"), () -> {
                    onApply.accept(new ArrayList<>(picked));
                    navigateBack();
                }, null, true, 40));
    }

    @Override
    protected void buildContent(KineticUi ui, KineticLayout.Rect body) {
        var split = takeRow(body);
        textInput(ui, split.first(), query, KineticI18n.translatable("gui.entitycontrol.common.search"),
                KineticI18n.translatable("tip.entitycontrol.common.search"), value -> {
                    query = value;
                    rows.update(visibleRows());
                });
        rows.layout(this, split.second(), visibleRows());
    }

    private List<TextRowList.Row> visibleRows() {
        List<TextRowList.Row> result = new ArrayList<>();
        String search = query.trim().toLowerCase(Locale.ROOT);
        for (String id : dimensions) {
            Component label = dimensionLabel(id);
            String text = label.getString();
            if (search.isEmpty() || KineticSearch.match(text + " " + KineticSearch.pinyin(text), search)) {
                result.add(new TextRowList.Row(id, label));
            }
        }
        return result;
    }

    public static Component dimensionLabel(String id) {
        String path = id.replace(':', '.').replace('/', '.');
        for (String key : List.of("dimension." + path, "generator." + path, "gui.entitycontrol.common.dimension." + path)) {
            if (KineticI18n.hasTranslation(key)) return Component.literal(id + "  ").append(KineticI18n.translatable(key));
        }
        return Component.literal(id);
    }

    @Override
    protected void renderContent(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        rows.render(graphics, mouseX, mouseY, key -> false, picked::contains, key -> false);
    }

    @Override
    protected boolean contentClickCapture(MouseInput input) {
        if (!input.isLeft()) return false;
        TextRowList.Row row = rows.rowAt(input.x(), input.y());
        if (row == null) return false;
        if (!picked.remove(row.key())) picked.add(row.key());
        return true;
    }
}
