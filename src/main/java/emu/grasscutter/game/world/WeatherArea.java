package emu.grasscutter.game.world;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.WeatherData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.Player.SceneLoadState;
import emu.grasscutter.game.props.ClimateType;
import emu.grasscutter.server.packet.send.PacketSceneAreaWeatherNotify;
import lombok.Getter;
import lombok.val;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * A weather area of a scene with a player inside, created when one enters, released by the scene once empty.
 * Its weather starts as the area's default one, then changes randomly by its template (WeatherTemplateExcelConfigData,
 * the chances of the next weather from the current one), unless fixed to the default one (isUseDefault, or no
 * template).
 */
public class WeatherArea {
    /** Scene seconds between the random weather changes */
    public static final int CHANGE_INTERVAL = 240;  // about 4 in-game hours
    /** Upcoming weathers, as asked by SceneWeatherForecastReq */
    public static final int FORECAST_SIZE = 12;

    @Getter private final Scene scene;
    @Getter private final WeatherData config;
    @Getter private ClimateType climateType;
    @Getter private float transDuration = 0;
    @Getter private final List<Player> players = new CopyOnWriteArrayList<>();
    /** Scene seconds since which the area is empty, -1 while a player is inside */
    @Getter private int emptySince = -1;

    private final boolean fixed;
    private int nextChangeTime;
    private final List<ClimateType> forecast = new ArrayList<>();

    public WeatherArea(Scene scene, WeatherData config) {
        this.scene = scene;
        this.config = config;
        this.climateType = config.getDefaultClimate();
        this.nextChangeTime = scene.getSceneTimeSeconds() + CHANGE_INTERVAL;

        boolean hasTemplate = hasTemplate(config.getTemplateName());
        if (!config.isUseDefault() && !hasTemplate) {
            Grasscutter.getLogger().warn("Weather area {} template {} not found, fixed to its default climate {}",
                config.getAreaID(), config.getTemplateName(), config.getDefaultClimate());
        }
        this.fixed = config.isUseDefault() || !hasTemplate;
        Grasscutter.getLogger().debug("Weather area {} created in scene {}: {}, template {}{}", config.getAreaID(), scene.getId(),
            this.climateType, config.getTemplateName(), this.fixed ? ", fixed" : "");
    }

    private static boolean hasTemplate(String templateName) {
        if (templateName == null) return false;
        return IntStream.rangeClosed(ClimateType.CLIMATE_SUNNY.getValue(), ClimateType.CLIMATE_MIST.getValue())
            .anyMatch(value -> GameData.getWeatherTemplateDataByNameMap().containsKey(templateName + value));
    }

    public int getAreaId() {
        return this.config.getAreaID();
    }

    public ClimateType getCurrentClimateType() {
        return this.climateType;
    }

    /** Sets the weather, notifying the players inside if it changed, e.g. by /weather */
    public void setClimateType(ClimateType type) {
        if (type == this.climateType) return;
        Grasscutter.getLogger().debug("Weather area {} climate {} -> {}, {} player(s)", getAreaId(), this.climateType, type, this.players.size());
        this.climateType = type;
        for (Player player : this.players) {
            if (player.getSceneLoadState().getValue() >= SceneLoadState.INIT.getValue()) {
                player.sendPacket(new PacketSceneAreaWeatherNotify(getAreaId(), type, this.transDuration));
            }
        }
    }

    public boolean enterArea(Player player) {
        this.emptySince = -1;
        if (this.players.contains(player)) return false;
        this.players.add(player);
        return true;
    }

    public boolean leaveArea(Player player) {
        boolean removed = this.players.remove(player);
        if (this.players.isEmpty() && this.emptySince < 0) {
            this.emptySince = this.scene.getSceneTimeSeconds();
        }
        return removed;
    }

    /** The area is turned off: its players are told they're out of any area */
    public void remove() {
        for (Player player : this.players) {
            if (player.getSceneLoadState().getValue() >= SceneLoadState.INIT.getValue()) {
                player.sendPacket(new PacketSceneAreaWeatherNotify(0, ClimateType.CLIMATE_NONE, 0));
            }
            player.setWeatherAreaId(0);
        }
        this.players.clear();
    }

    /** The random weather changes, every CHANGE_INTERVAL scene seconds, unless fixed or locked by the world */
    public void onTick(int sceneTime) {
        if (this.fixed || this.scene.getWorld().isWeatherLocked() || sceneTime < this.nextChangeTime) return;

        this.nextChangeTime = sceneTime + CHANGE_INTERVAL;
        fillForecast();
        var next = this.forecast.remove(0);
        Grasscutter.getLogger().debug("Weather area {} change at scene time {}: {} -> {}, forecast {}", getAreaId(), sceneTime,
            this.climateType, next, this.forecast);
        setClimateType(next);
    }

    /** The upcoming weathers, the next change being the first one */
    public List<Integer> getForcastList() {
        fillForecast();
        return this.forecast.stream().map(ClimateType::getValue).toList();
    }

    private void fillForecast() {
        while (this.forecast.size() < FORECAST_SIZE) {
            var last = this.forecast.isEmpty() ? this.climateType : this.forecast.get(this.forecast.size() - 1);
            this.forecast.add(this.fixed ? this.climateType : randWeather(last));
        }
    }

    /** The next weather from the current one, by the template's chances (cumulative), the default one if none */
    private ClimateType randWeather(ClimateType current) {
        val template = GameData.getWeatherTemplateDataByNameMap().get(this.config.getTemplateName() + current.getValue());
        if (template == null) return this.config.getDefaultClimate();

        float[] chances = {template.getSunnyProb(), template.getCloudyProb(), template.getRainProb(),
            template.getThunderstormProb(), template.getSnowProb(), template.getMistProb()};
        ClimateType[] types = {ClimateType.CLIMATE_SUNNY, ClimateType.CLIMATE_CLOUDY, ClimateType.CLIMATE_RAIN,
            ClimateType.CLIMATE_THUNDERSTORM, ClimateType.CLIMATE_SNOW, ClimateType.CLIMATE_MIST};

        float total = 0;
        for (float chance : chances) total += chance;
        if (total <= 0) return this.config.getDefaultClimate();

        float roll = ThreadLocalRandom.current().nextFloat() * total;
        for (int i = 0; i < chances.length; i++) {
            roll -= chances[i];
            if (roll < 0) return types[i];
        }
        return types[types.length - 1];
    }
}
