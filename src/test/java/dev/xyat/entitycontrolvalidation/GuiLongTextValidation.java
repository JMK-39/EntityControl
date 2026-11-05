package dev.xyat.entitycontrolvalidation;

import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.layout.KineticLayout;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.widget.KineticButton;
import dev.xyat.kineticcore.internal.client.gui.screen.KineticScreenHost;
import dev.xyat.entitycontrol.spawn.client.gui.SpawnControlScreen;
import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import dev.xyat.entitycontrol.breakspawn.client.gui.BlockRuleEditorScreen;
import dev.xyat.entitycontrol.breakspawn.client.gui.BlockEntityPoolScreen;
import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.entitycontrol.spawn.client.gui.SpawnerControlScreen;
import dev.xyat.entitycontrol.spawn.config.SpawnerConfig;
import dev.xyat.entitycontrol.reset.client.gui.EntityResetRuleListScreen;
import dev.xyat.entitycontrol.reset.config.EntityReseConfig;
import dev.xyat.entitycontrol.client.gui.kit.EcPage;
import dev.xyat.entitycontrol.client.gui.kit.DimensionPickerPage;
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

/*** Uses the existing client and independent unsaved samples. Stage 4 clicks local draft controls, never Save. */
public final class GuiLongTextValidation {
    private static final Logger LOG=LoggerFactory.getLogger(GuiLongTextValidation.class);
    private static final String ROOT=System.getProperty("entitycontrol.guiValidation.output","D:/IDEAWork/EntityControl/.gradle/gui-long-text-20261004/");
    private static final String[] NAMES={"attributes-global","attributes-zombie","buffs-zombie","buffs-modified","dummy","curios","components","components-invalid","core-lists","spawn-equipment","damage-distance","config-tooltips","portable-dummy","entity-selector","spawn-main","spawn-switches","break-main","break-context","spawner-main","reset-main","break-attribute-range","modifier-buff-edit","break-global-settings","generic-pick-list"};
    private static final BitSet capturedPages = new BitSet();
    private static final List<String> checks = new ArrayList<>();
    private static final Set<String> capturedCases = new LinkedHashSet<>();
    private static boolean installed,started,screenshot,finished,originalFullscreen;
    private static String originalLanguage;
    private static String failureDetail="";
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
            if(page==11 && mc.screen!=null)hoverConfigRow();
            // Server editor-close packets arrive after the synchronous interaction checks.
            if(page==12 && !(KineticGui.currentPage() instanceof DummyItemPreviewPage))KineticGui.open(new DummyItemPreviewPage());
            if(page>=14 && page<=19 && !screenshot && now>=due && !Stage4.advance()) {
                due=now+250;return;
            }
            if(!screenshot && now>=due) {
                if(page<=3)Stage4.modifierLayout();
                capture("start");layout();screenshot=true;due=now+(phase==4?3400:550);return;
            }
            if(screenshot && now>=due) {
                if(phase==4)capture("scroll");
                nextPage();
            }
        } catch(Throwable error) {
            failures++;
            failureDetail=errorDetail(error,"phase="+phase+" page="+page+" case="+(page>=0 && page<NAMES.length?NAMES[page]:"none")+" "+Stage4.diagnostics());
            LOG.error("ENTITY_GUI_FAIL phase="+phase+" page="+page,error);
            finish();
        }
    }
    private static void nextPhase() {
        if(stressOriginal!=null){Language.inject(stressOriginal);stressOriginal=null;}
        phase++;page=-1;
        // One English 1080p round by default; list phases (0-4) in entitycontrol.guiValidation.phases to run more.
        String selectedPhases=System.getProperty("entitycontrol.guiValidation.phases","1");
        while(phase<5 && !Boolean.getBoolean("entitycontrol.guiValidation.fullHdOnly") && !List.of(selectedPhases.split(",")).contains(String.valueOf(phase)))phase++;
        boolean fullHdOnly=Boolean.getBoolean("entitycontrol.guiValidation.fullHdOnly");
        if(phase>=(fullHdOnly?2:5)){finish();return;}
        var mc=Minecraft.getInstance();
        mc.setScreen(null);
        String lang=(fullHdOnly?phase==1:phase==2 || phase==3)?"zh_cn":"en_us";
        mc.getLanguageManager().setSelected(lang);
        mc.options.languageCode=lang;
        int width=fullHdOnly || phase==1 || phase==3?1920:854,height=fullHdOnly || phase==1 || phase==3?1080:480;
        mc.getWindow().setWindowed(width,height);mc.resizeDisplay();
        reload=mc.reloadResourcePacks();
        LOG.info("ENTITY_GUI_PHASE phase={} language={} requested={}x{} autoScale=true",phase,lang,width,height);
    }
    private static void nextPage() throws Exception {
        page++;
        String selectedPages=System.getProperty("entitycontrol.guiValidation.pages", "");
        while(page<NAMES.length && !selectedPages.isBlank() && !List.of(selectedPages.split(",")).contains(String.valueOf(page)))page++;
        if(page>=NAMES.length){nextPhase();return;}
        Stage4.reset();
        openPage(page);
        screenshot=false;due=System.currentTimeMillis()+1000;
        LOG.info("ENTITY_GUI_OPEN phase={} case={} page={}",phase,NAMES[page],KineticGui.currentPage()!=null?KineticGui.currentPage().getClass().getName():String.valueOf(Minecraft.getInstance().screen));
    }
    private static void openPage(int index) throws Exception {
        var mc=Minecraft.getInstance();
        if(index>=14 && index<=19){Stage4.open(index);return;}
        if(index>=20){openIsolatedEditor(index);return;}
        if(index==12){
            CompletableFuture.runAsync(()->DummyItemValidation.run(mc.getSingleplayerServer()),mc.getSingleplayerServer()).get();
            KineticGui.open(new DummyItemPreviewPage());return;
        }
        if(index==10){KineticGui.open(new DamageDistanceProbePage());return;}
        if(index==13){dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors.openEntitySelector(net.minecraft.network.chat.Component.literal("Entity selector"),java.util.List.of("minecraft:zombie"),ids->{});return;}
        if(index==11){
            var registered=dev.xyat.kineticcore.api.config.client.KTConfigApi.find(
                    dev.xyat.entitycontrol.dummy.config.DummyConfigGui.PAGE_ID).orElseThrow();
            var missing=registered.entries().stream().filter(e->e.isValueEntry()
                    && (e.tooltip()==null || e.tooltip().getString().isBlank())).map(e->e.id()).toList();
            LOG.info("ENTITY_CONFIG_TOOLTIP_REGISTERED values={} missing={} language={}",
                    registered.entries().stream().filter(e->e.isValueEntry()).count(),missing,
                    mc.getLanguageManager().getSelected());
            if(!missing.isEmpty())throw new AssertionError("Registered config rows lack hover help: "+missing);
            for(var configPage:dev.xyat.kineticcore.api.config.client.KTConfigApi.pages()){
                if(!configPage.id().startsWith("entitycontrol:"))continue;
                for(var entry:configPage.entries()){
                    if(!entry.isValueEntry() && entry.type()!=dev.xyat.kineticcore.api.config.client.KTConfigEntry.Type.ACTION)continue;
                    if(entry.tooltip()==null || entry.tooltip().getString().isBlank())
                        throw new AssertionError("Missing menu hover help: "+configPage.id()+"/"+entry.id());
                }
            }
            LOG.info("ENTITY_CONFIG_TOOLTIP_ALL_PAGES_PASS language={}",mc.getLanguageManager().getSelected());
            dev.xyat.kineticcore.api.config.client.KTConfigApi.openOwner("entitycontrol");
            ((dev.xyat.kineticcore.api.client.gui.widget.KineticTextField)field(mc.screen,"searchBox")).setTextValue(
                    dev.xyat.kineticcore.api.text.KineticI18n.translatable("cfg.entitycontrol.dummy.dummy.particleScale").getString());
            return;
        }
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
            //? if >=1.21 {
            /*dummy.onAddedToLevel();
            *///?} else
            dummy.onAddedToWorld();
            // A real dummy gets its configured Curios slots on the server; initialize the client-only fixture here.
            dev.xyat.entitycontrol.dummy.CuriosCompat.initDummySlots(dummy);
            if (Boolean.getBoolean("entitycontrol.guiValidation.defaultSlots") &&
                    dev.xyat.entitycontrol.dummy.CuriosCompat.getSlotCount(dummy) != 16) {
                throw new AssertionError("Fresh dummy configuration must provide exactly 16 Curios slots");
            }
            dummy.getInventory().setItem(4, new ItemStack(Items.EMERALD, 64));
            for (int slot = 0; slot < dev.xyat.entitycontrol.dummy.CuriosCompat.getSlotCount(dummy); slot++) {
                dev.xyat.entitycontrol.dummy.CuriosCompat.setCurioItem(dummy, slot,
                        new ItemStack(slot % 2 == 0 ? Items.DIAMOND : Items.DIAMOND_SWORD, slot % 2 == 0 ? 64 : 1));
            }
            var menu=new dev.xyat.entitycontrol.dummy.DummyMenu(0,mc.player.getInventory(),dummy);
            if(index==4)mc.setScreen((net.minecraft.client.gui.screens.Screen)construct("dev.xyat.kineticcore.internal.client.gui.page.PageContainerScreen",new dev.xyat.entitycontrol.dummy.client.gui.DummyScreen(menu,Component.literal("GUI validation")),mc.player.getInventory(),Component.literal("GUI validation")));
            else KineticGui.open(new dev.xyat.entitycontrol.dummy.client.gui.CuriosScreen(menu));
        }else if(index==9){
            var rule = new dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig.EntityRule();
            var equipment = new dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig.EquipmentSpec();
            equipment.itemId = "minecraft:diamond_sword";
            equipment.count = 3;
            ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
            //? if >=1.21 {
            /*named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal("Native equipment validation"));
            equipment.components = dev.xyat.entitycontrol.breakspawn.data.EquipmentData.format(named);
            *///?} else {
            named.setHoverName(Component.literal("Native equipment validation"));
            equipment.nbt = named.getTag().toString();
            //?}
            rule.equipment.put("mainhand", equipment);
            var editor = new dev.xyat.entitycontrol.breakspawn.client.gui.EquipmentEditorScreen("minecraft:zombie",rule,()->{});
            KineticGui.open(editor);
            ItemStack preview = (ItemStack)invoke(editor,"stack",equipment);
            Stage4.require(preview.getCount()==3 && preview.getHoverName().getString().equals("Native equipment validation"),
                    "equipment preview preserves the configured native stack data and count");
            var icon=(KineticLayout.Rect)((Map<?,?>)field(editor,"icons")).get("mainhand");
            invoke(editor,"contentTooltips",icon.x()+1,icon.y()+1);
            Object overlay=((KineticScreenHost)mc.screen).kineticRuntime().overlays();
            Stage4.require(field(overlay,"tooltip").getClass().getSimpleName().equals("ItemTooltip"),
                    "equipment icon uses Core's native item tooltip");
            checks.add("Native equipment data, count and ItemTooltip preserved");
        }else if(index==8){KineticGui.open(new CoreListsPage());
        }else{
        //? if >=1.21 {
        /*dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors.openNbtEditor(index==6?"[damage=1]":"[invalid=]",text->{try{dev.xyat.entitycontrol.breakspawn.data.EquipmentData.compile("minecraft:diamond_sword",text);return null;}catch(RuntimeException invalid){return String.valueOf(invalid.getMessage());}},value->{});
        *///?} else {
        dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors.openNbtEditor(index==6?"{Damage:1}":"{Damage:}",text->{try{net.minecraft.nbt.TagParser.parseTag(text);return null;}catch(Exception invalid){return String.valueOf(invalid.getMessage());}},value->{});
        //?}
        }
    }
    private static void openIsolatedEditor(int index) throws Exception {
        EcPage editor;
        switch(index) {
            case 20 -> {
                var rule=new BreakSpawnConfig.EntityRule();
                var range=new BreakSpawnConfig.AttributeRange();range.min=20D;range.max=40D;
                String attribute=dev.xyat.kineticcore.api.registry.KineticRegistries.attributes().ids().stream()
                        .map(Object::toString).filter(id->id.endsWith("max_health")).findFirst().orElseThrow();
                rule.attributes.put(attribute,range);
                editor=new dev.xyat.entitycontrol.breakspawn.client.gui.AttributeRangeScreen("minecraft:zombie",rule,()->{});
                Stage4.setField(editor,"selected",attribute);
            }
            case 21 -> {
                var parent=new dev.xyat.entitycontrol.modifier.client.gui.EntityModifierScreen("{}");
                var buff=new dev.xyat.entitycontrol.modifier.config.EntityModifierConfig.PotionBuff();
                buff.chance=0.25D;buff.minLevel=1;buff.maxLevel=3;
                buff.dimensions="minecraft:overworld,minecraft:the_end";
                editor=new dev.xyat.entitycontrol.modifier.client.gui.BuffEditScreen(parent,"minecraft:zombie","minecraft:speed",buff);
            }
            case 22 -> {
                var sample=new BreakSpawnConfig.ConfigRoot();
                sample.global.minSpawnCount=1;sample.global.maxSpawnCount=4;
                sample.global.horizontalRadius=12;sample.global.verticalRadius=6;
                editor=new dev.xyat.entitycontrol.breakspawn.client.gui.GlobalSettingsScreen(sample,()->{});
            }
            case 23 -> {
                var entries=new ArrayList<dev.xyat.entitycontrol.client.gui.kit.PickListPage.Entry>();
                for(int i=0;i<60;i++) entries.add(new dev.xyat.entitycontrol.client.gui.kit.PickListPage.Entry(
                        "validation:long_attribute_identifier_"+i,
                        Component.literal("A long translated attribute name for a bounded selection row "+i)));
                List<String> applied=new ArrayList<>();
                var picker=new dev.xyat.entitycontrol.client.gui.kit.PickListPage(
                        Component.literal("Select attributes — isolated sample"),entries,
                        List.of("validation:long_attribute_identifier_0"),applied::addAll);
                ((Set<String>)field(picker,"picked")).add("validation:long_attribute_identifier_2");
                editor=picker;
            }
            default -> throw new AssertionError("Unknown isolated editor "+index);
        }
        KineticGui.open(editor);
        Stage4.require(editor.width()==640 && editor.height()==360 && editor.frame().x()==0 && editor.frame().y()==0,
                "isolated child editor keeps its full shared canvas and outer frame");
        for(var child:Minecraft.getInstance().screen.children()) {
            if(!(child instanceof dev.xyat.kineticcore.api.client.gui.widget.KineticControl control) || !control.controlVisible())continue;
            Stage4.require(control.controlX()>=4 && control.controlY()>=4
                            && control.controlX()+control.controlWidth()<=636 && control.controlY()+control.controlHeight()<=356,
                    "isolated child editor controls stay inside the four-pixel canvas inset");
        }
        checks.add("Isolated child editor covered: "+editor.getClass().getSimpleName()+"; no live configuration or save request");
    }

    private static final class DummyItemPreviewPage extends KineticPage {
        DummyItemPreviewPage(){super(Component.literal("Portable testing dummy"));}
        @Override protected void build(dev.xyat.kineticcore.api.client.gui.ui.KineticUi ui) { }
        @Override protected void renderForeground(dev.xyat.kineticcore.api.client.gui.render.KineticGraphics g,int mx,int my,float pt) {
            var stack=new ItemStack(dev.xyat.kineticcore.api.registry.KineticRegistries.items().get(
                    dev.xyat.kineticcore.api.resource.KineticResourceIds.of("entitycontrol","dummy")));
            g.item(stack,width()/2-8,height()/2-8);
        }
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
    private static final class DamageDistanceProbePage extends KineticPage {
        private boolean checked;
        DamageDistanceProbePage(){super(Component.literal("Damage distance runtime validation"));}
        @Override protected void build(dev.xyat.kineticcore.api.client.gui.ui.KineticUi ui){}
        @Override protected void renderForeground(dev.xyat.kineticcore.api.client.gui.render.KineticGraphics g,int mx,int my,float pt) {
            if(checked)return;
            checked=true;
            try {
                var method=dev.xyat.entitycontrol.dummy.config.DummyConfigGui.class.getDeclaredMethod("buildClientPage");method.setAccessible(true);
                var config=(dev.xyat.kineticcore.api.config.client.KTConfigPage)method.invoke(null);
                var entry=config.entries().stream().filter(e->e.id().equals("damage_render_distance")).findFirst().orElseThrow();
                if(!entry.defaultValue().equals(64)||entry.minimum().intValue()!=1||entry.maximum().intValue()!=1024)throw new AssertionError("Distance config bounds");
                for(var item:config.entries())if(item.isValueEntry()&&(item.tooltip()==null||item.tooltip().getString().isBlank()))throw new AssertionError("Missing config tooltip: "+item.id());
                var manager=dev.xyat.entitycontrol.dummy.client.DummyTextManager.class;
                var particlesField=manager.getDeclaredField("particles");particlesField.setAccessible(true);
                @SuppressWarnings("unchecked") var particles=(List<Object>)particlesField.get(null);
                var constructor=Class.forName(manager.getName()+"$FloatingText").getDeclaredConstructors()[0];constructor.setAccessible(true);
                var draw=manager.getDeclaredMethod("drawLabels",dev.xyat.kineticcore.api.client.gui.render.KineticGraphics.class,java.util.function.Function.class,net.minecraft.world.phys.Vec3.class,float.class);draw.setAccessible(true);
                var player=Minecraft.getInstance().player;var origin=player.position();
                // Floating numbers are only drawn without cumulative damage; a pack may have it enabled.
                var cumulative=dev.xyat.entitycontrol.dummy.config.DummyClientConfig.accumulateDamage;
                boolean wasCumulative=cumulative.get();
                Object previousDistance=dev.xyat.entitycontrol.dummy.config.DummyClientConfig.damageRenderDistance.get();
                cumulative.set(false);
                try {
                for(int distance:new int[]{1,64,1024,16,64}) {
                    entry.writeSnapshot(distance);config.save();particles.clear();
                    // Just inside each limit: exactly on it, rounding at large world coordinates can put a label outside.
                    for(double blocks:new double[]{0.99,63.99,1023.99,1025}){
                        var point=origin.add(blocks,0,0);particles.add(constructor.newInstance(point,point,"42",0xFFFFFF,false,0f,0f));
                    }
                    int[] projections={0};
                    java.util.function.Function<net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec2> project=world->{projections[0]++;return new net.minecraft.world.phys.Vec2(100+projections[0]*36,100);};
                    draw.invoke(null,g,project,origin,0f);
                    int expected=distance==1024?3:distance==64?2:1;
                    if(projections[0]!=expected)throw new AssertionError("Live distance render: "+distance+" -> "+projections[0]);
                    LOG.info("ENTITY_DAMAGE_RANGE_PASS configured={} visible={} immediate=true",distance,projections[0]);
                }
                } finally {
                    particles.clear();
                    cumulative.set(wasCumulative);
                    entry.writeSnapshot(previousDistance);config.save();
                }
                LOG.info("ENTITY_DAMAGE_CONFIG_PASS default=64 min=1 max=1024 allValueTooltips=true language={}",Minecraft.getInstance().getLanguageManager().getSelected());
            }catch(Throwable error){throw new AssertionError("Damage distance runtime check",error);}
            dev.xyat.entitycontrol.dummy.config.DummyConfigGui.open();
            try{((dev.xyat.kineticcore.api.client.gui.widget.KineticTextField)field(Minecraft.getInstance().screen,"searchBox")).setTextValue("damage_render_distance");}
            catch(Exception error){throw new AssertionError("Distance setting is not searchable",error);}
        }
    }
    private static void hoverConfigRow()throws Exception {
        var mc=Minecraft.getInstance();
        var rows=(Map<?,Integer>)field(mc.screen,"visibleRows");
        for(var row:rows.entrySet()){
            if(!"ENTRY".equals(invoke(row.getKey(),"kind").toString()))continue;
            double x=((Number)invoke(mc.screen,"canvasX")).doubleValue();
            double y=((Number)invoke(mc.screen,"canvasY")).doubleValue();
            double scale=((Number)invoke(mc.screen,"canvasScale")).doubleValue();
            for(String name:List.of("xpos","f_91507_"))try{
                var f=mc.mouseHandler.getClass().getDeclaredField(name);f.setAccessible(true);
                f.setDouble(mc.mouseHandler,(x+45*scale)*mc.getWindow().getWidth()/mc.getWindow().getGuiScaledWidth());break;
            }catch(NoSuchFieldException ignored){}
            for(String name:List.of("ypos","f_91508_"))try{
                var f=mc.mouseHandler.getClass().getDeclaredField(name);f.setAccessible(true);
                f.setDouble(mc.mouseHandler,(y+(row.getValue()+5)*scale)*mc.getWindow().getHeight()/mc.getWindow().getGuiScaledHeight());break;
            }catch(NoSuchFieldException ignored){}
            return;
        }
        throw new AssertionError("Filtered config row is not visible");
    }
    private static void capture(String frame)throws Exception {
        var mc=Minecraft.getInstance();Path path=Path.of(ROOT,String.format("%d-%02d-%s-%s.png",phase,page,NAMES[page],frame));Files.createDirectories(path.getParent());
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
        capturedPages.set(page);
        capturedCases.add(phase+":"+page);
        captures++;LOG.info("ENTITY_GUI_CAPTURE phase={} case={} image={}x{}",phase,NAMES[page],mc.getWindow().getWidth(),mc.getWindow().getHeight());
    }
    /*** Logs controls of the open screen that overlap or sit closer than 2 px (Core layout check). */
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
        String next=System.getProperty("entitycontrol.guiValidation.thenValidation", "").trim();
        try {
            writeResult(next);
        } catch(Exception error) { failures++;LOG.error("ENTITY_GUI_RESULT_FAIL",error); }
        LOG.info("ENTITY_GUI_{} pages={} captures={} failures={} userSettingsRestored=true",failures==0?"PASS":"FAIL",capturedPages.cardinality(),captures,failures);
        if(failures==0 && !next.isBlank())try {
            String type=next.endsWith(".install()")?next.substring(0,next.length()-".install()".length()):next;
            Class.forName(type).getMethod("install").invoke(null);
            LOG.info("ENTITY_GUI_CHAIN_STARTED {}",next);return;
        } catch(Exception error) {
            failures++;LOG.error("ENTITY_GUI_CHAIN_FAIL {}",next,error);
            failureDetail=errorDetail(error,"chain="+next);
            try { writeResult(next); }
            catch(Exception resultError){LOG.error("ENTITY_GUI_RESULT_FAIL",resultError);}
        }
        dev.xyat.kineticcore.api.runtime.KineticClientRuntime.stopClient();
    }

    private static void writeResult(String next)throws java.io.IOException {
        Path directory=Path.of(ROOT).toAbsolutePath();Files.createDirectories(directory);
        Files.writeString(directory.resolve("result.txt"), "ENTITY_GUI_"+(failures==0?"PASS":"FAIL")+"\n"
                +"pages="+capturedPages+"\nphasePages="+capturedCases+"\ncaptures="+captures+"\nfailures="+failures
                +"\nchecks="+checks+"\nnext="+(failures==0?next:"")+"\nfailureDetail="+failureDetail+"\n");
    }

    private static String errorDetail(Throwable error,String context) {
        var trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));
        return context+"\n"+trace;
    }

    /** Real input through the current host; each step waits for a rendered frame before reading menu bounds. */
    private static final class Stage4 {
        private static final List<String> ENTITY_IDS=List.of("minecraft:zombie","minecraft:skeleton","minecraft:creeper",
                "minecraft:spider","minecraft:cave_spider","minecraft:drowned","minecraft:husk","minecraft:stray",
                "minecraft:witch","minecraft:enderman","minecraft:blaze","minecraft:slime","minecraft:magma_cube",
                "minecraft:pig","minecraft:cow","minecraft:sheep","minecraft:chicken","minecraft:rabbit",
                "minecraft:horse","minecraft:iron_golem");
        private static int step;
        private static long selectorWaitStarted,selectorDeadline,lastWaitLog;
        private static KineticPage owner;
        private static BiomeSpawnConfig.GlobalSettings globals;
        private static BreakSpawnConfig.ConfigRoot blocks;
        private static String firstBlock,secondBlock;
        private static String serverSpawnGlobals,serverSpawnProfile,serverBlocks,serverSpawnerRules,serverResetRules;

        static void reset(){step=0;owner=null;globals=null;blocks=null;selectorWaitStarted=selectorDeadline=lastWaitLog=0L;}

        static String diagnostics() {
            var mc=Minecraft.getInstance();var current=KineticGui.currentPage();
            return "stage4Step="+step+" screen="+(mc.screen==null?"null":mc.screen.getClass().getName())
                    +" currentPage="+(current==null?"null":current.getClass().getName())
                    +" itemSearchReady="+dev.xyat.kineticcore.api.client.search.KineticItemSearch.ready()
                    +" selectorWaitMs="+(selectorWaitStarted==0L?0L:System.currentTimeMillis()-selectorWaitStarted);
        }

        static void open(int index)throws Exception {
            // These snapshots detect accidental writes to the live client configuration, too.
            serverSpawnGlobals=BiomeSpawnConfig.GSON.toJson(BiomeSpawnConfig.globals);
            serverSpawnProfile=BiomeSpawnConfig.GSON.toJson(BiomeSpawnConfig.currentProfile);
            serverBlocks=BreakSpawnConfig.GSON.toJson(BreakSpawnConfig.CURRENT);
            serverSpawnerRules=SpawnerConfig.GSON.toJson(SpawnerConfig.SPAWNER_RULES_CACHE);
            serverResetRules=BreakSpawnConfig.GSON.toJson(EntityReseConfig.ENTITY_RULES_CACHE);
            if(index<=15) {
                globals=new BiomeSpawnConfig.GlobalSettings();
                var profile=new BiomeSpawnConfig.ConfigProfile();
                for(String id:ENTITY_IDS) {
                    var node=new BiomeSpawnConfig.EntityNode();node.category="monster";node.enable_control=true;
                    node.biomes.put("minecraft:plains",new BiomeSpawnConfig.SpawnerDataNode());profile.entities.put(id,node);
                }
                owner=new SpawnControlScreen(globals,profile,1);
            } else if(index<=17) {
                blocks=new BreakSpawnConfig.ConfigRoot();
                for(String id:List.of("minecraft:diamond_ore","minecraft:stone")) {
                    var rule=BreakSpawnConfig.createBlockRuleFromDefaults(blocks.global);
                    rule.entityWeights.put("minecraft:zombie",3);rule.entityWeights.put("minecraft:skeleton",1);
                    blocks.blocks.put(id,rule);
                }
                blocks.entities.put("minecraft:zombie",new BreakSpawnConfig.EntityRule());
                blocks.entities.put("minecraft:skeleton",new BreakSpawnConfig.EntityRule());
                firstBlock=blocks.blocks.keySet().iterator().next();secondBlock=blocks.blocks.keySet().stream().skip(1).findFirst().orElseThrow();
                owner=new BlockRuleEditorScreen(blocks);
            } else if(index==18) {
                var snapshot=new SpawnerConfig.SpawnerEditorSnapshot();snapshot.entityIds.addAll(ENTITY_IDS);
                snapshot.data.global.fixedSpawnDelaySeconds=12.5D;
                snapshot.data.global.tuningEnabled=true;
                snapshot.data.entities.put("minecraft:zombie",snapshot.data.global.copy());
                owner=new SpawnerControlScreen(snapshot);
            } else {
                owner=new EntityResetRuleListScreen();
                // Suppress the normal opening read request; no server traffic is necessary for layout validation.
                setField(owner,"syncRequested",true);
                setField(owner,"selectedId","minecraft:zombie");invoke(owner,"loadRule");
            }
            KineticGui.open(owner);
        }

        static boolean advance()throws Exception {
            var mc=Minecraft.getInstance();
            require(mc.getWindow().getWidth()==1920 && mc.getWindow().getHeight()==1080,"stage4 must run at 1920x1080");
            if(page==14) {
                switch(step++) {
                    case 0 -> {
                        require(KineticGui.currentPage()==owner,"spawn main owner");
                        require(!header("save").isEnabled(),"fresh spawn sample is clean");
                        geometry(owner,"cards",KineticLayout.ENTITY_CARD_SIZE);clickEntityCard("minecraft:zombie");return false;
                    }
                    case 1 -> {
                        var node=(BiomeSpawnConfig.EntityNode)invoke(owner,"node");
                        node.dim_blacklist.add("minecraft:overworld");
                        clickPageButton("gui.entitycontrol.spawn.editor.dim_whitelist");return false;
                    }
                    case 2 -> {
                        assertDimensionWindow();clickDimension("minecraft:overworld");return false;
                    }
                    case 3 -> {
                        require(KineticGui.currentPage() instanceof DimensionPickerPage,"multi-select stays open after picking a row");
                        var node=(BiomeSpawnConfig.EntityNode)invoke(owner,"node");
                        require(node.dim_whitelist.isEmpty(),"picker keeps changes local before Apply");
                        capture("dimension-whitelist-window");
                        clickPageButton("gui.entitycontrol.common.dimensions.apply");return false;
                    }
                    case 4 -> {
                        require(KineticGui.currentPage()==owner,"Apply returns to the same parent draft");
                        var node=(BiomeSpawnConfig.EntityNode)invoke(owner,"node");
                        require(node.dim_whitelist.contains("minecraft:overworld") && !node.dim_blacklist.contains("minecraft:overworld"),"dimension lists stay mutually exclusive");
                        require(header("save").isEnabled(),"dimension draft enables Save without sending it");
                        clickPageButton("gui.entitycontrol.spawn.editor.dim_blacklist");return false;
                    }
                    case 5 -> { assertDimensionWindow();clickDimension("minecraft:overworld");return false; }
                    case 6 -> { clickPageButton("gui.entitycontrol.common.back");return false; }
                    default -> {
                        require(KineticGui.currentPage()==owner,"Back cancels the dimension window");
                        var node=(BiomeSpawnConfig.EntityNode)invoke(owner,"node");
                        require(node.dim_whitelist.contains("minecraft:overworld") && !node.dim_blacklist.contains("minecraft:overworld"),"cancelled dimension picks do not change the draft");
                        check("spawn-five-standard-cards-and-dimension-window-draft");return true;
                    }
                }
            }
            if(page==15) {
                switch(step++) {
                    case 0 -> { geometry(owner,"cards",KineticLayout.ENTITY_CARD_SIZE);clickPageButton("gui.entitycontrol.spawn.editor.switches");return false; }
                    case 1 -> {
                        assertSwitchMenu(false,false,true);capture("before-toggle");clickMenu(0);return false;
                    }
                    case 2 -> {
                        require(globals.enable_rule_override && !globals.enable_biome_override && globals.auto_scan,"rules toggle changes only its draft flag");
                        require(header("save").isEnabled(),"spawn toggle enables Save without clicking it");
                        clickPageButton("gui.entitycontrol.spawn.editor.switches");return false;
                    }
                    case 3 -> { assertSwitchMenu(true,false,true);clickMenu(1);return false; }
                    case 4 -> {
                        require(globals.enable_rule_override && globals.enable_biome_override && globals.auto_scan,"biome toggle updates local draft");
                        clickPageButton("gui.entitycontrol.spawn.editor.switches");return false;
                    }
                    case 5 -> { assertSwitchMenu(true,true,true);clickMenu(2);return false; }
                    case 6 -> {
                        require(globals.enable_rule_override && globals.enable_biome_override && !globals.auto_scan,"autoscan only switches true to false; no refresh");
                        clickPageButton("gui.entitycontrol.spawn.editor.switches");return false;
                    }
                    default -> { assertSwitchMenu(true,true,false);check("three-button-toggles-local-picked-save");return true; }
                }
            }
            if(page==16) {
                switch(step) {
                    case 0 -> {
                        require(KineticGui.currentPage()==owner,"block main owner");
                        geometry(owner,"blocks",22);
                        require(header("add").text().getString().equals(Component.translatable("gui.entitycontrol.breakspawn.ui.add_block").getString()),"Add block is a direct header button without a menu arrow");
                        selectorWaitStarted=System.currentTimeMillis();selectorDeadline=selectorWaitStarted+30_000L;
                        clickControl(header("add"),false);step=1;return false;
                    }
                    case 1 -> {
                        if(mc.screen==null || !mc.screen.getClass().getSimpleName().equals("ItemSelectorScreen")) {
                            long now=System.currentTimeMillis();
                            require(now<selectorDeadline,"Timed out after 30s waiting for Add block's asynchronous Core item selector: "+diagnostics());
                            if(lastWaitLog==0L || now-lastWaitLog>=5_000L) {
                                lastWaitLog=now;LOG.info("ENTITY_STAGE4_WAIT_SELECTOR {}",diagnostics());
                            }
                            return false;
                        }
                        var options=(dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors.ItemSelectorOptions)field(mc.screen,"options");
                        require(options.itemResultsOnly() && options.accepts(new ItemStack(Items.STONE)) && !options.accepts(new ItemStack(Items.DIAMOND_SWORD)),"selector accepts blocks only");
                        LOG.info("ENTITY_STAGE4_SELECTOR_READY waitedMs={}",System.currentTimeMillis()-selectorWaitStarted);
                        capture("add-selector");clickPageButton("gui.kineticcore.config.back");step=2;return false;
                    }
                    default -> {
                        require(KineticGui.currentPage()==owner && blocks.blocks.size()==2,"selector Back returns to the same clean block sample");
                        require(!header("save").isEnabled(),"cancelling Add does not dirty sample");
                        check("block-direct-add-selector-back");return true;
                    }
                }
            }
            if(page==18) {
                switch(step++) {
                    case 0 -> {
                        geometry(owner,"cards",KineticLayout.ENTITY_CARD_SIZE);capture("global-fixed-delay");
                        clickEntityCard("minecraft:zombie");return false;
                    }
                    default -> {
                        require("minecraft:zombie".equals(field(owner,"selected")),"Spawner card selects its own local rule");
                        require(!header("save").isEnabled(),"Spawner selection does not dirty its sample");
                        geometry(owner,"cards",KineticLayout.ENTITY_CARD_SIZE);check("spawner-five-standard-columns-global-and-selected-fixed-pairs");return true;
                    }
                }
            }
            if(page==19) {
                require(KineticGui.currentPage()==owner && (boolean)field(owner,"syncRequested") && !(boolean)field(owner,"waitingForSync"),"Reset layout sample opens without a rules request");
                geometry(owner,"cards",KineticLayout.ENTITY_CARD_SIZE);
                require("minecraft:zombie".equals(field(owner,"selectedId")),"Reset form displays the selected entity");
                check("reset-five-standard-columns-selected-form-no-request");return true;
            }
            switch(step++) {
                case 0 -> {
                    geometry(owner,"blocks",22);
                    require(firstBlock.equals(field(owner,"selectedBlockId")),"first block initially selected");
                    rightClickSecond();return false;
                }
                case 1 -> {
                    assertBlockMenu();capture("menu-before-edit");clickMenu(0);return false;
                }
                case 2 -> {
                    require(KineticGui.currentPage() instanceof BlockEntityPoolScreen,"context Edit opens the real pool editor");
                    var pool=KineticGui.currentPage();
                    require(secondBlock.equals(field(pool,"blockId")) && field(pool,"config")==blocks && field(pool,"blockRule")==blocks.blocks.get(secondBlock),"pool belongs to the second right-clicked block");
                    capture("second-block-pool");clickPageButton("gui.entitycontrol.common.back");return false;
                }
                case 3 -> {
                    require(KineticGui.currentPage()==owner && secondBlock.equals(field(owner,"selectedBlockId")),"pool Back returns to the same parent and second selection");
                    require(blocks.blocks.size()==2,"editing and returning did not remove blocks");rightClickSecond();return false;
                }
                case 4 -> { assertBlockMenu();capture("menu-before-delete");clickMenu(1);return false; }
                default -> {
                    require(blocks.blocks.size()==1 && blocks.blocks.containsKey(firstBlock) && !blocks.blocks.containsKey(secondBlock),"Delete removes only the second right-clicked block");
                    require(firstBlock.equals(field(owner,"selectedBlockId")),"Delete selects the remaining first block");
                    require(header("save").isEnabled(),"Delete enables Save without sending it");
                    deleteKey();
                    require(blocks.blocks.isEmpty() && field(owner,"selectedBlockId")==null,"Delete key reuses removal for the selected remaining block");
                    check("block-second-right-click-edit-back-delete-and-delete-key");return true;
                }
            }
        }

        private static void assertSwitchMenu(boolean rules,boolean biomes,boolean autoscan)throws Exception {
            List<?> controls=menuControls();require(controls.size()==3,"three spawn switches");
            boolean[] expected={rules,biomes,autoscan};
            for(int i=0;i<3;i++) {
                var item=(KineticOverlays.MenuItem)invoke(controls.get(i),"item");
                var button=(KineticButton)invoke(controls.get(i),"button");
                require(item.isButton() && !item.isChoice() && item.checked()!=null,"switch explicitly requests a genuine button row");
                require(Boolean.valueOf(expected[i]).equals(item.checked()) && button.isPicked()==expected[i] && !button.isSelected(),"switch retains green toggle state");
                require(!(boolean)invokeStaticTextRow(item),"render path uses button drawing, not selection-row drawing");
            }
        }

        private static Object invokeStaticTextRow(KineticOverlays.MenuItem item)throws Exception {
            Object overlay=overlays(),menu=field(overlay,"contextMenu");
            return invoke(overlay,"textRow",item,menu);
        }

        private static void assertBlockMenu()throws Exception {
            require(secondBlock.equals(field(owner,"selectedBlockId")),"right-click selects second block before constructing actions");
            List<?> controls=menuControls();require(controls.size()==2,"block context contains Edit pool and Delete only");
            String[] keys={"edit_pool","remove_block"};
            for(int i=0;i<2;i++) {
                var item=(KineticOverlays.MenuItem)invoke(controls.get(i),"item");
                require(item.label().getString().equals(Component.translatable("gui.entitycontrol.breakspawn.ui."+keys[i]).getString()) && item.enabled(),"block context action "+keys[i]);
                require(!(boolean)invokeStaticTextRow(item),"block action uses genuine button drawing");
            }
        }

        private static void rightClickSecond()throws Exception {
            var visible=(List<?>)field(owner,"visibleBlocks");require(visible.size()==2 && secondBlock.equals(visible.get(1)),"second rendered block matches expected id");
            var rect=(KineticLayout.Rect)invoke(field(owner,"blocks"),"slot",1);
            clickPage(rect.x()+rect.width()/2.0,rect.y()+rect.height()/2.0,1);
        }

        private static void clickEntityCard(String id)throws Exception {
            Object grid=field(owner,"cards");var keys=(List<?>)field(grid,"keys");int index=keys.indexOf(id);
            require(index>=0,"sample contains entity card "+id);
            var rect=(KineticLayout.Rect)invoke(grid,"card",index);
            clickPage(rect.x()+rect.width()/2.0,rect.y()+rect.height()/2.0,0);
        }

        static void modifierLayout()throws Exception {
            geometry(KineticGui.currentPage(),"cards",KineticLayout.ENTITY_CARD_SIZE);
            checks.add(phase+":"+page+":modifier-five-standard-columns-title-free-contained");
            LOG.info("ENTITY_STAGE4_CHECK phase={} page={} modifier-five-standard-columns-title-free-contained",phase,page);
        }

        private static void assertDimensionWindow()throws Exception {
            require(KineticGui.currentPage() instanceof DimensionPickerPage,"dimension button opens a compact multi-select window");
            var picker=KineticGui.currentPage();var frame=(KineticLayout.Rect)field(picker,"frame");
            require(frame.width()==360 && frame.height()==240 && frame.x()>4 && frame.y()>4,"dimension window is compact and centered");
            var list=field(picker,"rows");var rows=(List<?>)field(list,"rows");
            Object overworld=rows.stream().filter(row->{try{return invoke(row,"key").equals("minecraft:overworld");}catch(Exception error){throw new AssertionError(error);}}).findFirst().orElseThrow();
            var text=(Component)invoke(overworld,"text");
            require(text.getString().contains("minecraft:overworld") && !text.getString().equals("minecraft:overworld"),"dimension ID includes a translated name");
            for(var child:Minecraft.getInstance().screen.children())if(child instanceof KineticButton button)
                require(button.controlWidth()==40,"short dimension window actions remain 40px");
        }

        private static void clickDimension(String id)throws Exception {
            for(var child:Minecraft.getInstance().screen.children()) {
                if(child instanceof dev.xyat.kineticcore.api.client.gui.widget.KineticTextField search) {
                    search.setTextValue(id);break;
                }
            }
            Object list=field(KineticGui.currentPage(),"rows");var rows=(List<?>)field(list,"rows");
            int index=-1;for(int i=0;i<rows.size();i++)if(invoke(rows.get(i),"key").equals(id)){index=i;break;}
            require(index>=0,"dimension window contains "+id);
            var rect=(KineticLayout.Rect)invoke(list,"row",index);
            clickPage(rect.x()+rect.width()/2.0,rect.y()+rect.height()/2.0,0);
        }

        /** Check actual built rectangles, including both ends of every narrowed right-side input pair. */
        private static void geometry(KineticPage target,String gridName,int size)throws Exception {
            Object grid=field(target,gridName);
            var area=(KineticLayout.Rect)field(grid,"area");
            var body=((EcPage)target).body();
            int columns=gridName.equals("blocks")?10:5;
            require(((Number)field(grid,"columns")).intValue()==columns,"grid keeps standard card sizes and the intended number of columns");
            require(area.y()==body.y()+EcPage.PAD+EcPage.H+EcPage.GAP,"left content moves up by the removed title row");
            var sections=(List<?>)field(target,"sections");
            require(!sections.isEmpty() && ((Component)invoke(sections.get(0),"title")).getString().isBlank(),"left count title is removed");
            String method=gridName.equals("blocks")?"slot":"card";
            var first=(KineticLayout.Rect)invoke(grid,method,0);
            var last=(KineticLayout.Rect)invoke(grid,method,columns-1);
            var next=(KineticLayout.Rect)invoke(grid,method,columns);
            require(first.width()==size && last.y()==first.y() && next.x()==first.x() && next.y()>=first.bottom()+2,"standard slots keep two-pixel gaps and wrap after the last column");
            require(last.right()<=area.right()-6,"last column leaves room for its scrollbar (2026-10-22: 6 px, scrollbar closer to the cards)");
            String selectedName="";
            if(target instanceof SpawnControlScreen p) {
                selectedName=(String)invoke(p,"entityName",field(p,"selectedId"));
            } else if(target instanceof SpawnerControlScreen p) {
                String selected=(String)field(p,"selected");
                if(!selected.equals("__global__"))selectedName=((Component)invoke(p,"entityName",selected)).getString();
            } else if(target instanceof EntityResetRuleListScreen p) {
                selectedName=((Component)invoke(p,"entityName",field(p,"selectedId"))).getString();
            } else if(target instanceof dev.xyat.entitycontrol.modifier.client.gui.EntityModifierScreen p) {
                Object selected=field(p,"selectedEntity");
                if(selected!=null)selectedName=(String)invoke(selected,"translatedName");
            } else if(target instanceof BlockRuleEditorScreen p) {
                selectedName=((Component)invoke(p,"blockName",field(p,"selectedBlockId"))).getString();
            }
            for(Object section:sections) {
                var title=(Component)invoke(section,"title");
                require(selectedName.isBlank() || !title.getString().contains(selectedName),"right-side section does not duplicate selected entity/block name");
            }
            List<KineticLayout.Rect> panels=new ArrayList<>();
            for(Object section:sections)panels.add((KineticLayout.Rect)invoke(section,"rect"));
            for(var listener:Minecraft.getInstance().screen.children()) {
                if(!(listener instanceof dev.xyat.kineticcore.api.client.gui.widget.KineticControl control) || !control.controlVisible())continue;
                int x=control.controlX(),y=control.controlY(),right=x+control.controlWidth(),bottom=y+control.controlHeight();
                if(y<body.y())continue;
                var panel=panels.stream().filter(r->r.contains(x,y)).findFirst().orElseThrow(()->new AssertionError("control outside content panels at "+x+","+y));
                require(x>=panel.x()+2 && y>=panel.y()+2 && right<=panel.right()-2 && bottom<=panel.bottom()-2,
                        "content control stays inside its panel: "+x+","+y+".."+right+","+bottom+" panel="+panel);
            }
            LOG.info("ENTITY_STAGE4_GEOMETRY phase={} page={} columns={} card={} grid={} sections={} titleRowRemoved=true controlsContained=true",phase,page,columns,size,area,sections.size());
        }

        private static void setField(Object target,String name,Object value)throws Exception {
            for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try {
                var f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;
            }catch(NoSuchFieldException ignored){}
            throw new NoSuchFieldException(name);
        }

        private static Object overlays(){return ((KineticScreenHost)Minecraft.getInstance().screen).kineticRuntime().overlays();}
        private static List<?> menuControls()throws Exception {
            Object menu=field(overlays(),"contextMenu");require(menu!=null,"actual context menu is open");
            return (List<?>)invoke(menu,"controls");
        }
        private static KineticButton header(String id)throws Exception{return (KineticButton)invoke(owner,"headerButton",id);}
        private static void clickMenu(int index)throws Exception {clickControl((KineticButton)invoke(menuControls().get(index),"button"),true);}
        private static void clickPageButton(String key) {
            String label=Component.translatable(key).getString();
            for(var listener:Minecraft.getInstance().screen.children())if(listener instanceof KineticButton button && (button.text().getString().equals(label) || button.text().getString().equals(label+" ▾"))) {
                if(key.endsWith(".back"))require(button.controlWidth()==40,"Back is 40px");
                clickControl(button,false);return;
            }
            throw new AssertionError("Missing actual button "+key);
        }
        private static void clickControl(KineticButton button,boolean screenCoordinates) {
            require(button!=null && button.isEnabled(),"button is enabled");
            double x=button.controlX()+button.controlWidth()/2.0,y=button.controlY()+button.controlHeight()/2.0;
            if(screenCoordinates)clickScreen(x,y,0);else clickPage(x,y,0);
        }
        private static void clickPage(double x,double y,int button) {
            var host=(KineticScreenHost)Minecraft.getInstance().screen;clickScreen(host.toScreenX(x),host.toScreenY(y),button);
        }
        private static void clickScreen(double x,double y,int button) {
            var screen=Minecraft.getInstance().screen;
            //? if >=26.1 {
            /*var input=new net.minecraft.client.input.MouseButtonEvent(x,y,new net.minecraft.client.input.MouseButtonInfo(button,0));
            screen.mouseClicked(input,false);screen.mouseReleased(input);
            *///?} else {
            screen.mouseClicked(x,y,button);screen.mouseReleased(x,y,button);
            //?}
        }
        private static void deleteKey() {
            //? if >=26.1 {
            /*Minecraft.getInstance().screen.keyPressed(new net.minecraft.client.input.KeyEvent(261,0,0));*/
            //?} else {
            Minecraft.getInstance().screen.keyPressed(261,0,0);
            //?}
        }
        private static void check(String name) {
            require(serverSpawnGlobals.equals(BiomeSpawnConfig.GSON.toJson(BiomeSpawnConfig.globals))
                    && serverSpawnProfile.equals(BiomeSpawnConfig.GSON.toJson(BiomeSpawnConfig.currentProfile))
                    && serverBlocks.equals(BreakSpawnConfig.GSON.toJson(BreakSpawnConfig.CURRENT))
                    && serverSpawnerRules.equals(SpawnerConfig.GSON.toJson(SpawnerConfig.SPAWNER_RULES_CACHE))
                    && serverResetRules.equals(BreakSpawnConfig.GSON.toJson(EntityReseConfig.ENTITY_RULES_CACHE)),"live client configuration is unchanged");
            checks.add(phase+":"+page+":"+name);LOG.info("ENTITY_STAGE4_CHECK phase={} page={} {}",phase,page,name);
        }
        private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    }
    /*** Core list styles in a 2x2 grid, each with its own search: single-select text rows (yellow), multi-select text
     * rows (green), multi-select button rows (green), and an open autocomplete popup. */
    private static final class CoreListsPage extends KineticPage {
        private static final List<String> IDS=List.of("minecraft:plains","minecraft:forest","minecraft:desert","minecraft:a_deliberately_long_biome_identifier_to_check_scrolling","minecraft:taiga","minecraft:swamp","minecraft:jungle","minecraft:badlands","minecraft:savanna","minecraft:snowy_plains","minecraft:meadow","minecraft:cherry_grove");
        private final String[] queries={"","",""};
        CoreListsPage(){super(Component.literal("GUI test only - Core list styles (not part of the mod)"));useCanvas(640,360,4);}
        @Override protected void renderBackground(dev.xyat.kineticcore.api.client.gui.render.KineticGraphics g,int mx,int my,float pt){
            dev.xyat.kineticcore.api.client.gui.theme.KineticTheme.panel(g,0,0,width(),height());
            dev.xyat.kineticcore.api.client.gui.theme.KineticTheme.separator(g,4,24,width()-8);
            g.scrollingText(title(),52,8,width()-56,0xFFFFFF,false);
        }
        private List<String> filtered(int area){
            return IDS.stream().filter(id->dev.xyat.kineticcore.api.client.search.KineticSearch.match(id,queries[area])).toList();
        }
        private void search(dev.xyat.kineticcore.api.client.gui.ui.KineticUi ui,int area,int x,int y,int w){
            ui.textField(x,y,w).value(queries[area]).placeholder(Component.literal("Search")).onChange(value->{if(!value.equals(queries[area])){queries[area]=value;rebuild();}}).build();
        }
        @Override protected void build(dev.xyat.kineticcore.api.client.gui.ui.KineticUi ui) {
            ui.button(4,4,40).text(Component.literal("Back")).onClick(this::close).build();
            int w=(width()-12)/2, h=(height()-28-4-4)/2, x1=4, x2=8+w, y1=28, y2=32+h, listH=h-20;
            search(ui,0,x1,y1,w);
            var single=new ArrayList<dev.xyat.kineticcore.api.client.gui.widget.list.SelectionItem>();
            for(String id:filtered(0))single.add(new dev.xyat.kineticcore.api.client.gui.widget.list.SelectionItem(Component.literal(id),null,null,true,false));
            ui.selectionList(x1,y1+20,w,listH,single).selected(Math.min(1,single.size()-1)).textRows().build();
            search(ui,1,x2,y1,w);
            var multi=new ArrayList<dev.xyat.kineticcore.api.client.gui.widget.list.ToggleItem>();
            for(String id:filtered(1))multi.add(new dev.xyat.kineticcore.api.client.gui.widget.list.ToggleItem(Component.literal(id),null,IDS.indexOf(id)%3==0,true));
            ui.toggleList(x2,y1+20,w,listH,multi).textRows().build();
            search(ui,2,x1,y2,w);
            var buttons=new ArrayList<dev.xyat.kineticcore.api.client.gui.widget.list.ToggleItem>();
            for(String id:filtered(2))buttons.add(new dev.xyat.kineticcore.api.client.gui.widget.list.ToggleItem(Component.literal(id),null,IDS.indexOf(id)%3==0,true));
            ui.toggleList(x1,y2+20,w,listH,buttons).build();
            var field=ui.autoComplete(x2,y2,w,()->IDS.stream().map(dev.xyat.kineticcore.api.client.search.KineticSuggestion::of).toList()).value("minecraft:").build();
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
