package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Loggers;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.ability.mixins.AbilityMixinHandler;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.BasePlayerManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ElementReactionType;
import io.netty.channel.DefaultEventLoop;
import lombok.Getter;
import lombok.NonNull;
import lombok.val;
import org.anime_game_servers.core.base.Version;
import org.anime_game_servers.multi_proto.gi.messages.ability.AbilityInvokeEntry;
import org.anime_game_servers.multi_proto.gi.messages.ability.AbilityInvokeEntryHead;
import org.anime_game_servers.multi_proto.gi.messages.ability.ModifierAction;
import org.anime_game_servers.multi_proto.gi.messages.ability.meta.*;
import org.anime_game_servers.multi_proto.gi.messages.general.ability.AbilityScalarType;
import org.anime_game_servers.multi_proto.gi.messages.general.ability.AbilityScalarValueEntry;
import org.anime_game_servers.multi_proto.gi.messages.general.ability.AbilityString;
import org.slf4j.Logger;
import teyvat.ability.config.*;
import teyvat.ability.config.actions.LoseHP;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;


public final class AbilityManager extends BasePlayerManager {

    /**
     * The ability system's logger. A problem is tagged inline, at the start of its message, by its class:
     * <ul>
     *     <li>{@code [BUG]} (error): a server side failure, such as an exception or an impossible state</li>
     *     <li>{@code [UNEXPECTED]} (warn): the client refers to state the server doesn't have, e.g. an entity,
     *     an instanced ability or modifier, a server buff. The ability implementation being incomplete, this may
     *     reveal one of its flaws rather than a client issue</li>
     *     <li>{@code [MISSING]} (warn): resource or config data not found, e.g. an ability by name, a modifier or
     *     an action by its local id</li>
     *     <li>{@code [UNIMPLEMENTED]} (warn): something the server doesn't handle yet, e.g. an invoke type, an
     *     action or mixin without handler</li>
     *     <li>{@code [INVALID]} (warn): client data that doesn't parse or doesn't fit, e.g. a null key, an unknown
     *     modifier action</li>
     *     <li>{@code [FAILED]} (warn): an action or mixin handler that ran but didn't succeed</li>
     * </ul>
     * The normal flow is untagged, at debug, one line per handled event, and payload dumps are at trace.
     * The context comes right after the tag, usually the invoke's argument type, e.g.
     * {@code [UNEXPECTED] ABILITY_META_OVERRIDE_PARAM: entity 123 not found}.
     */
    @Getter
    private static final Logger logger = Loggers.getAbilitySystem();

    private final static HashMap<Class<?>, AbilityActionHandler> actionHandlers = new HashMap<>();
    private final static HashMap<Class<?>, AbilityMixinHandler> mixinHandlers = new HashMap<>();

    // Single thread: invokes are handled in order, e.g. a modifier's actions only after the modifier is added.
    // A task must not throw: the loop runs tasks unguarded, an exception terminates it for good
    // TODO: should be world or scene scoped instead of shared by the whole server
    public static final ExecutorService eventExecutor = new DefaultEventLoop();
    static {
        registerHandlers();
    }

    public AbilityManager(Player player) {
        super(player);
    }

    public static void registerHandlers() {
        var handlerClassesAction = Grasscutter.reflector.getSubTypesOf(AbilityActionHandler.class);

        for (var obj : handlerClassesAction) {
            try {
                var handler = obj.getDeclaredConstructor().newInstance();
                actionHandlers.put(handler.getType(), handler);
            } catch (Exception e) {
                logger.error("[BUG] Unable to register the action handler {}", obj.getName(), e);
            }
        }

        var handlerClassesMixin = Grasscutter.reflector.getSubTypesOf(AbilityMixinHandler.class);

        for (var obj : handlerClassesMixin) {
            try {
                var handler = obj.getDeclaredConstructor().newInstance();
                mixinHandlers.put(handler.getType(), handler);
            } catch (Exception e) {
                logger.error("[BUG] Unable to register the mixin handler {}", obj.getName(), e);
            }
        }
    }

