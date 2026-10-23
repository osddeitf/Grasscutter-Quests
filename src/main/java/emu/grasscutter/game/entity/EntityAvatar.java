package emu.grasscutter.game.entity;

import emu.grasscutter.data.excels.TeamResonanceData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.create_config.CreateGadgetEntityConfig;
import emu.grasscutter.game.inventory.EquipType;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.EntityIdType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.props.PlayerProperty;
import teyvat.ability.config.enums.AbilityState;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.server.event.player.PlayerMoveEvent;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.utils.Position;
import emu.grasscutter.utils.ProtoHelper;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import lombok.Getter;
import lombok.val;
import org.anime_game_servers.multi_proto.gi.messages.general.PlayerDieType;
import org.anime_game_servers.multi_proto.gi.messages.general.PropChangeReason;
import org.anime_game_servers.multi_proto.gi.messages.general.ability.AbilitySyncStateInfo;
import org.anime_game_servers.multi_proto.gi.messages.general.entity.SceneReliquaryInfo;
import org.anime_game_servers.multi_proto.gi.messages.scene.entity.*;

import java.util.ArrayList;
import java.util.List;

public class EntityAvatar extends GameEntity {
    @Getter private final Avatar avatar;
    @Getter private PlayerDieType killedType;
    @Getter private int killedBy;

    public EntityAvatar(Avatar avatar) {
        this(null, avatar);
    }

    public EntityAvatar(Scene scene, Avatar avatar) {
        super(scene);
        this.avatar = avatar;
        if (scene != null) {
            this.id = getScene().getWorld().getNextEntityId(EntityIdType.AVATAR);

            GameItem weapon = this.getAvatar().getWeapon();
            if (weapon != null) {
                if (!(weapon.getWeaponEntity() != null && weapon.getWeaponEntity().getScene() == scene)) {
                    val weaponCreateConfig = new CreateGadgetEntityConfig(weapon.getItemData().getGadgetId());
                    weapon.setWeaponEntity(new EntityWeapon(getPlayer().getScene(), weaponCreateConfig));
                    scene.getWeaponEntities().put(weapon.getWeaponEntity().getId(), weapon.getWeaponEntity());
                }
                //weapon.setWeaponEntityId(getScene().getWorld().getNextEntityId(EntityIdType.WEAPON));
            }
        }
    }

    @Override
    public int getEntityTypeId() {
        return getAvatar().getAvatarId();
    }

    public Player getPlayer() {
        return this.avatar.getPlayer();
    }

    @Override
    public Position getPosition() {
        return getPlayer().getPosition();
    }

    @Override
    public Position getRotation() {
        return getPlayer().getRotation();
    }

