package dev.xyat.entitycontrol.dummy.client;

import dev.xyat.entitycontrol.dummy.util.ColorText;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import dev.xyat.entitycontrol.dummy.config.DummyClientConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import org.joml.Matrix4f;
import org.joml.Vector4f;

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

    private static void onRenderLevel(KineticClientEvents.LevelRenderContext event) {
            PoseStack poseStack = event.poseStack();
            Vec3 camPos = event.camera().getPosition();

            var level = KineticClientRuntime.currentLevel();
            Player player = KineticClientRuntime.localPlayer();
            if (level == null || player == null) return;

            int screenWidth = KineticClientRuntime.guiScaledWidth();
            int screenHeight = KineticClientRuntime.guiScaledHeight();

            MultiBufferSource.BufferSource immediate = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
            boolean cumulativeMode = DummyClientConfig.accumulateDamage.get();

            Matrix4f viewMatrix = poseStack.last().pose();
            Matrix4f projMatrix = event.projectionMatrix();

            RenderSystem.backupProjectionMatrix();
            Matrix4f ortho = new Matrix4f().setOrtho(0, screenWidth, screenHeight, 0, -1000, 1000);
            RenderSystem.setProjectionMatrix(ortho, RenderSystem.getVertexSorting());

            PoseStack modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.pushPose();
            modelViewStack.setIdentity();
            RenderSystem.applyModelViewMatrix();

            PoseStack pose = new PoseStack();

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();

            for (Map.Entry<Integer, HudInstance> entry : activeHuds.entrySet()) {
                Entity entity = level.getEntity(entry.getKey());
                if (entity != null && entity.isAlive()) {
                    Vec3 origin = entity.position().add(0, entity.getBbHeight() + DummyClientConfig.overheadOffset.get(), 0);

                    ClipContext context = new ClipContext(camPos, origin, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player);
                    HitResult hit = level.clip(context);

                    if (hit.getType() != HitResult.Type.MISS) {
                        continue;
                    }

                    ScreenPosition position = projectPosition(origin, camPos, viewMatrix, projMatrix, screenWidth, screenHeight);
                    if (position != null) {
                        entry.getValue().render2D(pose, position.x(), position.y(), immediate);
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
                    Vec3 origin = entity.getPosition(event.partialTick()).add(0, entity.getBbHeight() + worldOffset, 0);
                    ScreenPosition position = projectPosition(origin, camPos, viewMatrix, projMatrix, screenWidth, screenHeight);
                    if (position == null) {
                        continue;
                    }

                    float verticalOffset = -12.0F;
                    if (hud != null) {
                        verticalOffset -= hud.getRenderedHeight();
                    }
                    entry.getValue().render2D(pose, position.x(), position.y() + verticalOffset, immediate);
                }
            } else {
                for (FloatingText particle : particles) {
                    ScreenPosition position = projectPosition(particle.origin3d, camPos, viewMatrix, projMatrix, screenWidth, screenHeight);
                    if (position != null) {
                        particle.render2D(pose, position.x(), position.y(), immediate, event.partialTick());
                    }
                }
            }

            immediate.endBatch();

            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();

            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
    }

    private static ScreenPosition projectPosition(Vec3 origin, Vec3 camPos, Matrix4f viewMatrix, Matrix4f projMatrix, int screenWidth, int screenHeight) {
        Vector4f position = new Vector4f(
                (float) (origin.x - camPos.x),
                (float) (origin.y - camPos.y),
                (float) (origin.z - camPos.z),
                1.0F
        );
        viewMatrix.transform(position);
        projMatrix.transform(position);

        if (position.w() <= 0.0F) {
            return null;
        }

        float normalizedX = position.x() / position.w();
        float normalizedY = position.y() / position.w();
        float screenX = (normalizedX + 1.0F) * 0.5F * screenWidth;
        float screenY = (1.0F - normalizedY) * 0.5F * screenHeight;
        return new ScreenPosition(screenX, screenY);
    }

    private record ScreenPosition(float x, float y) {
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
                lines.add(ColorText.translatable("gui.entitycontrol.dummy.dummy.source", sourceName).withStyle(style -> style.withColor(configColor)));
            }
            if (DummyClientConfig.showOverheadType.get()) {
                int configColor = DummyClientConfig.colorOverheadType.get();
                MutableComponent typeName = type.copy();
                lines.add(ColorText.translatable("gui.entitycontrol.dummy.dummy.type", typeName).withStyle(style -> style.withColor(configColor)));
            }
            lines.add(ColorText.translatable("gui.entitycontrol.dummy.dummy.stats", DECIMAL_FORMAT.format(total), hits).withStyle(style -> style.withColor(DummyClientConfig.colorOverheadStats.get())));

            if (DummyClientConfig.showOverheadAvgDps.get()) {
                lines.add(ColorText.translatable("gui.entitycontrol.dummy.dummy.dps_with_avg", DECIMAL_FORMAT.format(dps), DECIMAL_FORMAT.format(avgDps)).withStyle(style -> style.withColor(DummyClientConfig.colorOverheadDps.get())));
            } else {
                lines.add(ColorText.translatable("gui.entitycontrol.dummy.dummy.dps", DECIMAL_FORMAT.format(dps)).withStyle(style -> style.withColor(DummyClientConfig.colorOverheadDps.get())));
            }
        }

        float getRenderedHeight() {
            return lines.size() * 10.0F * DummyClientConfig.overheadScale.get().floatValue() * 0.8F;
        }

        void render2D(PoseStack pose, float screenX, float screenY, MultiBufferSource buffer) {
            Font font = KineticClientRuntime.font();
            pose.pushPose();

            pose.translate(screenX, screenY, 0);

            float scale = DummyClientConfig.overheadScale.get().floatValue() * 0.8f;
            pose.scale(scale, scale, 1.0f);

            Matrix4f matrix = pose.last().pose();
            int yOffset = -(lines.size() * 10);
            int bgColor = 0;

            for (Component line : lines) {
                float xOffset = -font.width(line) / 2.0f;
                font.drawInBatch(line, xOffset, yOffset, 0xFFFFFF, false, matrix, buffer, Font.DisplayMode.NORMAL, bgColor, 15728880);
                yOffset += 10;
            }

            pose.popPose();
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

        void render2D(PoseStack pose, float screenX, float screenY, MultiBufferSource buffer) {
            Font font = KineticClientRuntime.font();
            pose.pushPose();
            pose.translate(screenX, screenY, 0);

            float scale = DummyClientConfig.particleScale.get().floatValue();
            pose.scale(scale, scale, 1.0F);

            float x = -font.width(displayText) / 2.0F;
            Matrix4f matrix = pose.last().pose();
            int shadowColor = 0xA0000000 | ((displayColor & 0x00FCFCFC) >> 2);
            font.drawInBatch(displayText, x + 0.5F, 0.5F, shadowColor, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, 15728880);
            font.drawInBatch(displayText, x, 0, displayColor, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, 15728880);
            pose.popPose();
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

        void render2D(PoseStack pose, float screenX, float screenY, MultiBufferSource buffer, float partialTick) {
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

            pose.pushPose();
            pose.translate(screenX + currentOffsetX, screenY + currentOffsetY, 0);

            float scale = DummyClientConfig.particleScale.get().floatValue();
            pose.scale(scale, scale, 1.0f);

            Font font = KineticClientRuntime.font();
            float x = -font.width(text) / 2f;
            Matrix4f matrix = pose.last().pose();

            font.drawInBatch(
                    text,
                    x,
                    0,
                    finalColor,
                    true,
                    matrix,
                    buffer,
                    Font.DisplayMode.NORMAL,
                    0,
                    15728880
            );

            pose.popPose();
        }
    }
}