    public void executeAction(Ability ability, @NonNull ConfigAbilityAction action, byte[] abilityData, GameEntity source, GameEntity target) {
        var actionType = describeAction(action);
        if (ability == null) {
            logger.error("[BUG] Action {}: executed without an ability", actionType);
            return;
        }
        var handler = actionHandlers.get(action.getClass());
        if (handler == null) {
            logger.warn("[UNIMPLEMENTED] Action {}: no handler, at {}", actionType, ability.getName());
            return;
        }

        logger.trace("Action {} content: {}", actionType, action);
        eventExecutor.execute(() -> {
            try {
                if (!handler.execute(ability, action, abilityData, source, target)) {
                    logger.warn("[FAILED] Action {}: not executed, at {}", actionType, ability.getName());
                }
            } catch (Exception e) {
                logger.error("[BUG] Action {}: exception, at {}", actionType, ability.getName(), e);
            }
        });
    }

    public void executeMixin(Ability ability, @NonNull ConfigAbilityMixin mixinData, byte[] abilityData) {
        var mixinType = describeAction(mixinData);
        if (ability == null) {
            logger.error("[BUG] Mixin {}: executed without an ability", mixinType);
            return;
        }
        var handler = mixinHandlers.get(mixinData.getClass());
        if (handler == null) {
            logger.warn("[UNIMPLEMENTED] Mixin {}: no handler, at {}", mixinType, ability.getName());
            return;
        }

        logger.trace("Mixin {} content: {}", mixinType, mixinData);
        eventExecutor.execute(() -> {
            try {
                if (!handler.execute(ability, mixinData, abilityData)) {
                    logger.warn("[FAILED] Mixin {}: not executed, at {}", mixinType, ability.getName());
                }
            } catch (Exception e) {
                logger.error("[BUG] Mixin {}: exception, at {}", mixinType, ability.getName(), e);
            }
        });
    }

    public void onAbilityInvoke(AbilityInvokeEntry invoke) throws Exception {
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        var targetId = invoke.getHead() != null ? invoke.getHead().getTargetId() : 0;
        logger.debug("Ability invoke {}: entity {}, target {}", invoke.getArgumentType(), getEntityIdentity(entity),
            targetId != 0 ? getEntityIdentity(this.player.getScene().getEntityById(targetId)) : null);
        logger.trace("Ability invoke {} content: {}", invoke.getArgumentType(), invoke);

        if(invoke.getHead() != null && invoke.getHead().getLocalId() != 0) {
            this.handleServerInvoke(invoke);
            return;
        }

        switch (invoke.getArgumentType()) {
            case ABILITY_META_OVERRIDE_PARAM -> this.handleOverrideParam(invoke);
//            case ABILITY_META_CLEAR_OVERRIDE_PARAM ->
            case ABILITY_META_REINIT_OVERRIDE_MAP -> this.handleReinitOverrideMap(invoke);
            case ABILITY_META_MODIFIER_CHANGE -> this.handleModifierChange(invoke);
            case ABILITY_ACTION_GENERATE_ELEM_BALL -> this.handleGenerateElemBall(invoke);
            case ABILITY_META_GLOBAL_FLOAT_VALUE -> this.handleGlobalFloatValue(invoke);
            case ABILITY_META_MODIFIER_DURABILITY_CHANGE -> this.handleModifierDurabilityChange(invoke);
            case ABILITY_META_ADD_NEW_ABILITY -> this.handleAddNewAbility(invoke);
            case ABILITY_META_REMOVE_ABILITY -> this.handleRemoveAbility(invoke);
            case ABILITY_META_TRIGGER_ELEMENT_REACTION -> this.handleTriggerElementReaction(invoke);
            case ABILITY_META_ADD_SPECIAL_ENERGY_VALUE -> this.handleAddSpecialEnergy(invoke);
            case ABILITY_MIXIN_COST_STAMINA -> this.handleServerInvoke(invoke);
            case ABILITY_MIXIN_CHANGE_PHLOGISTON -> this.handleServerInvoke(invoke);
            case ABILITY_META_LOSE_HP -> this.handleLoseHp(invoke);
            default -> {
                logger.warn("[UNIMPLEMENTED] {}: invoke type not handled", invoke.getArgumentType());
            }
        }
    }

