package dev.xyat.entitycontrolpreservationvalidation;

import dev.xyat.entitycontrol.dummy.DummyInit;
import dev.xyat.entitycontrol.dummy.DummyMenu;
import dev.xyat.entitycontrol.dummy.client.gui.DummyScreen;
import dev.xyat.entitycontrol.modifier.client.gui.EntityModifierScreen;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Opt-in client fixture: uses unsaved editor drafts and a copied world in the complete installed pack. */
@Mod("entitycontrol_preservation_validation")
public final class GuiPreservationValidation {
    private static final Logger LOG = LoggerFactory.getLogger(GuiPreservationValidation.class);
    private static final String[] CASES = {"entity-models", "effect-icons", "dummy-vanilla-inventory", "armor-effect-icons", "armor-piece-effect-icons", "crafting-vanilla", "furnace-vanilla", "smithing-vanilla", "stonecutter-vanilla", "combat-entity-models", "combat-filtered-model", "mob-effect-icons", "mob-effect-picker", "tacz-vanilla-slots", "recipe-browser-vanilla-slots"};
    private static boolean started, finished, captured, fullscreen;
    private static int phase = -1, page = -1, failures, captures, scale, width, height;
    private static String language;
    private static long due;
    private static CompletableFuture<Void> reload;
    private static boolean waitingCaptured;

    public GuiPreservationValidation() {
        LOG.info("GUI_PRESERVATION_INSTALLED enabled={}", Boolean.getBoolean("entitycontrol.preservationValidation"));
        if (Boolean.getBoolean("entitycontrol.preservationValidation"))
            KineticClientEvents.onTick(KineticClientEvents.TickPhase.END, GuiPreservationValidation::tick);
    }

