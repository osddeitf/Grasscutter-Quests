package emu.grasscutter.game.world;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.types.ObjectId;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.annotations.Indexed;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.Player;
import lombok.Getter;

@Entity(value = "scene_datas", useDiscriminator = false)
public class SceneInstanceData {
    @Id private ObjectId id;

    @Indexed private int ownerUid;
    @Getter private int sceneId;

    /** Weather areas turned on (true) or off (false) by scripts and quests, the others are as their isDefaultValid */
    private Map<Integer, Boolean> weatherAreaStates;
    /** Legacy: the weather areas turned on, with their climate, read as turned on */
    @Deprecated private Map<Integer, Integer> weatherAreas;

    public SceneInstanceData(Scene scene, Player owner) {
        this.sceneId = scene.getId();
        this.ownerUid = owner.getUid();

        this.weatherAreaStates = new ConcurrentHashMap<>();
        this.weatherAreas = new ConcurrentHashMap<>();
    }

    @Deprecated  // Morphia only!
    SceneInstanceData(){
        this.weatherAreaStates = new ConcurrentHashMap<>();
        this.weatherAreas = new ConcurrentHashMap<>();
    }

    public void save() {
        DatabaseHelper.saveSceneInstanceData(this);
    }

    /** @return The area's state set by a script or quest, null if none */
    public synchronized Boolean getWeatherAreaState(int areaId) {
        migrateLegacyWeatherAreas();
        return this.weatherAreaStates.get(areaId);
    }

    public synchronized void setWeatherAreaState(int areaId, boolean on) {
        migrateLegacyWeatherAreas();
        this.weatherAreaStates.put(areaId, on);
    }

    private void migrateLegacyWeatherAreas() {
        if (this.weatherAreaStates == null) this.weatherAreaStates = new ConcurrentHashMap<>();
        if (this.weatherAreas == null || this.weatherAreas.isEmpty()) return;
        this.weatherAreas.keySet().forEach(areaId -> this.weatherAreaStates.putIfAbsent(areaId, true));
        this.weatherAreas.clear();
    }
}
