package emu.grasscutter.game.player;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.BuffData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.props.ServerBuffType;
import emu.grasscutter.server.packet.send.PacketServerBuffChangeNotify;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import lombok.val;
import org.anime_game_servers.multi_proto.gi.messages.battle.ServerBuffChangeType;
import org.anime_game_servers.multi_proto.gi.messages.general.ability.ServerBuff;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Server buffs given by the server, each held by a set of entities: avatars by guid, other entities by id.
 * Buff uids are incremental, the client refers to them in the invokes of the buff's modifier.
 */
public class PlayerBuffManager extends BasePlayerManager {
    private int nextBuffUid;

    /** Buffs without a group (group id 0), by uid: never replaced */
    private final Int2ObjectMap<ServerBuffInstance> ungroupedBuffs;
    /** Buffs by group id: a holder has at most one buff of a group, a new one replaces it */
    private final Int2ObjectMap<List<ServerBuffInstance>> groupedBuffs;

    public PlayerBuffManager(Player player) {
        super(player);
        this.ungroupedBuffs = new Int2ObjectOpenHashMap<>();
        this.groupedBuffs = new Int2ObjectOpenHashMap<>();
    }

    /**
     * Gets a new uid for a server buff
     * @return New integer buff uid
     */
    private int getNextBuffUid() {
        return ++nextBuffUid;
    }

    private Stream<ServerBuffInstance> allBuffs() {
        return Stream.concat(
            this.ungroupedBuffs.values().stream(),
            this.groupedBuffs.values().stream().flatMap(List::stream)
        );
    }

    /**
     * Gets a buff by its uid, as referred to by the invokes of the buff's modifier
     * @param uid Server buff uid
     * @return The buff, null if it doesn't exist (anymore)
     */
    @Nullable
    public synchronized ServerBuffInstance getBuff(int uid) {
        return allBuffs().filter(buff -> buff.getUid() == uid).findFirst().orElse(null);
    }

    /**
     * @param buffId Server buff id
     * @return True if the player has a buff of this id
     */
    public synchronized boolean hasBuff(int buffId) {
        return allBuffs().anyMatch(buff -> buff.getBuffData().getId() == buffId);
    }

    /**
     * Clears all player buffs
     */
    public synchronized void clearBuffs() {
        notifyHolders(ServerBuffChangeType.DEL_SERVER_BUFF, allBuffs().toList());
        this.ungroupedBuffs.clear();
        this.groupedBuffs.clear();
    }

    /**
     * Adds a server buff to the player.
     * @param buffId Server buff id
     * @return True if a buff was added
     */
    public boolean addBuff(int buffId) {
        return addBuff(buffId, -1f);
    }

    /**
     * Adds a server buff to the player.
     * @param buffId Server buff id
     * @param duration Duration of the buff in seconds, the buff's own time if negative
     * @return True if a buff was added
     */
    public synchronized boolean addBuff(int buffId, float duration) {
        return addBuff(buffId, duration, null);
    }

    /**
     * Adds a server buff to the player.
     * The buff's modifier and its actions (e.g. heal) are then applied by the client, through the buff's invokes.
     * @param buffId Server buff id
     * @param duration Duration of the buff in seconds, the buff's own time if negative
     * @param target Target avatar of an avatar buff, the current avatar if null
     * @return True if a buff was added
     */
    public synchronized boolean addBuff(int buffId, float duration, @Nullable Avatar target) {
        // Get buff excel data
        BuffData buffData = GameData.getBuffDataMap().get(buffId);
        if (buffData == null) return false;

        // Set duration
        if (duration < 0f) {
            duration = buffData.getTime();
        }

        // Dont add buff if duration is 0, a negative one (e.g. the buff's time -1) lasts until removed
        if (duration == 0) {
            return false;
        }

        var holders = getHolderAvatars(buffData, target);
        if (holders.isEmpty()) return false;

        // Create and store buff
        var avatarGuids = holders.stream().map(Avatar::getGuid).toList();
        var buff = new ServerBuffInstance(getNextBuffUid(), buffData, duration, avatarGuids, List.of());
        if (buff.getGroupId() == 0) {
            this.ungroupedBuffs.put(buff.getUid(), buff);
        } else {
            // Replace the group's previous buff on the same holders
            var group = this.groupedBuffs.computeIfAbsent(buff.getGroupId(), k -> new ArrayList<>());
            var replaced = group.stream().filter(buff::sharesHolder).toList();
            group.removeAll(replaced);
            notifyHolders(ServerBuffChangeType.DEL_SERVER_BUFF, replaced);
            group.add(buff);
        }

        notifyHolders(ServerBuffChangeType.ADD_SERVER_BUFF, List.of(buff));
        return true;
    }