    private void handleLoseHp(AbilityInvokeEntry invoke) {
        var entity = getInvokeEntity(invoke);
        if (entity == null) return;

        var head = invoke.getHead();
        var data = AbilityMetaLoseHp.parseBy(invoke.getAbilityData(), player.getSession().getVersion());
        if (head == null || data == null) {
            logger.warn("[INVALID] ABILITY_META_LOSE_HP: invoke head or data doesn't parse");
            return;
        }

        var target = this.player.getScene().getEntityById(head.getTargetId());
        if (target == null) {
            logger.warn("[UNEXPECTED] ABILITY_META_LOSE_HP: target {} not found", head.getTargetId());
            return;
        }
        var modifier = entity.getInstancedModifiers().get(head.getInstancedModifierId());
        if (modifier == null) {
            logger.warn("[UNEXPECTED] ABILITY_META_LOSE_HP: modifier id {} not found on {}",
                head.getInstancedModifierId(), getEntityIdentity(entity));
            return;
        }

        var ability = modifier.getAbility();
        var localId = data.getLoseHpConfigIdx();
        var action = GameData.getAbilityConfigs().action(ability.getData(), localId);
        if (action == null) {
            logger.warn("[MISSING] ABILITY_META_LOSE_HP: no action at local id {} of {}", localId, ability.getName());
            return;
        }
        if (!(action instanceof LoseHP)) {
            logger.warn("[UNEXPECTED] ABILITY_META_LOSE_HP: local id {} of {} is {}, not a LoseHP action",
                localId, ability.getName(), describeAction(action));
            return;
        }
        executeAction(ability, action, new byte[0], entity, target);
    }

    /** The invoke's entity, logged when not found */
    @Nullable
    private GameEntity<?> getInvokeEntity(AbilityInvokeEntry invoke) {
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        if (entity == null) {
            logger.warn("[UNEXPECTED] {}: entity {} not found", invoke.getArgumentType(), invoke.getEntityId());
        }
        return entity;
    }

    private void handleAddSpecialEnergy(AbilityInvokeEntry invoke) {
        var entity = getInvokeEntity(invoke);
        if (entity == null) return;
        if (!(entity instanceof EntityAvatar avatarEntity)) {
            logger.warn("[UNEXPECTED] ABILITY_META_ADD_SPECIAL_ENERGY_VALUE: {} isn't an avatar", getEntityIdentity(entity));
            return;
        }
        var data = AbilityMetaAddSpecialEnergyValue.parseBy(invoke.getAbilityData(), player.getSession().getVersion());
        if (data == null) {
            logger.warn("[INVALID] ABILITY_META_ADD_SPECIAL_ENERGY_VALUE: data doesn't parse");
            return;
        }
        avatarEntity.getAvatar().addSpecialEnergy(data.getSpecialEnergyDelta());
    }

    private EntityIdentity getEntityIdentity(GameEntity<?> entity) {
        if (entity instanceof EntityGadget gadget) {
            var data = gadget.getGadgetData();
            return new EntityIdentity("Gadget", entity.getId(), data == null? 0: data.getId());
        }
        if (entity instanceof EntityMonster monster) {
            var data = monster.getMonsterData();
            return new EntityIdentity("Monster", entity.getId(), data == null? 0: data.getId());
        }
        if (entity instanceof EntityAvatar avatar) {
            var data = avatar.getAvatar().getData();
            return new EntityIdentity("Avatar", entity.getId(), data == null? 0: data.getId());
        }
        if (entity instanceof EntityClientGadget clientGadget) {
            var data = clientGadget.getGadgetData();
            return new EntityIdentity("ClientGadget", entity.getId(), data == null? 0: data.getId());
        }
        if (entity instanceof EntityTeam team) {
            return new EntityIdentity("Team", team.getId(), 0);
        }
        if (entity != null) {
            return new EntityIdentity("Unknown", entity.getId(), 0);
        }
        return null;
    }

