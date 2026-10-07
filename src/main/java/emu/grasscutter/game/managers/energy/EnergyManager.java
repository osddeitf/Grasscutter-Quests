package emu.grasscutter.game.managers.energy;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.DataLoader;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.AvatarSkillDepotData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.MonsterData.HpDrops;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.EntityItem;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.create_config.CreateGadgetEntityConfig;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.game.player.BasePlayerManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.props.MonsterType;
import emu.grasscutter.utils.Position;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.val;
import org.anime_game_servers.multi_proto.gi.messages.ability.AbilityInvokeEntry;
import org.anime_game_servers.multi_proto.gi.messages.ability.action.AbilityActionGenerateElemBall;
import org.anime_game_servers.multi_proto.gi.messages.general.PropChangeReason;
import org.anime_game_servers.multi_proto.gi.messages.scene.entity.ChangeEnergyReason;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

public class EnergyManager extends BasePlayerManager {

    private boolean energyUsage; // Should energy usage be enabled for this player?
    private final static Int2ObjectMap<List<EnergyDropInfo>> energyDropData = new Int2ObjectOpenHashMap<>();
    private final static Int2ObjectMap<List<SkillParticleGenerationInfo>> skillParticleGenerationData = new Int2ObjectOpenHashMap<>();

    public EnergyManager(Player player) {
        super(player);
        this.energyUsage=GAME_OPTIONS.energyUsage;
    }

    public static void initialize() {
        // Read the data we need for monster energy drops.
        try {
            DataLoader.loadList("EnergyDrop.json", EnergyDropEntry.class).forEach(entry -> {
                energyDropData.put(entry.getDropId(), entry.getDropList());
            });

            Grasscutter.getLogger().debug("Energy drop data successfully loaded.");
        }
        catch (Exception ex) {
            Grasscutter.getLogger().error("Unable to load energy drop data.", ex);
        }

        // Read the data for particle generation from skills
        try {
            DataLoader.loadList("SkillParticleGeneration.json", SkillParticleGenerationEntry.class).forEach(entry -> {
                skillParticleGenerationData.put(entry.getAvatarId(), entry.getAmountList());
            });

            Grasscutter.getLogger().debug("Skill particle generation data successfully loaded.");
        }
        catch (Exception ex) {
            Grasscutter.getLogger().error("Unable to load skill particle generation data data.", ex);
        }
    }

    /**
     * Particle creation for elemental skills.
     */
    private int getBallCountForAvatar(int avatarId) {
        // We default to two particles.
        int count = 2;

        // If we don't have any data for this avatar, stop.
        if (!skillParticleGenerationData.containsKey(avatarId)) {
            Grasscutter.getLogger().warn("No particle generation data for avatarId {} found.", avatarId);
        }
        // If we do have data, roll for how many particles we should generate.
        else {
            int roll = ThreadLocalRandom.current().nextInt(0, 100);
            int percentageStack = 0;
            for (SkillParticleGenerationInfo info : skillParticleGenerationData.get(avatarId)) {
                int chance = info.getChance();
                percentageStack += chance;
                if (roll < percentageStack) {
                    count = info.getValue();
                    break;
                }
            }
        }

        // Done.
        return count;
    }

    private int getBallIdForElement(ElementType element) {
        // If we have no element, we default to an element-less particle.
        if (element == null) {
            return 2024;
        }

        // Otherwise, we determine the particle's ID based on the element.
        return switch (element) {
            case Fire -> 2017;
            case Water -> 2018;
            case Grass -> 2019;
            case Electric -> 2020;
            case Wind -> 2021;
            case Ice -> 2022;
            case Rock -> 2023;
            default -> 2024;
        };
    }

    public void handleGenerateElemBall(AbilityInvokeEntry invoke) {
        // ToDo:
        // This is also called when a weapon like Favonius Warbow etc. creates energy through its passive.
        // We are not handling this correctly at the moment.

        // Get action info.
        val action = AbilityActionGenerateElemBall.parseBy(invoke.getAbilityData(), player.getSession().getVersion());
        if (action == null) {
            return;
        }

        // Default to an elementless particle.
        int itemId = 2024;

        // Generate 2 particles by default.
        int amount = 2;

        // Try to get the casting avatar from the player's party.
        Optional<EntityAvatar> avatarEntity = this.getCastingAvatarEntityForEnergy(invoke.getEntityId());

        // Bug: invokes twice sometimes, Ayato, Keqing
        // ToDo: deal with press, hold difference. deal with charge(Beidou, Yunjin)
        if (avatarEntity.isPresent()) {
            Avatar avatar = avatarEntity.get().getAvatar();

            if (avatar != null) {
                int avatarId = avatar.getAvatarId();
                AvatarSkillDepotData skillDepotData = avatar.getSkillDepot();

                // Determine how many particles we need to create for this avatar.
                amount = this.getBallCountForAvatar(avatarId);

                // Determine the avatar's element, and based on that the ID of the
                // particles we have to generate.
                if (skillDepotData != null) {
                    ElementType element = skillDepotData.getElementType();
                    itemId = this.getBallIdForElement(element);
                }
            }
        }

        // Generate the particles.
        var pos = new Position(action.getPos());
        for (int i = 0; i < amount; i++) {
            this.generateElemBall(itemId, pos, 1);
        }
    }

    /*
     * Energy logic related to using skills.
     */

