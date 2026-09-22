package dev.xyat.entitycontrol.dummy.entity;

import dev.xyat.kineticcore.api.menu.KineticMenus;
import dev.xyat.kineticcore.api.registry.KineticEntityAttributes;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.entitycontrol.dummy.DummyMenu;
import dev.xyat.entitycontrol.dummy.CuriosCompat;
import dev.xyat.entitycontrol.dummy.config.DummyConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class DummyEntityTest extends Mob implements MenuProvider {

    private final SimpleContainer inventory = new SimpleContainer(6);
    private CompoundTag attributeOverrides = new CompoundTag();
    private static final EntityDataAccessor<Integer> DATA_MOB_TYPE_ID = SynchedEntityData.defineId(DummyEntityTest.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_IFRAMES = SynchedEntityData.defineId(DummyEntityTest.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HEALTH_DROP = SynchedEntityData.defineId(DummyEntityTest.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ENVIRONMENT_DAMAGE = SynchedEntityData.defineId(DummyEntityTest.class, EntityDataSerializers.BOOLEAN);

    public long lastHitTime = 0;
    private boolean slotsInitialized = false;
    private UUID ownerUUID = null;

    private boolean cachedStandby = false;
    private long nextStandbyCheckTick = 0L;

    public boolean allowKtRemoval = false;

    public DummyEntityTest(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
        this.xpReward = 0;
        this.inventory.addListener(container -> updateEquipment());
    }

    public void updateEquipment() {
        for (int i = 0; i < 6; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && !stack.getOrCreateTag().getBoolean("KTDummyItem")) {
                stack.getOrCreateTag().putBoolean("KTDummyItem", true);
            }
        }
        super.setItemSlot(EquipmentSlot.HEAD, inventory.getItem(0));
        super.setItemSlot(EquipmentSlot.CHEST, inventory.getItem(1));
        super.setItemSlot(EquipmentSlot.LEGS, inventory.getItem(2));
        super.setItemSlot(EquipmentSlot.FEET, inventory.getItem(3));
        super.setItemSlot(EquipmentSlot.MAINHAND, inventory.getItem(4));
        super.setItemSlot(EquipmentSlot.OFFHAND, inventory.getItem(5));
        this.savePresetToOwner();
    }

    @Override
    public void setHealth(float health) {
        if (health <= 0.05f) {
            health = this.getMaxHealth();
        }
        super.setHealth(health);
    }

    @Override
    public void die(@NotNull DamageSource cause) {}

    @Override
    public void kill() {}

    @Override
    public void remove(@NotNull RemovalReason reason) {
        if (reason == RemovalReason.KILLED) return;
        if (reason == RemovalReason.DISCARDED && !allowKtRemoval) return;
        super.remove(reason);
    }

    public void setOwnerUUID(UUID uuid) {
        this.ownerUUID = uuid;
    }

    public void savePresetToOwner() {
        if (this.level().isClientSide || this.ownerUUID == null) return;
        Player player = this.level().getPlayerByUUID(this.ownerUUID);
        if (player != null) {
            CompoundTag preset = new CompoundTag();

            ListTag list = new ListTag();
            for (int i = 0; i < this.inventory.getContainerSize(); i++) {
                ItemStack stack = this.inventory.getItem(i);
                if (!stack.isEmpty()) {
                    CompoundTag itemTag = new CompoundTag();
                    itemTag.putByte("Slot", (byte) i);
                    stack.save(itemTag);
                    list.add(itemTag);
                }
            }
            preset.put("Inventory", list);
            preset.putInt("MobType", this.getCustomMobTypeId());
            preset.putBoolean("IFrames", this.hasIFrames());
            preset.putBoolean("HealthDrop", this.isHealthDropEnabled());
            preset.putBoolean("EnvironmentDamage", this.isEnvironmentDamageEnabled());

            CompoundTag attrs = new CompoundTag();
            AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null) attrs.putDouble("MaxHealth", maxHealth.getBaseValue());
            AttributeInstance armor = this.getAttribute(Attributes.ARMOR);
            if (armor != null) attrs.putDouble("Armor", armor.getBaseValue());
            AttributeInstance toughness = this.getAttribute(Attributes.ARMOR_TOUGHNESS);
            if (toughness != null) attrs.putDouble("ArmorToughness", toughness.getBaseValue());
            preset.put("Attributes", attrs);
            preset.put("AttributeOverrides", this.attributeOverrides.copy());

            CompoundTag entityData = new CompoundTag();
            this.saveWithoutId(entityData);
            if (entityData.contains("ForgeCaps")) {
                preset.put("ForgeCaps", entityData.getCompound("ForgeCaps"));
            }

            player.getPersistentData().put("entitycontrolDummyPreset", preset);
        }
    }

    public void loadFromPlayerPreset(Player player) {
        CompoundTag preset = player.getPersistentData().getCompound("entitycontrolDummyPreset");
        if (preset.isEmpty()) return;

        if (preset.contains("Inventory")) {
            this.inventory.clearContent();
            ListTag list = preset.getList("Inventory", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                int slot = itemTag.getByte("Slot") & 255;
                if (slot < this.inventory.getContainerSize()) this.inventory.setItem(slot, ItemStack.of(itemTag));
            }
        }

        if (preset.contains("MobType")) this.setCustomMobType(preset.getInt("MobType"));
        if (preset.contains("IFrames")) this.setIFrames(preset.getBoolean("IFrames"));
        if (preset.contains("HealthDrop")) this.setHealthDrop(preset.getBoolean("HealthDrop"));
        if (preset.contains("EnvironmentDamage")) this.setEnvironmentDamage(preset.getBoolean("EnvironmentDamage"));

        if (preset.contains("Attributes")) {
            CompoundTag attrs = preset.getCompound("Attributes");
            if (attrs.contains("MaxHealth")) this.setAttributeBaseValue(Attributes.MAX_HEALTH, attrs.getDouble("MaxHealth"));
            if (attrs.contains("Armor")) this.setAttributeBaseValue(Attributes.ARMOR, attrs.getDouble("Armor"));
            if (attrs.contains("ArmorToughness")) this.setAttributeBaseValue(Attributes.ARMOR_TOUGHNESS, attrs.getDouble("ArmorToughness"));
        }
        if (preset.contains("AttributeOverrides", Tag.TAG_COMPOUND)) {
            restoreAttributeOverrides(preset.getCompound("AttributeOverrides"));
        }

        if (preset.contains("ForgeCaps")) {
            CompoundTag entityData = new CompoundTag();
            this.saveWithoutId(entityData);
            entityData.put("ForgeCaps", preset.getCompound("ForgeCaps"));

            UUID tempUuid = this.getUUID();
            Vec3 tempPos = this.position();

            this.load(entityData);

            this.setUUID(tempUuid);
            this.setPos(tempPos.x, tempPos.y, tempPos.z);
        }

        updateEquipment();
    }

    private boolean isStandby() {
        if (this.level().isClientSide) return false;

        double standbyRange = DummyConfig.dummyStandbyRange.get();
        if (standbyRange <= 0) {
            cachedStandby = false;
            return false;
        }

        long now = this.level().getGameTime();
        if (now >= nextStandbyCheckTick) {
            int interval = Math.max(1, DummyConfig.dummyStandbyCheckIntervalTicks.get());
            nextStandbyCheckTick = now + interval;
            cachedStandby = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(standbyRange)).isEmpty();
        }
        return cachedStandby;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !isStandby() && super.canBeSeenAsEnemy();
    }

    @Override
    public boolean isSilent() {
        return isStandby() || super.isSilent();
    }

    @Override
    public boolean canAttack(@NotNull LivingEntity target) {
        return false;
    }

    @Override
    public boolean canBeAffected(@NotNull MobEffectInstance effect) {
        return !isStandby() && super.canBeAffected(effect);
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!this.level().isClientSide && !slotsInitialized) {
            CuriosCompat.initDummySlots(this);
            slotsInitialized = true;
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_MOB_TYPE_ID, 0);
        this.entityData.define(DATA_IFRAMES, true);
        this.entityData.define(DATA_HEALTH_DROP, true);
        this.entityData.define(DATA_ENVIRONMENT_DAMAGE, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            long now = this.level().getGameTime();

            // 环境方块伤害检测开关。
            // 关闭时不主动执行 checkInsideBlocks()，假人站在火焰、仙人掌、浆果丛等方块里不会产生高频环境伤害检测。
            // 开启后每 tick 检测，配合“无敌帧: 关”可恢复高频环境伤害/DPS 测试。
            if (this.isEnvironmentDamageEnabled()) {
                this.checkInsideBlocks();
            }

            if (now - this.lastHitTime > 100 && this.getHealth() < this.getMaxHealth()) {
                this.setHealth(this.getMaxHealth());
            }
        }
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (this.level().isClientSide) return false;

        Entity attacker = source.getEntity();
        boolean isPlayerAttack = attacker instanceof Player;

        if (!isPlayerAttack && isStandby()) {
            return false;
        }

        if (!this.hasIFrames()) this.invulnerableTime = 0;
        return super.hurt(source, amount);
    }

    public void setAttributeBaseValue(Attribute attribute, double value) {
        if (attribute == null || !Double.isFinite(value) || value != attribute.sanitizeValue(value)) return;
        ResourceLocation id = KineticRegistries.attributes().id(attribute);
        if (id == null || KineticRegistries.attributes().get(id) != attribute) return;
        AttributeInstance instance = KineticEntityAttributes.ensureInstance(this, attribute);
        instance.setBaseValue(value);
        this.attributeOverrides.putDouble(id.toString(), value);
        if (attribute == Attributes.MAX_HEALTH) this.setHealth(this.getMaxHealth());
    }

    private void restoreAttributeOverrides(CompoundTag saved) {
        if (saved == null || saved.getAllKeys().size() > 512) return;
        for (String name : saved.getAllKeys()) {
            if (!saved.contains(name, Tag.TAG_ANY_NUMERIC)) continue;
            ResourceLocation id = KineticResourceIds.tryParse(name);
            if (id == null || !name.equals(id.toString())) continue;
            Attribute attribute = KineticRegistries.attributes().get(id);
            if (attribute != null && id.equals(KineticRegistries.attributes().id(attribute))) {
                setAttributeBaseValue(attribute, saved.getDouble(name));
            }
        }
    }

    public void setCustomMobType(int id) { this.entityData.set(DATA_MOB_TYPE_ID, id); }
    public int getCustomMobTypeId() { return this.entityData.get(DATA_MOB_TYPE_ID); }
    public void setIFrames(boolean state) { this.entityData.set(DATA_IFRAMES, state); }
    public boolean hasIFrames() { return this.entityData.get(DATA_IFRAMES); }
    public void setHealthDrop(boolean state) { this.entityData.set(DATA_HEALTH_DROP, state); }
    public boolean isHealthDropEnabled() { return this.entityData.get(DATA_HEALTH_DROP); }
    public void setEnvironmentDamage(boolean state) { this.entityData.set(DATA_ENVIRONMENT_DAMAGE, state); }
    public boolean isEnvironmentDamageEnabled() { return this.entityData.get(DATA_ENVIRONMENT_DAMAGE); }

    @Override
    public @NotNull MobType getMobType() {
        return switch (getCustomMobTypeId()) {
            case 1 -> MobType.UNDEAD;
            case 2 -> MobType.ARTHROPOD;
            case 3 -> MobType.ILLAGER;
            case 4 -> MobType.WATER;
            default -> MobType.UNDEFINED;
        };
    }

    @Override
    public @NotNull InteractionResult interactAt(@NotNull Player player, @NotNull Vec3 pos, @NotNull InteractionHand hand) {
        if (!this.level().isClientSide && hand == InteractionHand.MAIN_HAND && player.isCrouching() && player.getMainHandItem().isEmpty()) {
            KineticMenus.open((ServerPlayer) player, getDisplayName(), (containerId, inventory, owner) -> new DummyMenu(containerId, inventory, this), data -> data.writeInt(getId()));
            return InteractionResult.SUCCESS;
        }
        return super.interactAt(player, pos, hand);
    }

    public void refreshSlotAttributes() { this.updateEquipment(); }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("DummyAttributeOverrides", this.attributeOverrides.copy());
        ListTag list = new ListTag();
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack stack = this.inventory.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putByte("Slot", (byte) i);
                stack.save(itemTag);
                list.add(itemTag);
            }
        }
        tag.put("DummyInventory", list);
        tag.putInt("DummyMobType", this.getCustomMobTypeId());
        tag.putBoolean("DummyIFrames", this.hasIFrames());
        tag.putBoolean("DummyHealthDrop", this.isHealthDropEnabled());
        tag.putBoolean("DummyEnvironmentDamage", this.isEnvironmentDamageEnabled());
        tag.putBoolean("SlotsInitialized", this.slotsInitialized);
        if (this.ownerUUID != null) {
            tag.putUUID("OwnerUUID", this.ownerUUID);
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.attributeOverrides = new CompoundTag();
        if (tag.contains("DummyAttributeOverrides", Tag.TAG_COMPOUND)) {
            restoreAttributeOverrides(tag.getCompound("DummyAttributeOverrides"));
        }
        this.inventory.clearContent();
        if (tag.contains("DummyInventory", 9)) {
            ListTag list = tag.getList("DummyInventory", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                int slot = itemTag.getByte("Slot") & 255;
                if (slot < this.inventory.getContainerSize()) this.inventory.setItem(slot, ItemStack.of(itemTag));
            }
        }
        updateEquipment();
        this.nextStandbyCheckTick = 0L;
        this.cachedStandby = false;
        this.setCustomMobType(tag.getInt("DummyMobType"));
        this.setIFrames(tag.getBoolean("DummyIFrames"));
        this.setHealthDrop(tag.getBoolean("DummyHealthDrop"));
        this.setEnvironmentDamage(tag.contains("DummyEnvironmentDamage") && tag.getBoolean("DummyEnvironmentDamage"));
        this.slotsInitialized = tag.getBoolean("SlotsInitialized");
        if (tag.contains("OwnerUUID")) {
            this.ownerUUID = tag.getUUID("OwnerUUID");
        }
    }

    @Nullable @Override protected SoundEvent getHurtSound(@NotNull DamageSource s) { return null; }
    @Nullable @Override protected SoundEvent getDeathSound() { return null; }
    @Nullable @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected void playStepSound(@NotNull net.minecraft.core.BlockPos p, @NotNull net.minecraft.world.level.block.state.BlockState s) {}

    @Override
    protected void dropAllDeathLoot(@NotNull DamageSource damageSource) {}

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
    }

    @Override
    protected void dropCustomDeathLoot(@NotNull DamageSource damageSource, int looting, boolean recentlyHit) {}

    @Override
    public boolean shouldDropLoot() { return false; }

    @Override
    public boolean shouldDropExperience() { return false; }

    @Override
    protected void dropExperience() {}

    @Nullable @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player p) {
        return new DummyMenu(id, inv, this);
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("entity.entitycontrol.dummy");
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(@NotNull Entity entity) {}
}