    record EntityIdentity(String type, int entityId, int id) {}

    private String describeAction(BaseConfigAction action) {
        if (action == null) return "null";
        if (action instanceof UnknownAction || action instanceof UnknownMixin) {
            return action.toString();
        }
        return action.getClass().getSimpleName();
    }

    public void handleServerInvoke(AbilityInvokeEntry invoke) {
        var head = invoke.getHead();
        var entity = getInvokeEntity(invoke);
        if (entity == null) return;

        Ability ability = null;

        //Find ability or modifier's ability
        boolean isServerBuff = head.getServerBuffUid() != 0;
        if (isServerBuff) {
            // the buff's modifier is keyed by the buff, its ability usually lives on the scene entity
            var modifier = entity.getServerBuffModifiers().get(head.getServerBuffUid());
            ability = modifier != null
                ? modifier.getAbility()
                : resolveServerBuffAbility(head, head.getModifierConfigLocalId());
        }
        else if (head.getInstancedModifierId() != 0) {
            var modifier = entity.getInstancedModifiers().get(head.getInstancedModifierId());
            if (modifier != null) {
                ability = modifier.getAbility();
            }
        }
        else if (head.getInstancedAbilityId() != 0) {
            ability = entity.getInstancedAbilities().get(head.getInstancedAbilityId());
        }

        if(ability == null) {
            logger.warn("[UNEXPECTED] {}: ability not found, instanced ability {}, modifier {}, server buff {}, on {}",
                invoke.getArgumentType(), head.getInstancedAbilityId(), head.getInstancedModifierId(),
                head.getServerBuffUid(), getEntityIdentity(entity));
            return;
        }

        //Time to reach the handlers
        var action = GameData.getAbilityConfigs().action(ability.getData(), head.getLocalId());
        if(action != null) {
            //Executing action
            // for a server buff, the target id is the ability owner, the action applies to the buff holder
            var target = head.getTargetId() != 0 && !isServerBuff
                ? this.player.getScene().getEntityById(head.getTargetId())
                : entity;
            if (target == null) {
                logger.warn("[UNEXPECTED] {}: target {} not found, action {} of {} executed without target",
                    invoke.getArgumentType(), head.getTargetId(), describeAction(action), ability.getName());
            }

            var debugSite = GameData.getAbilityConfigs().getDebugger().site(ability.getData().getAbilityName(), head.getLocalId());
            if (debugSite != null) {
                logger.debug("{}: action {}, {} #{} {}, entity {}, target {}",
                    invoke.getArgumentType(), describeAction(action),
                    debugSite.getAbilityName(), debugSite.getLocalId(), debugSite.getPath(),
                    getEntityIdentity(entity), getEntityIdentity(target)
                );
            }
            else {
                logger.debug("{}: action {}, {} #{}, entity {}, target {}",
                    invoke.getArgumentType(), describeAction(action),
                    ability.getData().getAbilityName(), head.getLocalId(),
                    getEntityIdentity(entity), getEntityIdentity(target)
                );
            }
            executeAction(ability, action, invoke.getAbilityData(), entity, target);
            return;
        } else {
            var mixin = GameData.getAbilityConfigs().mixin(ability.getData(), head.getLocalId());

            if(mixin != null) {
                logger.debug("{}: mixin {}, entity {}",
                    invoke.getArgumentType(),
                    GameData.getAbilityConfigs().getDebugger().describe(ability.getData().getAbilityName(), head.getLocalId()),
                    getEntityIdentity(entity)
                );
                executeMixin(ability, mixin, invoke.getAbilityData());
                return;
            }
        }

        logger.warn("[MISSING] {}: no action nor mixin at local id {} of {}, on {}", invoke.getArgumentType(),
            head.getLocalId(), ability.getName(), getEntityIdentity(entity));
    }

    // multi proto dynamic runtime only
    private boolean isScalarFloat(AbilityScalarValueEntry entry) {
        // compat: newer versions don't have valueType property, and only floatValue
        if (player.getSession().getVersion().getId() >= Version.GI_4_8_0.getId()) return true;
        return entry.getValueType() == AbilityScalarType.ABILITY_SCALAR_TYPE_FLOAT;
    }

