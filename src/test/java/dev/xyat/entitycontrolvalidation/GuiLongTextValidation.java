//? if >=1.21 {
/*package dev.xyat.entitycontrolvalidation;

import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.trading.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/^** Uses the existing client and unsaved page drafts. Never clicks or saves editor changes. *^/
public final class GuiLongTextValidation {
    private static final Logger LOG=LoggerFactory.getLogger(GuiLongTextValidation.class);
    private static final String ROOT=System.getProperty("entitycontrol.guiValidation.output","D:/IDEAWork/EntityControl/.gradle/gui-long-text-20261004/");
    private static final String[] NAMES={"attributes-global","attributes-zombie","buffs-zombie","buffs-modified","dummy","curios","components","components-invalid","core-lists"};
    private static boolean installed,started,screenshot,finished,originalFullscreen;
    private static String originalLanguage;
    private static int originalScale,originalWidth,originalHeight,phase=-1,page=-1,captures,failures;
    private static long due;
    private static CompletableFuture<Void> reload;
    private static Language stressOriginal;

    public static void install() { if(installed)return;installed=true;KineticClientEvents.onTick(KineticClientEvents.TickPhase.END,GuiLongTextValidation::tick); }
    private static void tick() {
        if(finished)return;
        try {
            var mc=Minecraft.getInstance();
            if(!started) {
                if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null)return;
                started=true;originalLanguage=mc.getLanguageManager().getSelected();originalScale=mc.options.guiScale().get();
                originalWidth=mc.getWindow().getWidth();originalHeight=mc.getWindow().getHeight();originalFullscreen=mc.getWindow().isFullscreen();
                mc.options.guiScale().set(0);
                if(originalFullscreen)mc.getWindow().toggleFullScreen();
                nextPhase();
                return;
            }
            if(reload!=null) {
                if(!reload.isDone() || mc.getOverlay()!=null)return;
                reload.join();reload=null;
                if(phase==4) {
                    stressOriginal=Language.getInstance();
                    Language.inject(new StressLanguage(stressOriginal));
                }
                nextPage();return;
            }
            long now=System.currentTimeMillis();
            if(!screenshot && now>=due) { capture("start");layout();screenshot=true;due=now+(phase==4?3400:550);return; }
            if(screenshot && now>=due) {
                if(phase==4)capture("scroll");
                nextPage();
            }
        } catch(Throwable error) {
            failures++;LOG.error("ENTITY_GUI_FAIL phase="+phase+" page="+page,error);
            finish();
        }
    }
    private static void nextPhase() {
        if(stressOriginal!=null){Language.inject(stressOriginal);stressOriginal=null;}
        phase++;page=-1;
        if(phase>=5){finish();return;}
        var mc=Minecraft.getInstance();
        mc.setScreen(null);
        String lang=phase==2 || phase==3?"zh_cn":"en_us";
        mc.getLanguageManager().setSelected(lang);
        mc.options.languageCode=lang;
        int width=phase==1 || phase==3?1536:854,height=phase==1 || phase==3?864:480;
        mc.getWindow().setWindowed(width,height);mc.resizeDisplay();
        reload=mc.reloadResourcePacks();
        LOG.info("ENTITY_GUI_PHASE phase={} language={} requested={}x{} autoScale=true",phase,lang,width,height);
    }
    private static void nextPage() throws Exception {
        page++;
        String selectedPages=System.getProperty("entitycontrol.guiValidation.pages", "");
        while(page<NAMES.length && !selectedPages.isBlank() && !List.of(selectedPages.split(",")).contains(String.valueOf(page)))page++;
        if(page>=NAMES.length){nextPhase();return;}
        openPage(page);
        screenshot=false;due=System.currentTimeMillis()+1000;
        LOG.info("ENTITY_GUI_OPEN phase={} case={} page={}",phase,NAMES[page],KineticGui.currentPage()!=null?KineticGui.currentPage().getClass().getName():String.valueOf(Minecraft.getInstance().screen));
    }
    private static void openPage(int index) throws Exception {
        var mc=Minecraft.getInstance();
        if(index<=3) {
            var p=new dev.xyat.entitycontrol.modifier.client.gui.EntityModifierScreen("{}");
            KineticGui.open(p);
            if(index==0)invoke(p,"setGlobal",true);
            if(index!=0){
                var entities=(List<dev.xyat.entitycontrol.modifier.client.gui.EntityModifierScreen.EntityGuiInfo>)field(p,"allEntities");
                var zombie=entities.stream().filter(e->e.id().equals("minecraft:zombie")).findFirst().orElseThrow();
                var f=p.getClass().getDeclaredField("selectedEntity");f.setAccessible(true);f.set(p,zombie);
                if(index==3)p.getLocalData().computeIfAbsent(zombie.id(),k->new dev.xyat.entitycontrol.modifier.config.EntityModifierConfig.EntityEditData()).buffs.put("minecraft:absorption",new dev.xyat.entitycontrol.modifier.config.EntityModifierConfig.PotionBuff());
                invoke(p,"rebuild");
            }
            if(index>=2)invoke(p,"switchPanel",field(p,"buffPanel"));
            else invoke(field(p,"attributePanel"),"restoreSelection","minecraft:generic.max_health");
        }else if(index<=5){
            var dummy=dev.xyat.entitycontrol.dummy.DummyInit.DUMMY.get().create(mc.level);
            dummy.setPos(mc.player.position());
            dummy.onAddedToLevel();
            var menu=new dev.xyat.entitycontrol.dummy.DummyMenu(0,mc.player.getInventory(),dummy);
            if(index==4)mc.setScreen((net.minecraft.client.gui.screens.Screen)construct("dev.xyat.kineticcore.internal.client.gui.page.PageContainerScreen",new dev.xyat.entitycontrol.dummy.client.gui.DummyScreen(menu,Component.literal("GUI validation")),mc.player.getInventory(),Component.literal("GUI validation")));
            else KineticGui.open(new dev.xyat.entitycontrol.dummy.client.gui.CuriosScreen(menu));
        }else if(index==8){KineticGui.open(new CoreListsPage());
        }else dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors.openNbtEditor(index==6?"[damage=1]":"[invalid=]",text->{try{dev.xyat.entitycontrol.breakspawn.data.EquipmentData.compile("minecraft:diamond_sword",text);return null;}catch(RuntimeException invalid){return String.valueOf(invalid.getMessage());}},value->{});
    }
    private static Object field(Object target,String name)throws Exception {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try {
            var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);
        }catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private static Object invoke(Object target,String name,Object...args)throws Exception {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())for(var m:type.getDeclaredMethods()) {
            if(m.getName().equals(name)&&compatible(m.getParameterTypes(),args)) {
                m.setAccessible(true);return m.invoke(target,args);
            }
        }
        throw new NoSuchMethodException(name);
    }
    private static Object construct(String name,Object...args)throws Exception {
        for(var c:Class.forName(name).getDeclaredConstructors())if(compatible(c.getParameterTypes(),args)){c.setAccessible(true);return c.newInstance(args);}
        throw new NoSuchMethodException(name+" constructor");
    }
    private static boolean compatible(Class<?>[]types,Object[]args) {
        if(types.length!=args.length)return false;
        for(int i=0;i<types.length;i++)if(args[i]!=null && !(types[i].isInstance(args[i]) || types[i]==int.class && args[i] instanceof Integer || types[i]==boolean.class && args[i] instanceof Boolean))return false;
        return true;
    }
    private static void capture(String frame)throws Exception {
        var mc=Minecraft.getInstance();Path path=Path.of(ROOT,String.format("%d-%02d-%s-%s.png",phase,page,NAMES[page],frame));Files.createDirectories(path.getParent());
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
        captures++;LOG.info("ENTITY_GUI_CAPTURE phase={} case={} image={}x{}",phase,NAMES[page],mc.getWindow().getWidth(),mc.getWindow().getHeight());
    }
    /^** Logs controls of the open screen that overlap or sit closer than 2 px (Core layout check). *^/
    private static void layout() {
        try {
            for(Object problem:(List<?>)Class.forName("dev.xyat.kineticcore.internal.client.gui.LayoutCheck").getMethod("currentScreenProblems").invoke(null))
                LOG.warn("ENTITY_GUI_LAYOUT phase={} case={} {}",phase,NAMES[page],problem);
            LOG.info("ENTITY_GUI_LAYOUT_CHECKED phase={} case={} controls={}",phase,NAMES[page],Class.forName("dev.xyat.kineticcore.internal.client.gui.LayoutCheck").getField("lastCheckedControls").get(null));
        } catch(ReflectiveOperationException unavailable) { LOG.warn("ENTITY_GUI_LAYOUT unavailable",unavailable); }
    }
    private static void finish() {
        finished=true;
        var mc=Minecraft.getInstance();
        if(stressOriginal!=null){Language.inject(stressOriginal);stressOriginal=null;}
        mc.options.guiScale().set(originalScale);
        mc.getLanguageManager().setSelected(originalLanguage);mc.options.languageCode=originalLanguage;
        mc.setScreen(null);
        mc.getWindow().setWindowed(originalWidth,originalHeight);
        if(originalFullscreen && !mc.getWindow().isFullscreen())mc.getWindow().toggleFullScreen();
        LOG.info("ENTITY_GUI_{} pages={} captures={} failures={} userSettingsRestored=true",failures==0?"PASS":"FAIL",NAMES.length,captures,failures);
        dev.xyat.kineticcore.api.runtime.KineticClientRuntime.stopClient();
    }
    /^** Core list styles: text rows (single-select yellow, multi-select green), button rows with green picks, and an open autocomplete popup. *^/
    private static final class CoreListsPage extends KineticPage {
        CoreListsPage(){super(Component.literal("Core list styles"));}
        @Override protected void build(dev.xyat.kineticcore.api.client.gui.ui.KineticUi ui) {
            int col=(width()-40)/3;
            var ids=List.of("minecraft:plains","minecraft:forest","minecraft:desert","minecraft:a_deliberately_long_biome_identifier_to_check_scrolling","minecraft:taiga","minecraft:swamp","minecraft:jungle","minecraft:badlands");
            var single=new ArrayList<dev.xyat.kineticcore.api.client.gui.widget.list.SelectionItem>();
            for(String id:ids)single.add(new dev.xyat.kineticcore.api.client.gui.widget.list.SelectionItem(Component.literal(id),null,null,true,false));
            ui.selectionList(10,30,col,110,single).selected(1).textRows().build();
            var multi=new ArrayList<dev.xyat.kineticcore.api.client.gui.widget.list.ToggleItem>();
            for(int i=0;i<ids.size();i++)multi.add(new dev.xyat.kineticcore.api.client.gui.widget.list.ToggleItem(Component.literal(ids.get(i)),null,i%3==0,true));
            ui.toggleList(20+col,30,col,110,multi).textRows().build();
            ui.toggleList(30+2*col,30,col,110,multi).build();
            var field=ui.autoComplete(10,160,col,()->ids.stream().map(dev.xyat.kineticcore.api.client.search.KineticSuggestion::of).toList()).value("minecraft:").build();
            focus(field);
        }
    }
    private static final class StressLanguage extends Language {
        private final Language delegate;
        StressLanguage(Language delegate){this.delegate=delegate;}
        @Override public String getOrDefault(String key,String fallback) {
            String text=delegate.getOrDefault(key,fallback);
            return key.startsWith("gui.entitycontrol.") && !key.contains(".value.") && !key.endsWith(".add_mark") && !key.endsWith(".remove_mark")?text+" - deliberately extended translation to verify text stays inside its own region":text;
        }
        @Override public boolean has(String key){return delegate.has(key);}
        @Override public boolean isDefaultRightToLeft(){return delegate.isDefaultRightToLeft();}
        @Override public net.minecraft.util.FormattedCharSequence getVisualOrder(net.minecraft.network.chat.FormattedText text){return delegate.getVisualOrder(text);}
    }
}
*///?}
