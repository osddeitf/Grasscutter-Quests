package emu.grasscutter.game.entity;

import emu.grasscutter.data.binout.config.ConfigEntityGadget;
import emu.grasscutter.data.binout.config.fields.ConfigVehicle;
import emu.grasscutter.game.entity.create_config.CreateGadgetEntityConfig;
import emu.grasscutter.game.entity.gadget.content.GadgetContent;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.props.VehicleType;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.server.packet.send.PacketVehicleInteractRsp;
import emu.grasscutter.utils.ProtoHelper;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.anime_game_servers.multi_proto.gi.messages.gadget.VehicleInteractType;
import org.anime_game_servers.multi_proto.gi.messages.general.Retcode;
import org.anime_game_servers.multi_proto.gi.messages.general.vehicle.VehicleInfo;
import org.anime_game_servers.multi_proto.gi.messages.general.vehicle.VehicleMember;
import org.anime_game_servers.multi_proto.gi.messages.scene.VehicleLocationInfo;
import org.anime_game_servers.multi_proto.gi.messages.scene.entity.*;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A vehicle, either created for a player (e.g. a skiff, an indwelled saurian), or placed by a group (e.g. a saurian
 * standing in the world): no owner nor transform entity, and not kept by the world
 */
public class EntityVehicle extends EntityGadget {
    /** Observed: an indwelled saurian's phlogiston bar is full at 50 */
    public static final float MAX_PHLOGISTON = 50;

    /** Null for a vehicle placed by a group */
    @Nullable @Getter private final Player owner;

    // TODO: never updated, StaminaManager tracks the riding player's vehicle stamina on its own (one per player,
    //  reset to its global max on getting in); it should read and write this instead
    @Getter @Setter private float curStamina;
    @Getter @Setter private float curPhlogiston;
    /** The entity this vehicle was transformed from, e.g. an indwelled saurian */
    @Getter @Setter private int transformEntityId;
    @Getter private final List<VehicleMember> vehicleMembers;

    public EntityVehicle(Scene scene, CreateGadgetEntityConfig createConfig) {
        super(scene, createConfig);
        this.owner = createConfig.getPlayerOwner();
        val vehicleConfig = getVehicleConfig();
        this.curStamina = vehicleConfig != null && vehicleConfig.getStamina() != null
            ? vehicleConfig.getStamina().getStaminaUpperLimit()
            : 240;
        if (getGroupId() > 0) {
            // TODO: from the gadget config, its phlogiston field isn't identified yet. Full, as on indwelling
            this.curPhlogiston = MAX_PHLOGISTON;
        }
        this.vehicleMembers = new ArrayList<>();
    }

    @Nullable
    public ConfigVehicle getVehicleConfig() {
        return getConfigGadget() != null ? getConfigGadget().getVehicle() : null;
    }

    public VehicleType getVehicleType() {
        val config = getVehicleConfig();
        return config != null && config.getVehicleType() != null ? config.getVehicleType() : VehicleType.None;
    }

    /** The config's, else 1, e.g. with an obfuscated config */
    public int getMaxSeatCount() {
        val config = getVehicleConfig();
        return config != null && config.getMaxSeatCount() > 0 ? config.getMaxSeatCount() : 1;
    }

    public boolean hasMember(int uid) {
        return findMember(uid) != null;
    }

    @Nullable
    private VehicleMember findMember(int uid) {
        return getVehicleMembers().stream().filter(member -> member.getUid() == uid).findFirst().orElse(null);
    }

