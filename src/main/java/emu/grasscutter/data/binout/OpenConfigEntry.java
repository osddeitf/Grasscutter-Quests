package emu.grasscutter.data.binout;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OpenConfigEntry {
	@Getter private final String name;
	@Getter private int extraTalentIndex;
	@Getter private SkillPointModifier[] skillPointModifiers;
    @Getter private final List<OpenConfigData> configTalents;

	public OpenConfigEntry(String name, OpenConfigData[] data) {
		this.name = name;
        // Unknown $type values deserialize to null; skip them instead of failing the whole entry
        this.configTalents = Arrays.stream(data)
            .filter(entry -> entry != null && entry.type != null)
            .toList();

		List<SkillPointModifier> modList = new ArrayList<>();

		for (var entry : this.configTalents) {
            switch (entry.type) {
                case AddTalentExtraLevel -> {
                    if (entry.talentIndex > 0) {
                        this.extraTalentIndex = entry.talentIndex;
                    }
                }
                case ModifySkillPoint -> {
                    modList.add(new SkillPointModifier(entry.skillID, entry.pointDelta));
                }
            }
		}

		if (modList.size() > 0) {
			this.skillPointModifiers = modList.toArray(new SkillPointModifier[0]);
		}
	}

	public static class SkillPointModifier {
		private int skillId;
		private int delta;

		public SkillPointModifier(int skillId, int delta) {
			this.skillId = skillId;
			this.delta = delta;
		}

		public int getSkillId() {
			return skillId;
		}

		public int getDelta() {
			return delta;
		}
	}
}