    @Override
    public boolean isAlive() {
        return this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) > 0f;
    }

    @Override
    public Int2FloatMap getFightProperties() {
        return getAvatar().getFightProperties();
    }

    public int getWeaponEntityId() {
        if (getAvatar().getWeapon() != null && getAvatar().getWeapon().getWeaponEntity() != null) {
            return getAvatar().getWeapon().getWeaponEntity().getId();
        }
        return 0;
    }

    @Override
    public void onDeath(int killerId) {
        super.onDeath(killerId); // Invoke super class's onDeath() method.

        this.killedType = PlayerDieType.PLAYER_DIE_KILL_BY_MONSTER;
        this.killedBy = killerId;
        clearEnergy(ChangeEnergyReason.CHANGE_ENERGY_NONE);
    }

    public void onDeath(PlayerDieType dieType, int killerId) {
        super.onDeath(killerId); // Invoke super class's onDeath() method.

        this.killedType = dieType;
        this.killedBy = killerId;
        clearEnergy(ChangeEnergyReason.CHANGE_ENERGY_NONE);
    }

    @Override
    public float heal(float amount, boolean mute) {
        // Do not heal character if they are dead
        if (!this.isAlive()) {
            return 0f;
        }

        float healed = super.heal(amount, mute);

        if (healed > 0f) {
            getScene().broadcastPacket(
                new PacketEntityFightPropChangeReasonNotify(this, FightProperty.FIGHT_PROP_CUR_HP, healed, mute ? PropChangeReason.PROP_CHANGE_NONE : PropChangeReason.PROP_CHANGE_ABILITY, ChangeHpReason.CHANGE_HP_ADD_ABILITY)
            );
        }

        return healed;
    }

    @Override
    public float heal(float amount) {
        return this.heal(amount, false);
    }

    // Consumes the whole energy, e.g. on death
    public void clearEnergy(ChangeEnergyReason reason) {
        costEnergy(this.avatar.getCurrentEnergy(), reason);
    }

    // Consumes some energy, down to 0, e.g. a burst's cost on skill start
    public void costEnergy(float cost, ChangeEnergyReason reason) {
        val curEnergyProp = this.avatar.getCurEnergyProp();
        float curEnergy = this.avatar.getCurrentEnergy();
        float newEnergy = Math.max(0, curEnergy - cost);

        this.avatar.setCurrentEnergy(newEnergy);

        // Send packets.
        this.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(this, curEnergyProp));

        if (reason == ChangeEnergyReason.CHANGE_ENERGY_SKILL_START) {
            this.getScene().broadcastPacket(new PacketEntityFightPropChangeReasonNotify(this, curEnergyProp, newEnergy - curEnergy, reason));
        }
    }

    /**
     * Fills the energy to max.
     * @return false if it was already full
     */
    public boolean refillEnergy() {
        float maxEnergy = this.avatar.getMaxEnergy();
        if (this.avatar.getCurrentEnergy() == maxEnergy || hasAbilityState(AbilityState.IgnoreAddEnergy)) {
            return false;
        }
        this.avatar.setCurrentEnergy(maxEnergy);
        this.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(this, this.avatar.getCurEnergyProp()));
        return true;
    }

    public void addEnergy(float amount, PropChangeReason reason) {
        this.addEnergy(amount, reason, false);
    }

    /**
     * Adds elemental energy (particles, attacks, items, ...), capped to max, or drains it (negative), down to 0.
     * Special energy isn't gained this way, so avatars using it are skipped.
     */
    public void addEnergy(float amount, PropChangeReason reason, boolean isFlat) {
        if (this.avatar.hasSpecialEnergy()) return;
        // a modifier's IgnoreAddEnergy state blocks gains only, e.g. during a burst, a drain still applies
        if (amount > 0 && hasAbilityState(AbilityState.IgnoreAddEnergy)) return;

        // Get current and maximum energy for this avatar.
        val curEnergyProp = this.avatar.getCurEnergyProp();
        float curEnergy = this.avatar.getCurrentEnergy();
        float maxEnergy = this.avatar.getMaxEnergy();

        // Scale amount by energy recharge, if the amount is not flat.
        if (!isFlat) {
            amount *= this.getFightProperty(FightProperty.FIGHT_PROP_CHARGE_EFFICIENCY);
        }

        // Determine the new energy value, a negative amount drains it.
        float newEnergy = Math.max(0, Math.min(curEnergy + amount, maxEnergy));

        // Set energy and notify.
        if (newEnergy != curEnergy) {
            this.avatar.setCurrentEnergy(newEnergy);

            // I only see EntityFightPropUpdataNotify being sent by the official server, and without any prop reason,
            // not sure how that differs to this
            this.getScene().broadcastPacket(new PacketEntityFightPropChangeReasonNotify(this, curEnergyProp, newEnergy, reason));
        }
    }

    public SceneAvatarInfo getSceneAvatarInfo() {
        val avatar = this.getAvatar();
        val player = this.getPlayer();
        val avatarInfo = new SceneAvatarInfo(player.getUid(), avatar.getAvatarId(), avatar.getGuid(), player.getPeerId());
        avatarInfo.setTalentIdList(avatar.getTalentIdList().intStream().boxed().toList());
        avatarInfo.setCoreProudSkillLevel(avatar.getCoreProudSkillLevel());
        avatarInfo.setSkillLevelMap(avatar.getSkillLevelMap());
        avatarInfo.setSkillDepotId(avatar.getSkillDepotId());
        avatarInfo.setInherentProudSkillList(avatar.getProudSkillList().stream().toList());
        avatarInfo.setProudSkillExtraLevelMap(avatar.getProudSkillBonusMap());
        avatarInfo.setTeamResonanceList(player.getTeamManager().getElementalResonances().stream().map(TeamResonanceData::getId).toList());
        avatarInfo.setWearingFlycloakId(avatar.getFlyCloak());
        avatarInfo.setCostumeId(avatar.getCostume());
        avatarInfo.setBornTime(avatar.getBornTime());
        avatarInfo.setWeaponSkinId(avatar.getWeaponSkin());

        val reliquaryList = new ArrayList<SceneReliquaryInfo>();
        val equipList = new ArrayList<Integer>();

        for (GameItem item : avatar.getEquips().values()) {
            if (item.getItemData().getEquipType() == EquipType.EQUIP_WEAPON) {
                var weapon = item.createSceneWeaponInfo();
                weapon.setWeaponSkinId(avatar.getWeaponSkin());
                avatarInfo.setWeapon(weapon);
            } else {
                reliquaryList.add(item.createSceneReliquaryInfo());
            }
            equipList.add(item.getItemId());
        }

        avatarInfo.setReliquaryList(reliquaryList);
        avatarInfo.setEquipIdList(equipList);

        return avatarInfo;
    }

    @Override
    public SceneEntityInfo toProto() {
        val bornPosProto = new org.anime_game_servers.multi_proto.gi.messages.general.Vector();
        val aiInfo = new SceneEntityAiInfo(true, bornPosProto);
        val authority = new EntityAuthorityInfo(new AbilitySyncStateInfo(), new EntityRendererChangedInfo(), aiInfo, bornPosProto);

        val entityInfo = new SceneEntityInfo(org.anime_game_servers.multi_proto.gi.messages.scene.entity.ProtEntityType.PROT_ENTITY_AVATAR, getId());
        entityInfo.setAnimatorParaList(List.of(new AnimatorParameterValueInfoPair()));
        entityInfo.setEntityClientData(new EntityClientData());
        entityInfo.setEntityAuthorityInfo(authority);
        entityInfo.setLastMoveSceneTimeMs(this.getLastMoveSceneTimeMs());
        entityInfo.setLastMoveReliableSeq(this.getLastMoveReliableSeq());
        entityInfo.setLifeState(this.getLifeState().getValue());
        entityInfo.setTagList(getEntityTags().stream().toList());

        if (this.getScene() != null) {
            entityInfo.setMotionInfo(this.getMotionInfo());
        }

        this.addAllFightPropsToEntityInfo(entityInfo);

        val pair = new org.anime_game_servers.multi_proto.gi.messages.scene.entity.PropPair(PlayerProperty.PROP_LEVEL.getId(), ProtoHelper.newPropValue(PlayerProperty.PROP_LEVEL, getAvatar().getLevel()));
        entityInfo.setPropList(List.of(pair));

        entityInfo.setEntity(new SceneEntityInfo.Entity.Avatar(this.getSceneAvatarInfo()));

        return entityInfo;
    }

    /**
     * Move this entity to a new position.
     * Additionally invoke player move event.
     *
     * @param newPosition The new position.
     * @param rotation    The new rotation.
     */
    @Override
    public void move(Position newPosition, Position rotation) {
        // Invoke player move event.
        PlayerMoveEvent event = new PlayerMoveEvent(
            this.getPlayer(), PlayerMoveEvent.MoveType.PLAYER,
            this.getPosition(), newPosition
        );
        event.call();

        // Set position and rotation.
        super.move(event.getDestination(), rotation);
    }
}