    /**
     * A player gets in, as one of their avatars: adds the vehicle's server buff (e.g. invincible, not lockable).
     * Anyone may get in, not only the owner.
     */
    public VehicleMember addMember(Player player, long avatarGuid) {
        val member = new VehicleMember(player.getUid(), avatarGuid);
        getVehicleMembers().add(member);

        val config = getVehicleConfig();
        if (config != null && config.getServerBuffId() != 0) {
            player.getBuffManager().addBuff(config.getServerBuffId());
        }

        player.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_ENTER_VEHICLE, getGadgetId());
        return member;
    }

    /** A player gets off: removes the vehicle's server buff. Null if the player wasn't in */
    @Nullable
    public VehicleMember removeMember(Player player) {
        val member = findMember(player.getUid());
        if (member == null) return null;
        getVehicleMembers().remove(member);

        val config = getVehicleConfig();
        if (config != null && config.getServerBuffId() != 0) {
            player.getBuffManager().removeBuff(config.getServerBuffId());
        }
        return member;
    }

    /**
     * Getting in, as on VehicleInteractReq: off any other vehicle first, the stamina, the member, then the response.
     * @return False if the vehicle is full
     */
    public boolean enter(Player player, long avatarGuid) {
        var member = findMember(player.getUid());
        if (member == null && getVehicleMembers().size() >= getMaxSeatCount()) {
            player.sendPacket(new PacketVehicleInteractRsp(Retcode.RET_VEHICLE_SLOT_OCCUPIED, getId(), VehicleInteractType.VEHICLE_INTERACT_IN));
            return false;
        }

        // Off any other vehicle first, as getting off does
        getScene().getVehiclesOf(player).stream().filter(vehicle -> vehicle != this).forEach(vehicle -> vehicle.exit(player));

        // Resets StaminaManager's own vehicle stamina, not this vehicle's (see curStamina)
        player.getStaminaManager().handleVehicleInteractReq(player.getSession(), getId(), VehicleInteractType.VEHICLE_INTERACT_IN);

        if (member == null) {
            member = addMember(player, avatarGuid);
        }
        // TODO: co-op, only the player is told, other players in the scene may need to know
        player.sendPacket(new PacketVehicleInteractRsp(this, member, VehicleInteractType.VEHICLE_INTERACT_IN));
        return true;
    }

    /**
     * Getting off, as on VehicleInteractReq: the stamina, the member, then the response.
     * TODO: Sorush leaves nothing behind on getting off
     */
    public void exit(Player player) {
        player.getStaminaManager().handleVehicleInteractReq(player.getSession(), getId(), VehicleInteractType.VEHICLE_INTERACT_OUT);

        var member = removeMember(player);
        if (member == null) {
            member = new VehicleMember(player.getUid(), player.getTeamManager().getCurrentCharacterGuid());
        }
        // TODO: co-op, only the player is told, other players in the scene may need to know
        player.sendPacket(new PacketVehicleInteractRsp(this, member, VehicleInteractType.VEHICLE_INTERACT_OUT));
    }

    @Override
    protected void fillFightProps(ConfigEntityGadget configGadget) {
        super.fillFightProps(configGadget);
        this.addFightProperty(FightProperty.FIGHT_PROP_CUR_SPEED, 0);
        this.addFightProperty(FightProperty.FIGHT_PROP_CHARGE_EFFICIENCY, 0);
    }

    /** None, whatever the gadget type: the vehicle info is the content */
    @Override
    public GadgetContent buildContent(CreateGadgetEntityConfig config) {
        return null;
    }

    /**
     * When the vehicle dies (e.g. a saurian drowning), after it disappeared, control returns to the members as
     * getting off does. The official server may do more.
     */
    @Override
    public void onDeath(int killerId) {
        super.onDeath(killerId);
        ejectMembers();
    }

    /** Control returns to the members as getting off does, e.g. when the vehicle dies or is evicted */
    public void ejectMembers() {
        for (val player : getScene().getPlayers()) {
            if (!hasMember(player.getUid())) continue;
            exit(player);
            // The avatar stays in the scene with a saurian (no disappear nor appear on the wire),
            // other vehicles may need it to appear again
        }
    }

    /** No longer kept by the world, e.g. killed or evicted */
    @Override
    public void onRemoved() {
        super.onRemoved();
        getScene().getWorld().getVehicleStore().remove(this);
    }

    /** For ScenePlayerLocationNotify, with the uids of the players inside */
    public VehicleLocationInfo toLocationInfo() {
        val info = new VehicleLocationInfo(getId(), getGadgetId(), getOwnerUid());
        info.setPos(getPosition().toProto());
        info.setRot(getRotation().toProto());
        info.setCurHp(getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        info.setMaxHp(getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
        info.setUidList(getVehicleMembers().stream().map(VehicleMember::getUid).toList());
        return info;
    }

    private int getOwnerUid() {
        return this.owner != null ? this.owner.getUid() : 0;
    }

    /** The gadget's, with the vehicle info as content */
    @Override
    public SceneEntityInfo toProto() {
        val entityInfo = super.toProto();

        val vehicle = new VehicleInfo();
        vehicle.setOwnerUid(getOwnerUid());
        vehicle.setCurStamina(getCurStamina());
        vehicle.setCurPhlogiston(getCurPhlogiston());
        vehicle.setTransformEntityId(getTransformEntityId());
        vehicle.setMemberList(List.copyOf(getVehicleMembers()));

        val gadgetInfo = ((SceneEntityInfo.Entity.Gadget) entityInfo.getEntity()).getValue();
        gadgetInfo.setContent(new SceneGadgetInfo.Content.VehicleInfo(vehicle));
        // TODO: co-op, the authority stays with the owner, even when another player drives
        if (this.owner != null) {
            gadgetInfo.setAuthorityPeerId(this.owner.getPeerId());
        }

        // A group's level, else as observed for a player's
        val level = getLevel() > 0 ? getLevel() : 47;
        val pair = new PropPair(PlayerProperty.PROP_LEVEL.getId(), ProtoHelper.newPropValue(PlayerProperty.PROP_LEVEL, level));
        entityInfo.setPropList(List.of(pair));

        return entityInfo;
    }
}
