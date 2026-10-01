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

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;


public final class AbilityManager extends BasePlayerManager {

    @Getter
    private static final Logger logger = Loggers.getAbilitySystem();

    private final static HashMap<Class<?>, AbilityActionHandler> actionHandlers = new HashMap<>();
    private final static HashMap<Class<?>, AbilityMixinHandler> mixinHandlers = new HashMap<>();

    // Single thread: invokes are handled in order, e.g. a modifier's actions only after the modifier is added.
    // TODO: should be world or scene scoped instead of shared by the whole server
    public static final ExecutorService eventExecutor = new DefaultEventLoop();
    static {
        registerHandlers();
    }

    @Getter private boolean abilityInvulnerable = false;

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
                logger.error("Unable to register handlerClassesAction.", e);
            }
        }

        var handlerClassesMixin = Grasscutter.reflector.getSubTypesOf(AbilityMixinHandler.class);

        for (var obj : handlerClassesMixin) {
            try {
                var handler = obj.getDeclaredConstructor().newInstance();
                mixinHandlers.put(handler.getType(), handler);
            } catch (Exception e) {
                logger.error("Unable to register handlerClassesMixin.", e);
            }
        }
    }

    public void executeAction(Ability ability, ConfigAbilityAction action, byte[] abilityData, GameEntity target) {
        var actionType = describeAction(action);
        var handler = actionHandlers.get(action.getClass());

        if (handler == null || ability == null) {
            logger.error("executeAction could not execute ability action {} at {}", actionType, ability.getName());
            return;
        }

        // should be DEBUG level
        logger.info("handleServerInvoke content {}", action);
        eventExecutor.execute(() -> {
            if (!handler.execute(ability, action, abilityData, target)) {
                logger.error("executeAction exec ability action failed {} at {}", actionType, ability.getName());
            }
        });
    }

    public void executeMixin(Ability ability, ConfigAbilityMixin mixinData, byte[] abilityData) {
        var mixinType = describeAction(mixinData);
        var handler = mixinHandlers.get(mixinData.getClass());

        if (handler == null || ability == null || mixinData == null) {
            logger.error("executeMixin could not execute ability mixin {} at {}", mixinType, ability.getName());
            return;
        }

        eventExecutor.execute(() -> {
            if (!handler.execute(ability, mixinData, abilityData)) {
                logger.error("executeMixin exec ability action failed {} at {}", mixinType, ability.getName());
            }
        });
    }

    public void onAbilityInvoke(AbilityInvokeEntry invoke) throws Exception {
        logger.debug("Ability invoke: {} {} ({}): {}", invoke, invoke.getArgumentType(), invoke.getArgumentType(), this.player.getScene().getEntityById(invoke.getEntityId()));
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        if(entity != null) {
            logger.debug("Entity group id {} config id {}", entity.getGroupId(), entity.getConfigId());
        }
        if(invoke.getHead() != null && invoke.getHead().getTargetId() != 0) {
            logger.debug("Target: {}", this.player.getScene().getEntityById(invoke.getHead().getTargetId()));
        }

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
            default -> {}
        }
    }

    private void handleAddSpecialEnergy(AbilityInvokeEntry invoke) {
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        if(entity == null) {
            logger.info("handleServerInvoke entity not found: {}", invoke.getEntityId());
            return;
        }
        if (!(entity instanceof EntityAvatar avatarEntity)) {
            logger.info("handleAddSpecialEnergy entity is not an avatar: {}", invoke.getEntityId());
            return;
        }
        var data = AbilityMetaAddSpecialEnergyValue.parseBy(invoke.getAbilityData(), player.getSession().getVersion());
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
        if (action instanceof UnknownAction || action instanceof UnknownMixin) {
            return action.toString();
        }
        return action.getClass().getSimpleName();
    }

    public void handleServerInvoke(AbilityInvokeEntry invoke) {
        var head = invoke.getHead();

        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        if(entity == null) {
            logger.info("handleServerInvoke entity not found: {}", invoke.getEntityId());
            return;
        }

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
            logger.warn("handleServerInvoke ability not found: entity {} ability {} modifier {}, {}", invoke.getEntityId(), head.getInstancedAbilityId(), head.getInstancedModifierId(), getEntityIdentity(entity));
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

            var debugSite = GameData.getAbilityConfigs().getDebugger().site(ability.getData().getAbilityName(), head.getLocalId());
            if (debugSite != null) {
                logger.info("handleServerInvoke action: {}, {}, {} #{} {}, entity {}, target {}",
                    describeAction(action), invoke.getArgumentType().toString(),
    //                head.getLocalId(), describeAction(action), ability.getData().getAbilityName(),
    //                GameData.getAbilityConfigs().getDebugger().site(ability.getData().getAbilityName(), head.getLocalId()),
                    debugSite.getAbilityName(), debugSite.getLocalId(), debugSite.getPath(),
                    getEntityIdentity(entity), getEntityIdentity(target)
                );
            }
            else {
                logger.info("handleServerInvoke action: {}, {}, {} #{}, entity {}, target {}",
                    describeAction(action), invoke.getArgumentType().toString(),
                    ability.getData().getAbilityName(), head.getLocalId(),
                    getEntityIdentity(entity), getEntityIdentity(target)
                );
            }
            executeAction(ability, action, invoke.getAbilityData(), target);

            return;
        } else {
            var mixin = GameData.getAbilityConfigs().mixin(ability.getData(), head.getLocalId());

            if(mixin != null) {
                logger.info("handleServerInvoke mixin: type {}, {}, entity {}",
                    invoke.getArgumentType().toString(),
                    GameData.getAbilityConfigs().getDebugger().describe(ability.getData().getAbilityName(), head.getLocalId()),
//                    head.getLocalId(), describeAction(mixin), ability.getData().getAbilityName(),
                    getEntityIdentity(entity)
                );
                executeMixin(ability, mixin, invoke.getAbilityData());

                return;
            }
        }

        logger.info("handleServerInvoke Action or mixin not found: local_id {}, ability {}, entity {}", head.getLocalId(), ability.getData().getAbilityName(), getEntityIdentity(entity));
    }

    /**
     * Invoked when a player starts a skill.
     * @param player The player who started the skill.
     * @param skillId The skill ID.
     * @param casterId The caster ID.
     */
    public void onSkillStart(Player player, int skillId, int casterId) {
        // Check if the player matches this player.
        if (player.getUid() != this.player.getUid()) {
            return;
        }

        // Check if the caster matches the player.
        if (player.getTeamManager().getCurrentAvatarEntity().getId() != casterId) {
            return;
        }

        var skillData = GameData.getAvatarSkillDataMap().get(skillId);
        if (skillData == null) {
            return;
        }

        // Check if the skill is an elemental burst.
        if (skillData.getCostElemVal() <= 0) {
            return;
        }

        // Set the player as invulnerable.
        this.abilityInvulnerable = true;
    }

    /**
     * Invoked when a player ends a skill.
     * @param player The player who started the skill.
     */
    public void onSkillEnd(Player player) {
        // Check if the player matches this player.
        if (player.getUid() != this.player.getUid()) {
            return;
        }

        // Check if the player is invulnerable.
        if (!this.abilityInvulnerable) {
            return;
        }

        // Set the player as not invulnerable.
        this.abilityInvulnerable = false;
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
            logger.info("setAbilityOverrideValue scalar type not supported: {}", valueChange.getValueType());

            return;
        }

        if(valueChange.getKey() == null){
            logger.warn("setAbilityOverrideValue key is null");
            return;
        }

        var hash = Ability.getAbilityHash(valueChange.getKey());
        var value = getScalarFloatValue(valueChange);
        ability.getOverrideMap().put(hash, value);

        if (valueChange.getKey().getType() instanceof AbilityString.Type.Str str){
            val key = str.getValue();
            logger.debug("Ability {} changed {} to {}", ability.getData().getAbilityName(), key, value);
        } else {
            // TODO: Calculate all the ability value hashes
            logger.info("Ability {} changed value with hash({}) to {}", ability.getData().getAbilityName(), hash, value);
        }

    }

    private void handleOverrideParam(AbilityInvokeEntry invoke) throws Exception {
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        var head = invoke.getHead();

        if(entity == null) {
            logger.info("handleOverrideParam entity not found: {}", invoke.getEntityId());
            return;
        }

        var ability = entity.getInstancedAbilities().get(head.getInstancedAbilityId());
        if(ability == null) {
            logger.error("handleOverrideParam ability not found {}", head.getInstancedAbilityId());
            return;
        }

        var valueChange = AbilityScalarValueEntry.parseBy(invoke.getAbilityData(), player.getSession().getVersion());
        setAbilityOverrideValue(ability, valueChange);
    }

    private void handleReinitOverrideMap(AbilityInvokeEntry invoke) throws Exception {
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        var head = invoke.getHead();

        if(entity == null) {
            logger.info("handleReinitOverrideMap entity not found: {}", invoke.getEntityId());
            return;
        }

        var ability = entity.getInstancedAbilities().get(head.getInstancedAbilityId());
        if(ability == null) {
            logger.error("handleReinitOverrideMap ability not found {}", head.getInstancedAbilityId());
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

        if(head.getInstancedAbilityId() == 0 || head.getInstancedModifierId() > 2000) return; //Error: TODO: display error

        if(head.getServerBuffUid() != 0) {
            handleServerBuffModifierChange(invoke, modChange);
            return;
        }

        if(head.isServerbuffModifier()) {
            //TODO
            logger.info("TODO: Handle serverbuff modifier");

            return;
        }

        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        if(entity == null){
            logger.warn("handleModifierChange entity not found: {}", invoke.getEntityId());
            return;
        }

        if(modChange.getAction() == ModifierAction.ADDED) {
            var modifierOwner = head.getTargetId() != 0
                ? this.player.getScene().getEntityById(head.getTargetId())
                : entity;
            var instancedAbility = modifierOwner.getInstancedAbilities().get(head.getInstancedAbilityId());
            if (instancedAbility == null) {
                logger.info("handleModifierChange No ability found. entity {}, owner {}, modChange: {}", getEntityIdentity(entity), getEntityIdentity(modifierOwner),  modChange);
                return; //TODO: Display error message
            }
            var instancedAbilityData = instancedAbility.getData();

            if(modChange.getModifierLocalId() >= instancedAbilityData.getModifiers().size())
            {
                logger.info("handleModifierChange modifier local id {} not found. entity {}, owner {}", modChange.getModifierLocalId(), getEntityIdentity(entity), getEntityIdentity(modifierOwner));
                return;
            }
            var modifierData = instancedAbilityData.modifier(modChange.getModifierLocalId());
            if(entity.getInstancedModifiers().containsKey(head.getInstancedModifierId())) {
                logger.info("Replacing entity {} modifier id {} with ability {} modifier {}", invoke.getEntityId(), head.getInstancedModifierId(), instancedAbilityData.getAbilityName(), 
                instancedAbilityData.getModifierNames().get(modChange.getModifierLocalId())
            );
            } else {
                logger.info("Adding entity {} modifier id {} with ability {} modifier {}", invoke.getEntityId(), head.getInstancedModifierId(), instancedAbilityData.getAbilityName(),
                    instancedAbilityData.getModifierNames().get(modChange.getModifierLocalId())
                );
            }

            var modifier = new AbilityModifierController(instancedAbility, modifierData);
            var replaced = entity.getInstancedModifiers().put(head.getInstancedModifierId(), modifier);
            if (replaced != null) replaced.detach(entity);
            modifier.attach(entity);
        } else if(modChange.getAction() == ModifierAction.REMOVED) {
            logger.info("Removed on entity {} modifier id {}: {}", invoke.getEntityId(), head.getInstancedModifierId(), entity.getInstancedModifiers().get(head.getInstancedModifierId()));

            //TODO: Add debug log
            var modifier = entity.getInstancedModifiers().remove(head.getInstancedModifierId());
            if (modifier != null) modifier.detach(entity);
        } else {
            //TODO: Display error message
            logger.debug("handleModifierChange unknown action");
        }
    }

    /** Modifier of a server buff, held by the invoke's entity and keyed by the buff uid */
    private void handleServerBuffModifierChange(AbilityInvokeEntry invoke, AbilityMetaModifierChange modChange) {
        var head = invoke.getHead();
        var holder = this.player.getScene().getEntityById(invoke.getEntityId());
        if (holder == null) {
            logger.warn("handleServerBuffModifierChange holder entity not found: {}", invoke.getEntityId());
            return;
        }

        int uid = head.getServerBuffUid();
        if (modChange.getAction() == ModifierAction.ADDED) {
            var buff = this.player.getBuffManager().getBuff(uid);
            var ability = resolveServerBuffAbility(head, modChange.getModifierLocalId());
            if (buff == null || ability == null) return;

            var modifierData = ability.getData().getModifiers().get(buff.getBuffData().getModifierName());
            if (modifierData == null) {
                logger.warn("handleServerBuffModifierChange modifier {} not found in {}", buff.getBuffData().getModifierName(), ability.getName());
                return;
            }
            logger.debug("Adding server buff {} modifier {} to {}", uid, buff.getBuffData().getModifierName(), getEntityIdentity(holder));

            var modifier = new AbilityModifierController(ability, modifierData);
            var replaced = holder.getServerBuffModifiers().put(uid, modifier);
            if (replaced != null) replaced.detach(holder);
            modifier.attach(holder);
        } else if (modChange.getAction() == ModifierAction.REMOVED) {
            var modifier = holder.getServerBuffModifiers().remove(uid);
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
            logger.warn("Server buff {} not found", head.getServerBuffUid());
            return null;
        }
        var abilityName = buff.getBuffData().getAbilityName();
        var modifierName = buff.getBuffData().getModifierName();

        var owner = this.player.getScene().getEntityById(head.getTargetId());
        if (owner == null) {
            logger.warn("Server buff {} ability owner not found: {}", head.getServerBuffUid(), head.getTargetId());
            return null;
        }

        var ability = owner.getInstancedAbilities().get(head.getInstancedAbilityId());
        var invokeAbilityName = ability == null ? null : ability.getName();
        var modifierNames = ability == null ? List.<String>of() : ability.getData().getModifierNames();
        var invokeModifierName = modifierLocalId >= 0 && modifierLocalId < modifierNames.size() ? modifierNames.get(modifierLocalId) : null;
        if (abilityName.equals(invokeAbilityName) && modifierName.equals(invokeModifierName)) {
            return ability;
        }

        logger.warn("Server buff {} ({}) is {} / {}, but the invoke points to {} / {} (instanced ability {}, modifier {}) on {}: wrong ability set?",
            head.getServerBuffUid(), buff.getBuffData().getId(), abilityName, modifierName,
            invokeAbilityName, invokeModifierName, head.getInstancedAbilityId(), modifierLocalId, getEntityIdentity(owner));
        return owner.getInstancedAbilities().values().stream()
            .filter(a -> abilityName.equals(a.getName()))
            .findFirst().orElse(null);
    }

    private void handleGenerateElemBall(AbilityInvokeEntry invoke) {

    }

    private void handleGlobalFloatValue(AbilityInvokeEntry invoke) {
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        if(entity == null) {
            logger.warn("handleGlobalFloatValue entity not found: {}", invoke.getEntityId());
            return;
        }

        var entry = AbilityScalarValueEntry.parseBy(invoke.getAbilityData(), player.getSession().getVersion());
        if(entry == null) return;

        // FIXME: the key is a global value name, not an ability name, so a hashed key never resolves here:
        //  the ability hash list only holds ability names
        var key = Ability.getAbilityName(entry.getKey());
        if(key == null) return;

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

    }

    private void handleAddNewAbility(AbilityInvokeEntry invoke) {
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());

        if(entity == null) {
            logger.info("handleAddNewAbility entity not found: {}", invoke.getEntityId());
            return;
        }

        var meta = AbilityMetaAddAbility.parseBy(invoke.getAbilityData(), player.getSession().getVersion());

        // assumption: ability name is not hashed
        var abilityName = Ability.getAbilityName(meta.getAbility().getAbilityName());
        var abilityData = abilityName == null? null: GameData.getAbilityConfigs().ability(abilityName);
        if(abilityData == null) {
            logger.info("handleAddNewAbility ability not found: {}", abilityName);
            return;
        }

        var instancedAbilityId = meta.getAbility().getInstancedAbilityId();
        var ability = entity.getInstancedAbilities().computeIfAbsent(
            instancedAbilityId,
            (id) -> new Ability(abilityData, entity, player)
        );

        meta.getAbility().getOverrideMap()
            .forEach(entry -> setAbilityOverrideValue(ability, entry));

        logger.debug("Ability added to entity {} at index {}", entity.getId(), instancedAbilityId);
    }

    private void handleRemoveAbility(AbilityInvokeEntry invoke) {
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        if (entity == null) return;
        Optional.ofNullable(invoke.getHead())
            .map(AbilityInvokeEntryHead::getInstancedAbilityId)
            .ifPresent(id -> entity.getInstancedAbilities().remove((int)id));
    }

    /**
     * Invoked when an entity triggered an elemental reaction.
     * @param invoke Holds information of elemental reaction, attacker and target.
     */
    private void handleTriggerElementReaction(AbilityInvokeEntry invoke) {
        if (getPlayer().getScene() == null) return;

        AbilityMetaTriggerElementReaction data = AbilityMetaTriggerElementReaction.parseBy(invoke.getAbilityData(), player.getSession().getVersion());
        GameEntity targetEntity = getPlayer().getScene().getEntityById(invoke.getEntityId());
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
            entity.getInstancedAbilities().put(id, ability);
        }
    }
}

