package dev.xyat.entitycontrol.dummy.client;

import dev.xyat.entitycontrol.dummy.config.DummyClientConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.render.KineticWorldRender;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.text.KineticI18n;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class DummyTextManager {
    private static final Map<Integer, HudInstance> activeHuds = new HashMap<>();
    private static final List<FloatingText> particles = new ArrayList<>();
    private static final Map<Integer, CumulativeDamageNumber> cumulativeNumbers = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.###");
    private static final DecimalFormat CUMULATIVE_FORMAT = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));

    private static final int PARTICLE_LIFESPAN = 60;
    private static final int PLAYER_DAMAGE_COLOR = 0xFF5555;
    private static final long CUMULATIVE_TIMEOUT_MILLIS = 3000L;
    private static final double DAMAGE_RENDER_DISTANCE_SQR = 4096.0D;

    private static int arcCounter = 0;

    public static void register() {
        KineticClientEvents.onTick(KineticClientEvents.TickPhase.END, DummyTextManager::onClientTick);
        KineticClientEvents.onLevelRender(KineticClientEvents.LevelRenderStage.AFTER_PARTICLES, DummyTextManager::onRenderLevel);
        //? if >=26.1
        /*KineticClientEvents.onHudRender(KineticClientEvents.HudStage.HOTBAR, DummyTextManager::onRenderHud);*/
    }

    public static void handlePacket(int entityId, Component source, Component type, float total, float dps, float avgDps, int hits, float currentDmg, boolean isDummy, int minionOwnerId) {
        if (isDummy) {
            updateHud(entityId, source, type, total, dps, avgDps, hits);
            // The training dummy already displays accumulated damage in its dedicated HUD.
            return;
        }

        if (!DummyClientConfig.showDamageParticles.get() || currentDmg <= 0f) {
            return;
        }

        boolean minion = minionOwnerId != -1;
        int color;
        if (minion) {
            Player localPlayer = KineticClientRuntime.localPlayer();
            if (localPlayer == null || localPlayer.getId() != minionOwnerId || !DummyClientConfig.showMinionDamage.get()) {
                return;
            }
            color = DummyClientConfig.colorMinion.get();
        } else {
            color = PLAYER_DAMAGE_COLOR;
        }

        if (DummyClientConfig.accumulateDamage.get()) {
            updateCumulativeNumber(entityId, currentDmg, color, minion);
        } else {
            spawnDamageNumber(entityId, currentDmg, color, minion);
        }
    }

    private static void updateHud(int entityId, Component source, Component type, float total, float dps, float avgDps, int hits) {
        HudInstance hud = activeHuds.computeIfAbsent(entityId, key -> new HudInstance());
        hud.update(source, type, total, dps, avgDps, hits);
    }

    private static void spawnDamageNumber(int entityId, float amount, int color, boolean minion) {
        var level = KineticClientRuntime.currentLevel();
        Entity entity = level == null ? null : level.getEntity(entityId);
        if (entity == null) return;

        Vec3 origin = entity.position().add(0, entity.getBbHeight() * 0.8, 0);

        int steps = 10;
        float t = (arcCounter % steps) / (float) (steps - 1);
        double angle = Math.toRadians(190 + t * 160);

        float baseSpeed = 100.0f;
        float speedJitter = 1.0f + (RANDOM.nextFloat() - 0.5f) * 0.2f;
        float finalSpeed = baseSpeed * speedJitter;

        float vx = (float) (Math.cos(angle) * finalSpeed);
        float vy = (float) (Math.sin(angle) * finalSpeed);

        arcCounter++;

        particles.add(new FloatingText(origin, DECIMAL_FORMAT.format(amount), color, minion, vx, vy));
    }

    private static void updateCumulativeNumber(int entityId, float amount, int color, boolean minion) {
        var level = KineticClientRuntime.currentLevel();
        Entity entity = level == null ? null : level.getEntity(entityId);
        if (entity == null || !entity.isAlive()) {
            return;
        }

        long now = System.currentTimeMillis();
        CumulativeDamageNumber number = cumulativeNumbers.computeIfAbsent(entityId, key -> new CumulativeDamageNumber());
        number.update(amount, color, minion, now);
    }

    public static void clearDamageParticles() {
        particles.removeIf(particle -> !particle.minion);
        cumulativeNumbers.values().forEach(CumulativeDamageNumber::clearDirectDamage);
        cumulativeNumbers.values().removeIf(CumulativeDamageNumber::isEmpty);
    }

    public static void clearMinionDamageParticles() {
        particles.removeIf(particle -> particle.minion);
        cumulativeNumbers.values().forEach(CumulativeDamageNumber::clearMinionDamage);
        cumulativeNumbers.values().removeIf(CumulativeDamageNumber::isEmpty);
    }

    public static void clearAllDamageNumbers() {
        particles.clear();
        cumulativeNumbers.clear();
    }

    private static void onClientTick() {
        if (KineticClientRuntime.paused()) return;

        Iterator<FloatingText> iterator = particles.iterator();
        while (iterator.hasNext()) {
            FloatingText particle = iterator.next();
            particle.tick();
            if (particle.isDead()) iterator.remove();
        }

        var level = KineticClientRuntime.currentLevel();
        long now = System.currentTimeMillis();
        cumulativeNumbers.entrySet().removeIf(entry -> {
            Entity entity = level == null ? null : level.getEntity(entry.getKey());
            return entity == null || !entity.isAlive() || entry.getValue().isExpired(now);
        });
        activeHuds.entrySet().removeIf(entry -> now - entry.getValue().lastUpdate > 5000L);
    }

    //? if >=26.1 {
    /*// 26.1 draws GUI elements only in the HUD pass: the level pass keeps this frame's camera, and the HUD pass draws the
    // labels at the projected positions, as the level-render overlay does on older versions.
    private static org.joml.Matrix4f frameView;
    private static org.joml.Matrix4f frameProjection;
    private static Vec3 frameCamera;
    private static float framePartialTick;

    private static void onRenderLevel(KineticClientEvents.LevelRenderContext event) {
        frameView = new org.joml.Matrix4f(event.poseStack().last().pose());
        frameProjection = new org.joml.Matrix4f(event.projectionMatrix());
        frameCamera = event.camera().getPosition();
        framePartialTick = event.partialTick();
    }

    private static void onRenderHud(KineticGraphics g, float partialTick) {
        if (frameView == null) return;
        int width = KineticClientRuntime.guiScaledWidth();
        int height = KineticClientRuntime.guiScaledHeight();
        drawLabels(g, world -> {
            org.joml.Vector4f position = new org.joml.Vector4f((float) (world.x - frameCamera.x), (float) (world.y - frameCamera.y),
                    (float) (world.z - frameCamera.z), 1.0F);
            frameView.transform(position);
            frameProjection.transform(position);
            if (position.w() <= 0.0F) return null;
            return new Vec2((position.x() / position.w() + 1.0F) * 0.5F * width, (1.0F - position.y() / position.w()) * 0.5F * height);
        }, frameCamera, framePartialTick);
    }
    *///?} else {
    private static void onRenderLevel(KineticClientEvents.LevelRenderContext event) {
        try (KineticWorldRender.ScreenOverlay overlay = KineticWorldRender.beginScreenOverlay(event)) {
            drawLabels(overlay.graphics(), overlay::project, event.camera().getPosition(), event.partialTick());
        }
    }
    //?}

    private static void drawLabels(KineticGraphics g, java.util.function.Function<Vec3, Vec2> project, Vec3 camPos, float partialTick) {
        var level = KineticClientRuntime.currentLevel();
        Player player = KineticClientRuntime.localPlayer();
        if (level == null || player == null) return;

        boolean cumulativeMode = DummyClientConfig.accumulateDamage.get();

        {

            for (Map.Entry<Integer, HudInstance> entry : activeHuds.entrySet()) {
                Entity entity = level.getEntity(entry.getKey());
                if (entity != null && entity.isAlive()) {
                    Vec3 origin = entity.position().add(0, entity.getBbHeight() + DummyClientConfig.overheadOffset.get(), 0);

                    ClipContext context = new ClipContext(camPos, origin, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player);
                    HitResult hit = level.clip(context);

                    if (hit.getType() != HitResult.Type.MISS) {
                        continue;
                    }

                    Vec2 position = project.apply(origin);
                    if (position != null) {
                        entry.getValue().render2D(g, position.x, position.y);
                    }
                }
            }

            if (cumulativeMode) {
                for (Map.Entry<Integer, CumulativeDamageNumber> entry : cumulativeNumbers.entrySet()) {
                    Entity entity = level.getEntity(entry.getKey());
                    if (entity == null || !entity.isAlive() || player.distanceToSqr(entity) > DAMAGE_RENDER_DISTANCE_SQR) {
                        continue;
                    }

                    HudInstance hud = activeHuds.get(entry.getKey());
                    double worldOffset = hud == null ? 0.5D : DummyClientConfig.overheadOffset.get();
                    Vec3 origin = entity.getPosition(partialTick).add(0, entity.getBbHeight() + worldOffset, 0);
                    Vec2 position = project.apply(origin);
                    if (position == null) {
                        continue;
                    }

                    float verticalOffset = -12.0F;
                    if (hud != null) {
                        verticalOffset -= hud.getRenderedHeight();
                    }
                    entry.getValue().render2D(g, position.x, position.y + verticalOffset);
                }
            } else {
                for (FloatingText particle : particles) {
                    Vec2 position = project.apply(particle.origin3d);
                    if (position != null) {
                        particle.render2D(g, position.x, position.y, partialTick);
                    }
                }
            }
        }
    }

    private static class HudInstance {
        private final List<Component> lines = new ArrayList<>();
        private long lastUpdate;

        void update(Component source, Component type, float total, float dps, float avgDps, int hits) {
            this.lastUpdate = System.currentTimeMillis();
            lines.clear();
            if (DummyClientConfig.showOverheadSource.get()) {
                int configColor = DummyClientConfig.colorOverheadSource.get();
                MutableComponent sourceName = source.copy();
                lines.add(KineticI18n.translatable("gui.entitycontrol.dummy.dummy.source", sourceName).withStyle(style -> style.withColor(configColor)));
            }
            if (DummyClientConfig.showOverheadType.get()) {
                int configColor = DummyClientConfig.colorOverheadType.get();
                MutableComponent typeName = type.copy();
                lines.add(KineticI18n.translatable("gui.entitycontrol.dummy.dummy.type", typeName).withStyle(style -> style.withColor(configColor)));
            }
            lines.add(KineticI18n.translatable("gui.entitycontrol.dummy.dummy.stats", DECIMAL_FORMAT.format(total), hits).withStyle(style -> style.withColor(DummyClientConfig.colorOverheadStats.get())));

            if (DummyClientConfig.showOverheadAvgDps.get()) {
                lines.add(KineticI18n.translatable("gui.entitycontrol.dummy.dummy.dps_with_avg", DECIMAL_FORMAT.format(dps), DECIMAL_FORMAT.format(avgDps)).withStyle(style -> style.withColor(DummyClientConfig.colorOverheadDps.get())));
            } else {
                lines.add(KineticI18n.translatable("gui.entitycontrol.dummy.dummy.dps", DECIMAL_FORMAT.format(dps)).withStyle(style -> style.withColor(DummyClientConfig.colorOverheadDps.get())));
            }
        }

        float getRenderedHeight() {
            return lines.size() * 10.0F * DummyClientConfig.overheadScale.get().floatValue()
                    * DummyClientConfig.damageTextScale.get().floatValue() * 0.8F;
        }

        void render2D(KineticGraphics g, float screenX, float screenY) {
            g.push();

            g.translate(screenX, screenY);

            float scale = DummyClientConfig.overheadScale.get().floatValue()
                    * DummyClientConfig.damageTextScale.get().floatValue() * 0.8f;
            g.scale(scale, scale);

            int yOffset = -(lines.size() * 10);

            for (Component line : lines) {
                g.push();
                g.translate(-KineticText.width(line) / 2.0f, yOffset);
                g.text(line, 0, 0, 0xFFFFFF, false);
                g.pop();
                yOffset += 10;
            }

            g.pop();
        }
    }

    private static class CumulativeDamageNumber {
        private float directDamage;
        private float minionDamage;
        private int directColor;
        private int minionColor;
        private boolean latestDamageWasMinion;
        private long lastUpdate;
        private String displayText = "0";
        private int displayColor = 0xFFFFFFFF;

        void update(float amount, int color, boolean minion, long now) {
            if (now - lastUpdate >= CUMULATIVE_TIMEOUT_MILLIS) {
                directDamage = 0.0F;
                minionDamage = 0.0F;
            }

            if (minion) {
                minionDamage += amount;
                minionColor = color;
            } else {
                directDamage += amount;
                directColor = color;
            }

            latestDamageWasMinion = minion;
            lastUpdate = now;
            refreshDisplay();
        }

        void clearDirectDamage() {
            directDamage = 0.0F;
            if (!latestDamageWasMinion && minionDamage > 0.0F) {
                latestDamageWasMinion = true;
            }
            refreshDisplay();
        }

        void clearMinionDamage() {
            minionDamage = 0.0F;
            if (latestDamageWasMinion && directDamage > 0.0F) {
                latestDamageWasMinion = false;
            }
            refreshDisplay();
        }

        boolean isExpired(long now) {
            return now - lastUpdate >= CUMULATIVE_TIMEOUT_MILLIS;
        }

        boolean isEmpty() {
            return directDamage <= 0.0F && minionDamage <= 0.0F;
        }

        private void refreshDisplay() {
            float amount = directDamage + minionDamage;
            displayText = CUMULATIVE_FORMAT.format(amount);
            int configuredColor = latestDamageWasMinion ? minionColor : directColor;
            displayColor = 0xFF000000 | (configuredColor & 0x00FFFFFF);
        }

        void render2D(KineticGraphics g, float screenX, float screenY) {
            g.push();
            g.translate(screenX, screenY);

            float scale = DummyClientConfig.particleScale.get().floatValue();
            g.scale(scale, scale);

            float x = -KineticText.width(displayText) / 2.0F;
            int shadowColor = 0xA0000000 | ((displayColor & 0x00FCFCFC) >> 2);
            g.translate(x + 0.5F, 0.5F);
            g.text(displayText, 0, 0, shadowColor, false);
            g.translate(-0.5F, -0.5F);
            g.text(displayText, 0, 0, displayColor, false);
            g.pop();
        }
    }

    private static class FloatingText {
        private final Vec3 origin3d;
        private float offsetX;
        private float offsetY;
        private float vx;
        private float vy;
        private final String text;
        private final int color;
        private int age = 0;
        private final boolean minion;

        FloatingText(Vec3 origin, String text, int color, boolean minion, float initVx, float initVy) {
            this.origin3d = origin;
            this.text = text;
            this.color = color;
            this.minion = minion;

            float spread = Math.max(0.1f, DummyClientConfig.particleSpread.get().floatValue());

            this.offsetX = (float) ((RANDOM.nextDouble() - 0.5) * 4 * spread);
            this.offsetY = (float) ((RANDOM.nextDouble() - 0.5) * 4 * spread);

            this.vx = initVx * spread;
            this.vy = initVy * spread;
        }

        void tick() {
            age++;
            offsetX += vx;
            offsetY += vy;

            vx *= 0.8f;
            vy *= 0.8f;
        }

        boolean isDead() {
            return age > PARTICLE_LIFESPAN;
        }

        void render2D(KineticGraphics g, float screenX, float screenY, float partialTick) {
            float currentAge = age + partialTick;
            if (currentAge > PARTICLE_LIFESPAN) return;

            float alpha = 1.0f;
            if (currentAge > 40.0f) {
                alpha = 1.0f - (currentAge - 40.0f) / (PARTICLE_LIFESPAN - 40.0f);
            }
            if (alpha < 0.05f) return;

            int alphaColor = (int) (alpha * 255) << 24;
            int rgb = color & 0x00FFFFFF;
            int finalColor = alphaColor | rgb;

            float currentOffsetX = offsetX + vx * partialTick;
            float currentOffsetY = offsetY + vy * partialTick;

            g.push();
            g.translate(screenX + currentOffsetX, screenY + currentOffsetY);

            float scale = DummyClientConfig.particleScale.get().floatValue();
            g.scale(scale, scale);

            g.translate(-KineticText.width(text) / 2f, 0);
            g.text(text, 0, 0, finalColor, true);

            g.pop();
        }
    }
}