    /**
     * A skill's energy cost on AvatarSkillStart, by the action's costEnergyRatio (e.g. 0 for a free variant, as Skirk's
     * special burst):
     * - elemental energy: the skill's costElemVal, partial, as an avatar may have several bursts with different costs
     *   (e.g. Varesa 70 and 30)
     * - special energy (e.g. Mavuika, Skirk): the max special energy, for skills consuming it only (specialEnergyType),
     *   not every skill of the avatar (e.g. Mavuika's elemental skill)
     */
    public void handleSkillStartCostEnergy(EntityAvatar avatar, int skillId, float ratio) {
        if (!GAME_OPTIONS.energyUsage || !this.energyUsage) return;

        var skillData = GameData.getAvatarSkillDataMap().get(skillId);
        if (skillData == null) return;

        float baseCost;
        if (skillData.getCostElemVal() > 0) {
            baseCost = skillData.getCostElemVal();
        } else if (avatar.getAvatar().hasSpecialEnergy() && skillData.getSpecialEnergyCostType() != null
            && !skillData.getSpecialEnergyCostType().isEmpty()) {
            baseCost = avatar.getAvatar().getMaxEnergy();
        } else {
            return;
        }

        float cost = baseCost * ratio;
        if (cost <= 0) return;
        avatar.costEnergy(cost, ChangeEnergyReason.CHANGE_ENERGY_SKILL_START);
    }

    /*
     * Monster energy drops.
     */

    private void generateElemBallDrops(EntityMonster monster, int dropId) {
        // Generate all drops specified for the given drop id.
        if (!energyDropData.containsKey(dropId)) {
            Grasscutter.getLogger().warn("No drop data for dropId {} found.", dropId);
            return;
        }

        for (EnergyDropInfo info : energyDropData.get(dropId)) {
            this.generateElemBall(info.getBallId(), monster.getPosition(), info.getCount());
        }
    }

    public void handleMonsterEnergyDrop(EntityMonster monster, float hpBeforeDamage, float hpAfterDamage) {
        // Make sure this is actually a monster.
        // Note that some wildlife also has that type, like boars or birds.
        MonsterType type = monster.getMonsterData().getType();
        if (type != MonsterType.MONSTER_ORDINARY && type != MonsterType.MONSTER_BOSS) {
            return;
        }

        // Calculate the HP thresholds for before and after the damage was taken.
        float maxHp = monster.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float thresholdBefore = hpBeforeDamage / maxHp;
        float thresholdAfter = hpAfterDamage / maxHp;

        // Determine the thresholds the monster has passed, and generate drops based on that.
        for (HpDrops drop : monster.getMonsterData().getHpDrops()) {
            if (drop.getDropId() == 0) {
                continue;
            }

            float threshold = drop.getHpPercent() / 100.0f;
            if (threshold < thresholdBefore && threshold >= thresholdAfter) {
                this.generateElemBallDrops(monster, drop.getDropId());
            }
        }

        // Handle kill drops.
        if (hpAfterDamage <= 0 && monster.getMonsterData().getKillDropId() != 0) {
            this.generateElemBallDrops(monster, monster.getMonsterData().getKillDropId());
        }
    }

    /*
     * Utilities.
     */

    private void generateElemBall(int ballId, Position position, int count) {
        // Generate a particle/orb with the specified parameters.
        ItemData itemData = GameData.getItemDataMap().get(ballId);
        if (itemData == null) {
            return;
        }

        val createConfig = new CreateGadgetEntityConfig(itemData, count)
            .setBornPos(position)
            .setPlayerOwner(this.getPlayer());

        EntityItem energyBall = new EntityItem(this.getPlayer().getScene(), createConfig);
        this.getPlayer().getScene().addEntity(energyBall);
    }

    private Optional<EntityAvatar> getCastingAvatarEntityForEnergy(int invokeEntityId) {
        // To determine the avatar that has cast the skill that caused the energy particle to be generated,
        // we have to look at the entity that has invoked the ability. This can either be that avatar directly,
        // or it can be an `EntityClientGadget`, owned (some way up the owner hierarchy) by the avatar
        // that cast the skill.

        // Try to get the invoking entity from the scene.
        GameEntity entity = this.player.getScene().getEntityById(invokeEntityId);

        // Determine the ID of the entity that originally cast this skill. If the scene entity is null,
        // or not an `EntityClientGadget`, we assume that we are directly looking at the casting avatar
        // (the null case will happen if the avatar was switched out between casting the skill and the
        // particle being generated). If the scene entity is an `EntityClientGadget`, we need to find the
        // ID of the original owner of that gadget.
        int avatarEntityId =
                (!(entity instanceof EntityClientGadget entityClientGadget))
                        ? invokeEntityId
                        : entityClientGadget.getOriginalOwnerEntityId();

        // Finally, find the avatar entity in the player's team.
        return this.player.getTeamManager().getActiveTeam()
                .stream()
                .filter(character -> character.getId() == avatarEntityId)
                .findFirst();
    }

    public boolean getEnergyUsage() {
        return this.energyUsage;
    }

    public boolean refillEntityAvatarEnergy () {
        EntityAvatar activeEntity = this.player.getTeamManager().getCurrentAvatarEntity();
        return activeEntity.refillEnergy();
    }

    public void refillTeamEnergy (PropChangeReason changeReason, boolean isFlat) {
        for (EntityAvatar entityAvatar : this.player.getTeamManager().getActiveTeam()) {
            // giving the max energy of the burst (costElemVal)
            entityAvatar.addEnergy(entityAvatar.getAvatar().getMaxEnergy(), changeReason, isFlat);
        }
    }

    public void setEnergyUsage(boolean energyUsage) {
        this.energyUsage = energyUsage;
        if (!energyUsage) {  // Refill team energy if usage is disabled
            refillTeamEnergy(PropChangeReason.PROP_CHANGE_GM, true);
        }
    }
}
