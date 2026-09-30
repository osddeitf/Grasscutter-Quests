package emu.grasscutter.game.ability.actions;

import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.quest.enums.QuestContent;
import teyvat.ability.config.actions.AvatarSkillStart;

public class ActionAvatarSkillStart extends AbilityActionHandler<AvatarSkillStart> {
    @Override
    public Class<?> getType() {
        return AvatarSkillStart.class;
    }

    @Override
    public boolean execute(Ability ability, AvatarSkillStart action, byte[] abilityData, GameEntity<?> target) {
        GameEntity owner = ability.getOwner();
        if(owner instanceof EntityAvatar avatar) {
            avatar.getPlayer().getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_SKILL, action.skillID);
        } else {
            logger.warn("AvatarSkillStart not implemented for other entities than EntityAvatar right now");

            return false;
        }

        return true;
    }
}
