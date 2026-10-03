package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.VehicleType;

import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.net.packet.BaseTypedPacket;
import lombok.val;
import org.anime_game_servers.multi_proto.gi.messages.gadget.VehicleInteractRsp;
import org.anime_game_servers.multi_proto.gi.messages.gadget.VehicleInteractType;
import org.anime_game_servers.multi_proto.gi.messages.general.Retcode;
import org.anime_game_servers.multi_proto.gi.messages.general.vehicle.VehicleMember;

public class PacketVehicleInteractRsp extends BaseTypedPacket<VehicleInteractRsp> {

    public PacketVehicleInteractRsp(Player player, int entityId, VehicleInteractType interactType) {
        super(new VehicleInteractRsp());

        val entity = player.getScene().getEntityById(entityId);

        if(entity instanceof EntityVehicle vehicle) {
            proto.setEntityId(vehicle.getId());

            val vehicleMember = new VehicleMember(player.getUid(), player.getTeamManager().getCurrentCharacterGuid());

            proto.setInteractType(interactType);
            proto.setMember(vehicleMember);
            setVehicle(vehicle);

            switch(interactType){
                case VEHICLE_INTERACT_IN -> {
                    vehicle.getVehicleMembers().add(vehicleMember);
                    player.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_ENTER_VEHICLE, vehicle.getGadgetId());
                }
                case VEHICLE_INTERACT_OUT -> {
                    vehicle.getVehicleMembers().remove(vehicleMember);
                }
                default -> {}
            }
        }
    }

    public PacketVehicleInteractRsp(EntityVehicle vehicle, VehicleMember vehicleMember, VehicleInteractType interactType) {
        super(new VehicleInteractRsp());

        if(vehicle != null) {
            proto.setEntityId(vehicle.getId());
            proto.setInteractType(interactType);
            proto.setMember(vehicleMember);
            setVehicle(vehicle);

            switch(interactType){
                case VEHICLE_INTERACT_IN -> {
                    vehicle.getVehicleMembers().add(vehicleMember);
                }
                case VEHICLE_INTERACT_OUT -> {
                    vehicle.getVehicleMembers().remove(vehicleMember);
                }
                default -> {}
            }
        }
    }

    private void setVehicle(EntityVehicle vehicle) {
        proto.setRetCode(Retcode.RET_SUCC);
        proto.setVehicleType(getVehicleType(vehicle).getValue());
        proto.setGadgetId(vehicle.getGadgetId());
        proto.setVehiclePos(vehicle.getPosition().toProto());
        proto.setVehicleRot(vehicle.getRotation().toProto());
    }

    private static VehicleType getVehicleType(EntityVehicle vehicle) {
        val config = vehicle.getVehicleConfig();
        return config != null && config.getVehicleType() != null ? config.getVehicleType() : VehicleType.None;
    }
}
