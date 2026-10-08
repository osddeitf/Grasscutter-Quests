package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.common.quest.SubQuestData.QuestExecParam;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestValueExec;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import lombok.val;

@QuestValueExec(QuestExec.QUEST_EXEC_SET_WEATHER_GADGET)
public class ExecSetWeatherGadget extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestExecParam condition, String... paramStr) {
        val weatherAreaId = Integer.parseInt(paramStr[0]);
        val openWeather = Integer.parseInt(paramStr[1]) == 1 ? true : false; //0 -> remove weather area, 1-> add weather area

        return quest.getOwner().getScene().setWeatherAreaEnabled(weatherAreaId, openWeather);
    }

}