    private static float getScalarFloatValue(AbilityScalarValueEntry entry) {
        val wrapper = entry.getValue();
        if (wrapper instanceof AbilityScalarValueEntry.Value.FloatValue floatValue) {
            return floatValue.getValue();
        }
        if (wrapper instanceof AbilityScalarValueEntry.Value.UintValue uintValue) {
            return uintValue.getValue().floatValue();
        }
        if (wrapper instanceof AbilityScalarValueEntry.Value.IntValue intValue) {
            return intValue.getValue().floatValue();
        }
        // 4.8 and later: a plain float_value instead of the oneof
        return entry.getFloatValue();
    }

    private void setAbilityOverrideValue(Ability ability, AbilityScalarValueEntry valueChange) {
        if (!isScalarFloat(valueChange)) {
            logger.warn("[UNIMPLEMENTED] Override value of {}: scalar type {} not supported", ability.getName(), valueChange.getValueType());

            return;
        }

        if(valueChange.getKey() == null){
            logger.warn("[INVALID] Override value of {}: null key", ability.getName());
            return;
        }

        var hash = Ability.getAbilityHash(valueChange.getKey());
        var value = getScalarFloatValue(valueChange);
        ability.getOverrideMap().put(hash, value);

        if (valueChange.getKey().getType() instanceof AbilityString.Type.Str str){
            val key = str.getValue();
            logger.debug("Override value of {}: {} = {}", ability.getName(), key, value);
        } else {
            // TODO: Calculate all the ability value hashes
            logger.debug("Override value of {}: hash {} = {}", ability.getName(), hash, value);
        }

    }

    private void handleOverrideParam(AbilityInvokeEntry invoke) throws Exception {
        var entity = getInvokeEntity(invoke);
        if (entity == null) return;
        var head = invoke.getHead();

        var ability = entity.getInstancedAbilities().get(head.getInstancedAbilityId());
        if(ability == null) {
            logger.warn("[UNEXPECTED] ABILITY_META_OVERRIDE_PARAM: instanced ability {} not found on {}",
                head.getInstancedAbilityId(), getEntityIdentity(entity));
            return;
        }

        var valueChange = AbilityScalarValueEntry.parseBy(invoke.getAbilityData(), player.getSession().getVersion());
        setAbilityOverrideValue(ability, valueChange);
    }

    private void handleReinitOverrideMap(AbilityInvokeEntry invoke) throws Exception {
        var entity = getInvokeEntity(invoke);
        if (entity == null) return;
        var head = invoke.getHead();

        var ability = entity.getInstancedAbilities().get(head.getInstancedAbilityId());
        if(ability == null) {
            logger.warn("[UNEXPECTED] ABILITY_META_REINIT_OVERRIDE_MAP: instanced ability {} not found on {}",
                head.getInstancedAbilityId(), getEntityIdentity(entity));
            return;
        }

        var valueChanges = AbilityMetaReInitOverrideMap.parseBy(invoke.getAbilityData(), player.getSession().getVersion());
        ability.refreshServerOverrideMap();
        ability.getOverrideMap().clear();
        for (var variableChange : valueChanges.getOverrideMap()) {
            setAbilityOverrideValue(ability, variableChange);
        }
    }