    /** An avatar buff goes to the target avatar (the current one if none), others to every avatar of the active team */
    private List<Avatar> getHolderAvatars(BuffData buffData, @Nullable Avatar target) {
        var teamManager = getPlayer().getTeamManager();
        if (buffData.getServerBuffType() == ServerBuffType.SERVER_BUFF_AVATAR) {
            if (target == null) {
                var current = teamManager.getCurrentAvatarEntity();
                target = current == null ? null : current.getAvatar();
            }
            return target == null ? List.of() : List.of(target);
        }
        return teamManager.getActiveTeam().stream().map(EntityAvatar::getAvatar).toList();
    }

    /**
     * Removes the buffs of a buff id
     * @param buffId Server buff id
     * @return True if a buff was removed
     */
    public synchronized boolean removeBuff(int buffId) {
        var removed = removeBuffsIf(buff -> buff.getBuffData().getId() == buffId);
        notifyHolders(ServerBuffChangeType.DEL_SERVER_BUFF, removed);
        return !removed.isEmpty();
    }

    public synchronized void onTick() {
        long currentTime = System.currentTimeMillis();
        var expired = removeBuffsIf(buff -> currentTime > buff.getEndTime());
        notifyHolders(ServerBuffChangeType.DEL_SERVER_BUFF, expired);
    }

    /** Removes the matching buffs from both lists, returning them */
    private List<ServerBuffInstance> removeBuffsIf(Predicate<ServerBuffInstance> filter) {
        var removed = new ArrayList<ServerBuffInstance>();
        Predicate<ServerBuffInstance> collect = buff -> {
            if (!filter.test(buff)) return false;
            removed.add(buff);
            return true;
        };
        this.ungroupedBuffs.values().removeIf(collect);
        this.groupedBuffs.values().forEach(group -> group.removeIf(collect));
        this.groupedBuffs.values().removeIf(List::isEmpty);
        return removed;
    }

    /** One notify per holder of each changed buff */
    private void notifyHolders(ServerBuffChangeType changeType, Collection<ServerBuffInstance> changed) {
        var packets = new ArrayList<PacketServerBuffChangeNotify>();
        for (var buff : changed) {
            var protos = List.of(buff.toProto());
            buff.getAvatarGuids().forEach(guid -> packets.add(PacketServerBuffChangeNotify.ofAvatar(guid, changeType, protos)));
            buff.getEntityIds().forEach(id -> packets.add(PacketServerBuffChangeNotify.ofEntity(id, changeType, protos)));
        }
        packets.forEach(getPlayer()::sendPacket);
    }

    @Getter
    public static class ServerBuffInstance {
        private final int uid;
        private final BuffData buffData;
        private final long endTime;
        /** Holders: avatars by guid, other entities by id */
        private final List<Long> avatarGuids;
        private final List<Integer> entityIds;

        public ServerBuffInstance(int uid, BuffData buffData, float duration, List<Long> avatarGuids, List<Integer> entityIds) {
            this.uid = uid;
            this.buffData = buffData;
            this.endTime = duration < 0 ? Long.MAX_VALUE : System.currentTimeMillis() + (long) (duration * 1000);
            this.avatarGuids = avatarGuids;
            this.entityIds = entityIds;
        }

        public int getGroupId() {
            return getBuffData().getGroupId();
        }

        public boolean sharesHolder(ServerBuffInstance other) {
            return this.avatarGuids.stream().anyMatch(other.avatarGuids::contains)
                || this.entityIds.stream().anyMatch(other.entityIds::contains);
        }

        public ServerBuff toProto() {
            val proto = new ServerBuff();
            proto.setServerBuffUid(this.getUid());
            proto.setServerBuffId(this.getBuffData().getId());
            proto.setServerBuffType(this.getBuffData().getServerBuffType().getValue());
            proto.setInstancedModifierId(1);
            return proto;
        }
    }
}
