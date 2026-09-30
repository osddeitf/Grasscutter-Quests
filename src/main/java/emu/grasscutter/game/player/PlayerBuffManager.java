package emu.grasscutter.game.player;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.AbilityMath;
import emu.grasscutter.data.excels.BuffData;
import emu.grasscutter.game.ability.AbilityLocalId;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketServerBuffChangeNotify;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import lombok.val;
import org.anime_game_servers.multi_proto.gi.messages.battle.ServerBuffChangeType;
import org.anime_game_servers.multi_proto.gi.messages.general.ability.ServerBuff;
import teyvat.ability.config.actions.HealHP;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class PlayerBuffManager extends BasePlayerManager {
    private int nextBuffUid;

    private final List<PlayerBuff> pendingBuffs;
    private final Int2ObjectMap<PlayerBuff> buffs; // Server buffs

    public PlayerBuffManager(Player player) {
        super(player);
        this.buffs = new Int2ObjectOpenHashMap<>();
        this.pendingBuffs = new ArrayList<>();
    }

    /**
     * Gets a new uid for a server buff
     * @return New integer buff uid
     */
    private int getNextBuffUid() {
        return ++nextBuffUid;
    }

    /**
     * Returns true if the player has a buff with this group id
     * @param groupId Buff group id
     * @return True if a buff with this group id exists
     */
    public synchronized boolean hasBuff(int groupId) {
        return this.buffs.containsKey(groupId);
    }

    /**
     * Clears all player buffs
     */
    public synchronized void clearBuffs() {
        // Remove from player
        getPlayer().sendPacket(
            new PacketServerBuffChangeNotify(getPlayer(), ServerBuffChangeType.DEL_SERVER_BUFF, this.buffs.values().stream().toList())
        );

        // Clear
        this.buffs.clear();
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
     * @param duration Duration of the buff in seconds. Set to 0 for an infinite buff.
     * @return True if a buff was added
     */
    public synchronized boolean addBuff(int buffId, float duration) {
        return addBuff(buffId, duration, null);
    }

    /**
     * Adds a server buff to the player.
     * @param buffId Server buff id
     * @param duration Duration of the buff in seconds. Set to 0 for an infinite buff.
     * @param target Target avatar
     * @return True if a buff was added
     */
    public synchronized boolean addBuff(int buffId, float duration, Avatar target) {
        // Get buff excel data
        BuffData buffData = GameData.getBuffDataMap().get(buffId);
        if (buffData == null) return false;

        boolean success = false;

        // Perform onAdded actions
        success |= performOnAdded(buffData, target);

        // Set duration
        if (duration < 0f) {
            duration = buffData.getTime();
        }

        // Dont add buff if duration is equal or less than 0
        if (duration <= 0) {
            return success;
        }

        // Clear previous buff if it exists
        this.removeBuff(buffData.getGroupId());

        // Create and store buff
        PlayerBuff buff = new PlayerBuff(getNextBuffUid(), buffData, duration);
        this.buffs.put(buff.getGroupId(), buff);

        // Packet
        getPlayer().sendPacket(new PacketServerBuffChangeNotify(getPlayer(), ServerBuffChangeType.ADD_SERVER_BUFF, buff));

        return true;
    }

    /**
     * Runs the top-level onAdded actions of the buff's modifier, only HealHP is supported.
     * There's no instanced ability for a server buff, so keys resolve to the ability specials.
     * TODO: temporary solution for the new ability config setup, server buffs need an overhaul
     * @return True if any action was performed
     */
    private boolean performOnAdded(BuffData buffData, Avatar target) {
        var configs = GameData.getAbilityConfigs();
        var ability = configs.ability(buffData.getAbilityName());
        if (ability == null) return false;
        int modifierIndex = ability.getModifierNames().indexOf(buffData.getModifierName());
        if (modifierIndex < 0) return false;

        Function<String, Float> specials = key -> ability.getAbilitySpecials().getOrDefault(key, 0f);
        boolean success = false;
        for (var action : AbilityLocalId.modifierActions(configs, ability, modifierIndex, AbilityLocalId.MODIFIER_ON_ADDED)) {
            if (action instanceof HealHP heal && target != null) {
                float maxHp = target.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
                float amount = AbilityMath.evaluate(heal.amount, specials, 0f)
                    + AbilityMath.evaluate(heal.amountByTargetMaxHPRatio, specials, 0f) * maxHp;
                target.getAsEntity().heal(amount);
                success = true;
            }
        }
        return success;
    }

    /**
     * Removes a buff by its group id
     * @param buffGroupId Server buff group id
     * @return True if a buff was remove
     */
    public synchronized boolean removeBuff(int buffGroupId) {
        PlayerBuff buff = this.buffs.remove(buffGroupId);

        if (buff != null) {
            getPlayer().sendPacket(
                new PacketServerBuffChangeNotify(getPlayer(), ServerBuffChangeType.DEL_SERVER_BUFF, buff)
            );
            return true;
        }

        return false;
    }

    public synchronized void onTick() {
        // Skip if no buffs
        if (this.buffs.isEmpty()) return;

        long currentTime = System.currentTimeMillis();

        // Add to pending buffs to remove if buff has expired
        this.buffs.values().removeIf(buff -> {
            if (currentTime <= buff.getEndTime())
                return false;
            this.pendingBuffs.add(buff);
            return true;
        });

        if (!this.pendingBuffs.isEmpty()) {
            // Send packet
            getPlayer().sendPacket(
                new PacketServerBuffChangeNotify(getPlayer(), ServerBuffChangeType.DEL_SERVER_BUFF, this.pendingBuffs)
            );
            this.pendingBuffs.clear();
        }
    }

    @Getter
    public static class PlayerBuff {
        private final int uid;
        private final BuffData buffData;
        private final long endTime;

        public PlayerBuff(int uid, BuffData buffData, float duration) {
            this.uid = uid;
            this.buffData = buffData;
            this.endTime = System.currentTimeMillis() + ((long) duration * 1000);
        }

        public int getGroupId() {
            return getBuffData().getGroupId();
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