    private void handleModifierChange(AbilityInvokeEntry invoke) throws Exception {
        //TODO:
        var modChange = AbilityMetaModifierChange.parseBy(invoke.getAbilityData(), player.getSession().getVersion());
        var head = invoke.getHead();

        if(head.getInstancedModifierId() > 2000) {
            logger.warn("[UNEXPECTED] ABILITY_META_MODIFIER_CHANGE: instanced modifier id {} over 2000", head.getInstancedModifierId());
            return;
        }
        // A removal only names the instanced modifier, its instanced ability id is 0
        if(modChange.getAction() == ModifierAction.ADDED && head.getInstancedAbilityId() == 0) {
            logger.warn("[UNEXPECTED] ABILITY_META_MODIFIER_CHANGE: modifier id {} added without an instanced ability, on entity {}",
                head.getInstancedModifierId(), invoke.getEntityId());
            return;
        }

        if(head.getServerBuffUid() != 0) {
            handleServerBuffModifierChange(invoke, modChange);
            return;
        }

        if(head.isServerbuffModifier()) {
            logger.warn("[UNEXPECTED] ABILITY_META_MODIFIER_CHANGE: serverbuff modifier without server buff uid, modifier id {}",
                head.getInstancedModifierId());
            return;
        }

        var entity = getInvokeEntity(invoke);
        if (entity == null) return;

        if(modChange.getAction() == ModifierAction.ADDED) {
            var modifierOwner = head.getTargetId() != 0
                ? this.player.getScene().getEntityById(head.getTargetId())
                : entity;
            if (modifierOwner == null) {
                logger.warn("[UNEXPECTED] ABILITY_META_MODIFIER_CHANGE: ability owner {} not found", head.getTargetId());
                return;
            }

            var instancedAbility = modifierOwner.getInstancedAbilities().get(head.getInstancedAbilityId());
            if (instancedAbility == null) {
                logger.warn("[UNEXPECTED] ABILITY_META_MODIFIER_CHANGE: instanced ability {} not found on owner {}, entity {}, change {}",
                    head.getInstancedAbilityId(), getEntityIdentity(modifierOwner),
                    getEntityIdentity(entity), modChange);
                return;
            }
            var instancedAbilityData = instancedAbility.getData();

            if(modChange.getModifierLocalId() >= instancedAbilityData.getModifiers().size())
            {
                logger.warn("[MISSING] ABILITY_META_MODIFIER_CHANGE: modifier local id {} not in {}, entity {}, owner {}",
                    modChange.getModifierLocalId(), instancedAbility.getName(), getEntityIdentity(entity),
                    getEntityIdentity(modifierOwner));
                return;
            }
            var modifierData = instancedAbilityData.modifier(modChange.getModifierLocalId());
            logger.debug("ABILITY_META_MODIFIER_CHANGE: {} modifier id {} with {} / {} on {}",
                entity.getInstancedModifiers().containsKey(head.getInstancedModifierId()) ? "replacing" : "adding",
                head.getInstancedModifierId(), instancedAbilityData.getAbilityName(),
                instancedAbilityData.getModifierNames().get(modChange.getModifierLocalId()), getEntityIdentity(entity));

            var modifier = new AbilityModifierController(instancedAbility, modifierData);
            var replaced = entity.getInstancedModifiers().put(head.getInstancedModifierId(), modifier);
            if (replaced != null) replaced.detach(entity);
            modifier.attach(entity);
        } else if(modChange.getAction() == ModifierAction.REMOVED) {
            var modifier = entity.getInstancedModifiers().remove(head.getInstancedModifierId());
            logger.debug("ABILITY_META_MODIFIER_CHANGE: removing modifier id {} from {}: {}", head.getInstancedModifierId(),
                getEntityIdentity(entity), modifier);
            if (modifier != null) modifier.detach(entity);
        } else {
            logger.warn("[INVALID] ABILITY_META_MODIFIER_CHANGE: unknown modifier action {}", modChange.getAction());
        }
    }

    /** Modifier of a server buff, held by the invoke's entity and keyed by the buff uid */
    private void handleServerBuffModifierChange(AbilityInvokeEntry invoke, AbilityMetaModifierChange modChange) {
        var head = invoke.getHead();
        var holder = getInvokeEntity(invoke);
        if (holder == null) return;

        int uid = head.getServerBuffUid();
        if (modChange.getAction() == ModifierAction.ADDED) {
            var buff = this.player.getBuffManager().getBuff(uid);
            var ability = resolveServerBuffAbility(head, modChange.getModifierLocalId());
            if (buff == null || ability == null) return;

            var modifierData = ability.getData().getModifiers().get(buff.getBuffData().getModifierName());
            if (modifierData == null) {
                logger.warn("[MISSING] ABILITY_META_MODIFIER_CHANGE: server buff {} modifier {} not in {}", uid,
                    buff.getBuffData().getModifierName(), ability.getName());
                return;
            }
            logger.debug("ABILITY_META_MODIFIER_CHANGE: adding server buff {} modifier {} to {}", uid,
                buff.getBuffData().getModifierName(), getEntityIdentity(holder));

            var modifier = new AbilityModifierController(ability, modifierData);
            var replaced = holder.getServerBuffModifiers().put(uid, modifier);
            if (replaced != null) replaced.detach(holder);
            modifier.attach(holder);
        } else if (modChange.getAction() == ModifierAction.REMOVED) {
            var modifier = holder.getServerBuffModifiers().remove(uid);
            logger.debug("ABILITY_META_MODIFIER_CHANGE: removing server buff {} modifier from {}: {}", uid,
                getEntityIdentity(holder), modifier != null);
            if (modifier != null) modifier.detach(holder);
        }
    }