    private static void tick() {
        if (finished) return;
        try {
            var mc = Minecraft.getInstance();
            if (!started) {
                if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) {
                    if (!waitingCaptured && mc.screen != null && mc.getOverlay() == null) {
                        waitingCaptured = true;
                        Path path = Path.of(System.getProperty("entitycontrol.preservation.output"), "startup-screen.png");
                        Files.createDirectories(path.getParent());
                        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(path); }
                        LOG.info("GUI_PRESERVATION_WAITING screen={}", mc.screen.getClass().getName());
                    }
                    return;
                }
                started = true; language = mc.getLanguageManager().getSelected(); scale = mc.options.guiScale().get();
                width = mc.getWindow().getWidth(); height = mc.getWindow().getHeight(); fullscreen = mc.getWindow().isFullscreen();
                if (fullscreen) mc.getWindow().toggleFullScreen();
                mc.options.guiScale().set(0); nextPhase(); return;
            }
            if (reload != null) {
                if (!reload.isDone() || mc.getOverlay() != null) return;
                reload.join(); reload = null; nextPage(); return;
            }
            if (System.currentTimeMillis() < due) return;
            if (!captured) { capture(); captured = true; due = System.currentTimeMillis() + 600; }
            else nextPage();
        } catch (Throwable error) {
            failures++; LOG.error("GUI_PRESERVATION_FAIL phase=" + phase + " page=" + page, error); finish();
        }
    }

    private static void nextPhase() {
        phase++; page = -1;
        if (phase >= 4) { finish(); return; }
        var mc = Minecraft.getInstance(); mc.setScreen(null);
        String lang = phase >= 2 ? "zh_cn" : "en_us";
        mc.getLanguageManager().setSelected(lang); mc.options.languageCode = lang;
        boolean large = phase % 2 == 1;
        mc.getWindow().setWindowed(large ? 1536 : 854, large ? 864 : 480); mc.resizeDisplay();
        reload = mc.reloadResourcePacks();
        LOG.info("GUI_PRESERVATION_PHASE phase={} language={} large={}", phase, lang, large);
    }

    private static void nextPage() throws Exception {
        page++;
        String selected = System.getProperty("entitycontrol.preservation.cases", "0,1,2,3,4,5,6,7,8");
        while (page < CASES.length && !selected.isBlank() && !List.of(selected.split(",")).contains(String.valueOf(page))) page++;
        if (page >= CASES.length) { nextPhase(); return; }
        var mc = Minecraft.getInstance();
        if (page <= 1) {
            var p = new EntityModifierScreen("{}"); KineticGui.open(p);
            var entities = (List<EntityModifierScreen.EntityGuiInfo>) field(p, "allEntities");
            var zombie = entities.stream().filter(e -> e.id().equals("minecraft:zombie")).findFirst().orElseThrow();
            setField(p, "selectedEntity", zombie); invoke(p, "rebuild");
            if (page == 1) invoke(p, "switchPanel", field(p, "buffPanel"));
        } else if (page == 2) {
            var dummy = DummyInit.DUMMY.get().create(mc.level); dummy.setPos(mc.player.position());
            dummy.getInventory().setItem(4, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_SWORD));
            //? if >=1.21 {
            /*dummy.onAddedToLevel();
            *///?}
            var menu = new DummyMenu(0, mc.player.getInventory(), dummy);
            mc.setScreen((Screen) construct("dev.xyat.kineticcore.internal.client.gui.page.PageContainerScreen", new DummyScreen(menu, Component.literal("Dummy")), mc.player.getInventory(), Component.literal("Dummy")));
        } else if (page <= 4) {
            var config = construct("dev.xyat.kineticarmory.armorsets.data.ArmorDataConfig"); invoke(config, "initNullFields");
            addEffect(config, "PotionEffectData", "potionEffects", "minecraft:speed");
            addEffect(config, "AttackEffectData", "attackEffects", "minecraft:slowness");
            addEffect(config, "EffectImmunityData", "effectImmunities", "minecraft:poison");
            setField(config, "flexiblePieces", true);
            KineticGui.open((KineticPage) construct("dev.xyat.kineticarmory.armorsets.client.gui." + (page == 3 ? "ArmorDetailPage" : "ArmorPieceBonusPage"), config));
        } else if (page <= 8) {
            String name = new String[]{"CRAFTING", "FURNACE", "SMITHING", "STONECUTTER"}[page - 5];
            var type = Enum.valueOf((Class) Class.forName("dev.xyat.contentstudio.recipe.RecipeRegistry$EditorType"), name);
            var menu = (net.minecraft.world.inventory.AbstractContainerMenu) construct("dev.xyat.contentstudio.recipe.UniversalRecipeMenu", 0, mc.player.getInventory(), type, null);
            // Ghost-slot behavior: placing an item copies one without consuming the carried stack.
            var carried = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 8);
            menu.setCarried(carried);
            menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
            if (carried.getCount() != 8 || menu.getSlot(0).getItem().getCount() != 1)
                throw new AssertionError("Recipe ghost-slot behavior changed for " + name);
            menu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
            ((net.minecraft.world.Container) field(menu, "outputContainer")).setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            var p = construct("dev.xyat.contentstudio.recipe.client.gui.RecipePage", menu, Component.literal(name));
            mc.setScreen((Screen) construct("dev.xyat.kineticcore.internal.client.gui.page.PageContainerScreen", p, mc.player.getInventory(), Component.literal(name)));
        } else if (page <= 10) {
            String weights = "minecraft:zombie=25;minecraft:skeleton=25;minecraft:creeper=25;minecraft:spider=25";
            var p = (KineticPage) construct("dev.xyat.combatsystems.worldgeneration.spawner.client.gui.SpawnerEntityWeightScreen",
                    weights, weights, (java.util.function.Consumer<String>) value -> {});
            if (page == 10) {
                setField(p, "selectedId", "minecraft:zombie");
                setField(p, "searchQuery", "minecraft:creeper"); invoke(p, "updateDisplayList");
                if (!"minecraft:creeper".equals(field(p, "selectedId")))
                    throw new AssertionError("Filtered editor retained a hidden target");
            }
            KineticGui.open(p);
        } else if (page <= 12) {
            var draft = new com.google.gson.JsonObject();
            var effects = new com.google.gson.JsonArray();
            if (page == 11) for (String id : List.of("minecraft:speed", "minecraft:slowness", "minecraft:poison")) effects.add(id + ",0");
            draft.add("effects", effects);
            var ref = Class.forName("dev.xyat.mobascension.client.gui.JsonRef").getMethod("root", com.google.gson.JsonObject.class).invoke(null, draft);
            var difficulty = Enum.valueOf((Class) Class.forName("dev.xyat.mobascension.config.MobDifficulty"), "CLASSIC");
            var p = (KineticPage) construct("dev.xyat.mobascension.client.gui.EffectsPage", ref, difficulty, true, (Runnable) () -> {});
            KineticGui.open(p);
            if (page == 12) invoke(p, "add");
        } else if (page == 13) {
            var recordClass = Class.forName("dev.xyat.taczworkshop.data.TaczRecipeRecord");
            var record = recordClass.getMethod("blank", String.class).invoke(null, "custom");
            invoke(record, "setId", "validation:unsaved_gui_draft");
            var ingredient = new com.google.gson.JsonObject(); ingredient.addProperty("item", "minecraft:iron_ingot");
            ((List<Object>) invoke(record, "materials")).add(construct("dev.xyat.taczworkshop.data.TaczMaterial", ingredient, 32));
            ((com.google.gson.JsonObject) invoke(record, "result")).getAsJsonObject("item").addProperty("item", "minecraft:netherite_sword");
            var p = (KineticPage) construct("dev.xyat.taczworkshop.client.gui.TaczRecipeEditorPage", null, record);
            setField(p, "selectedMaterial", 0); KineticGui.open(p);
        } else {
            var p = (KineticPage) construct("dev.xyat.contentstudio.recipe.client.gui.RecipePreviewPage");
            KineticGui.open(p);
            var records = (List<Object>) field(p, "displayRecords"); records.clear();
            for (var item : List.of(net.minecraft.world.item.Items.IRON_SWORD, net.minecraft.world.item.Items.GOLD_BLOCK, net.minecraft.world.item.Items.POTION)) {
                var record = construct("dev.xyat.contentstudio.recipe.RecipeRecord");
                setField(record, "output", new net.minecraft.world.item.ItemStack(item));
                setField(record, "editorType", "CRAFTING"); records.add(record);
            }
        }
        captured = false; due = System.currentTimeMillis() + 1800;
        LOG.info("GUI_PRESERVATION_OPEN phase={} case={}", phase, CASES[page]);
    }

    private static void addEffect(Object config, String type, String list, String id) throws Exception {
        Object effect = construct("dev.xyat.kineticarmory.armorsets.data.ArmorDataConfig$" + type);
        setField(effect, "effectId", id); ((List<Object>) field(config, list)).add(effect);
    }

    private static Object field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) try {
            var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(target);
        } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
    private static void setField(Object target, String name, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    private static boolean compatible(Class<?>[] types, Object[] args) {
        if (types.length != args.length) return false;
        for (int i = 0; i < types.length; i++) if (args[i] != null && !(types[i].isInstance(args[i]) || types[i] == boolean.class && args[i] instanceof Boolean || types[i] == int.class && args[i] instanceof Integer)) return false;
        return true;
    }
    private static Object construct(String name, Object... args) throws Exception {
        for (var c : Class.forName(name).getDeclaredConstructors()) if (compatible(c.getParameterTypes(), args)) { c.setAccessible(true); return c.newInstance(args); }
        throw new NoSuchMethodException(name);
    }
    private static Object invoke(Object target, String name, Object... args) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) for (var m : type.getDeclaredMethods())
            if (m.getName().equals(name) && compatible(m.getParameterTypes(), args)) { m.setAccessible(true); return m.invoke(target, args); }
        throw new NoSuchMethodException(name);
    }

    private static void capture() throws Exception {
        var mc = Minecraft.getInstance();
        Path path = Path.of(System.getProperty("entitycontrol.preservation.output"), phase + "-" + page + "-" + CASES[page] + ".png");
        Files.createDirectories(path.getParent());
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(path); }
        captures++; LOG.info("GUI_PRESERVATION_CAPTURE {}", path.getFileName());
    }

    private static void finish() {
        finished = true; var mc = Minecraft.getInstance();
        if (started) {
            mc.options.guiScale().set(scale); mc.getLanguageManager().setSelected(language); mc.options.languageCode = language;
            mc.setScreen(null); mc.getWindow().setWindowed(width, height);
            if (fullscreen && !mc.getWindow().isFullscreen()) mc.getWindow().toggleFullScreen();
        }
        LOG.info("GUI_PRESERVATION_{} captures={} failures={}", failures == 0 ? "PASS" : "FAIL", captures, failures);
        KineticClientRuntime.stopClient();
    }
}