    /**
     * The ability of the server buff an invoke points to: the buff's own ability, on the invoke's target entity
     * (usually the scene entity). Soft validated against the invoke's instanced ability and modifier local id,
     * a mismatch indicates a wrong ability set on the target entity.
     */
    @Nullable
    private Ability resolveServerBuffAbility(AbilityInvokeEntryHead head, int modifierLocalId) {
        var buff = this.player.getBuffManager().getBuff(head.getServerBuffUid());
        if (buff == null) {
            logger.warn("[UNEXPECTED] Server buff {}: not found", head.getServerBuffUid());
            return null;
        }
        var abilityName = buff.getBuffData().getAbilityName();
        var modifierName = buff.getBuffData().getModifierName();

        var owner = this.player.getScene().getEntityById(head.getTargetId());
        if (owner == null) {
            logger.warn("[UNEXPECTED] Server buff {}: ability owner {} not found", head.getServerBuffUid(), head.getTargetId());
            return null;
        }

        var ability = owner.getInstancedAbilities().get(head.getInstancedAbilityId());
        var invokeAbilityName = ability == null ? null : ability.getName();
        var modifierNames = ability == null ? List.<String>of() : ability.getData().getModifierNames();
        var invokeModifierName = modifierLocalId >= 0 && modifierLocalId < modifierNames.size() ? modifierNames.get(modifierLocalId) : null;
        if (abilityName.equals(invokeAbilityName) && modifierName.equals(invokeModifierName)) {
            return ability;
        }

        logger.warn("[UNEXPECTED] Server buff {} ({}): is {} / {}, but the invoke points to {} / {} (instanced ability {}, modifier {}) on {}: wrong ability set?",
            head.getServerBuffUid(), buff.getBuffData().getId(), abilityName, modifierName,
            invokeAbilityName, invokeModifierName, head.getInstancedAbilityId(), modifierLocalId, getEntityIdentity(owner));
        return owner.getInstancedAbilities().values().stream()
            .filter(a -> abilityName.equals(a.getName()))
            .findFirst().orElse(null);
    }

    private void handleGenerateElemBall(AbilityInvokeEntry invoke) {
        logger.warn("[UNIMPLEMENTED] ABILITY_ACTION_GENERATE_ELEM_BALL: not handled without a local id, entity {}", invoke.getEntityId());
    }

    private void handleGlobalFloatValue(AbilityInvokeEntry invoke) {
        var entity = getInvokeEntity(invoke);
        if (entity == null) return;

        var entry = AbilityScalarValueEntry.parseBy(invoke.getAbilityData(), player.getSession().getVersion());
        if(entry == null) {
            logger.warn("[INVALID] ABILITY_META_GLOBAL_FLOAT_VALUE: data doesn't parse");
            return;
        }

        logger.debug("ABILITY_META_GLOBAL_FLOAT_VALUE: key {} = {} on {}", entry.getKey(), entry.getFloatValue(), getEntityIdentity(entity));
        // FIXME: the key is a global value name, not an ability name, so a hashed key never resolves here:
        //  the ability hash list only holds ability names
        var key = Ability.getAbilityName(entry.getKey());
        if(key == null) {
            logger.warn("[UNIMPLEMENTED] ABILITY_META_GLOBAL_FLOAT_VALUE: key {} not resolved, global value names aren't hashed yet", entry.getKey());
            return;
        }

        if(key.startsWith("SGV_")) return; //Server does not allow to change these variables
        if (isScalarFloat(entry)) {
            float value = getScalarFloatValue(entry);
            if (!Float.isNaN(value))
                entity.getGlobalAbilityValues().put(key, value);
        }
        else if (entry.getValueType() == AbilityScalarType.ABILITY_SCALAR_TYPE_UINT) {
            float uvalue = getScalarFloatValue(entry);
            entity.getGlobalAbilityValues().put(key, uvalue);
        }
    }

    private void handleModifierDurabilityChange(AbilityInvokeEntry invoke) {
        logger.warn("[UNIMPLEMENTED] ABILITY_META_MODIFIER_DURABILITY_CHANGE: not handled, entity {}", invoke.getEntityId());
    }

    private void handleAddNewAbility(AbilityInvokeEntry invoke) {
        var entity = getInvokeEntity(invoke);
        if (entity == null) return;

        var meta = AbilityMetaAddAbility.parseBy(invoke.getAbilityData(), player.getSession().getVersion());

        // assumption: ability name is not hashed
        var abilityName = Ability.getAbilityName(meta.getAbility().getAbilityName());
        var abilityData = abilityName == null? null: GameData.getAbilityConfigs().ability(abilityName);
        if(abilityData == null) {
            logger.warn("[MISSING] ABILITY_META_ADD_NEW_ABILITY: ability {} not found, for {}",
                abilityName != null ? abilityName : meta.getAbility().getAbilityName(), getEntityIdentity(entity));
            return;
        }

        var instancedAbilityId = meta.getAbility().getInstancedAbilityId();
        var existing = entity.getInstancedAbilities().get(instancedAbilityId);
        var ability = existing != null ? existing : new Ability(abilityData, entity, player);
        if (existing == null) {
            entity.getInstancedAbilities().put(instancedAbilityId, ability);
            ability.attach();
        }

        meta.getAbility().getOverrideMap()
            .forEach(entry -> setAbilityOverrideValue(ability, entry));

        logger.debug("ABILITY_META_ADD_NEW_ABILITY: {} added to {} at instanced ability {}", abilityName,
            getEntityIdentity(entity), instancedAbilityId);
    }

    private void handleRemoveAbility(AbilityInvokeEntry invoke) {
        var entity = getInvokeEntity(invoke);
        if (entity == null) return;
        Optional.ofNullable(invoke.getHead())
            .map(AbilityInvokeEntryHead::getInstancedAbilityId)
            .map(id -> entity.getInstancedAbilities().remove((int)id))
            .ifPresent(Ability::detach);
    }

    /**
     * Invoked when an entity triggered an elemental reaction.
     * @param invoke Holds information of elemental reaction, attacker and target.
     */
    private void handleTriggerElementReaction(AbilityInvokeEntry invoke) {
        if (getPlayer().getScene() == null) return;

        AbilityMetaTriggerElementReaction data = AbilityMetaTriggerElementReaction.parseBy(invoke.getAbilityData(), player.getSession().getVersion());
        var targetEntity = getInvokeEntity(invoke);
        if (targetEntity == null) return;

        ElementReactionType reactionType = ElementReactionType.getTypeByValue(data.getElementReactionType());
        // Challenge related action
        Optional.ofNullable(getPlayer().getScene().getChallenge())
            .ifPresent(sceneChallenge -> sceneChallenge.onElementReaction(targetEntity, reactionType));
    }

    public void initEntityAbilities(GameEntity<?> entity, Collection<String> abilityNames) {
        int id = 0;
        for (var abilityName : abilityNames) {
            ++id;
            var abilityData = GameData.getAbilityConfigs().ability(abilityName);
            if (abilityData == null) continue;  // skip missing. TODO: logging
            var ability = new Ability(abilityData, entity, this.player);
            var replaced = entity.getInstancedAbilities().put(id, ability);
            if (replaced != null) replaced.detach();
            ability.attach();
        }
    }
}

