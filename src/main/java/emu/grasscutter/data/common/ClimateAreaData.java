package emu.grasscutter.data.common;

import emu.grasscutter.utils.Position;
import lombok.Data;
import teyvat.ability.config.enums.ClimateType;

/** A climate area of a scene (Scripts/Scene/&lt;id&gt;/scene&lt;id&gt;_climate_areas.json): a polygon on x/z, between bottom and top */
@Data
@SuppressWarnings(value = "SpellCheckingInspection")
public class ClimateAreaData {
    public int area_id;
    public ClimateType climateType = ClimateType.Normal;
    public Point[] points;
    public float top;
    public float bottom;

    @Data
    public static class Point {
        public float x;
        public float y;  // the world z
    }

    public boolean isInside(Position position) {
        if (points == null || position.getY() < bottom || position.getY() > top) return false;

        // Ray casting on x/z, as WeatherAreaPointData
        float posX = position.getX();
        float posZ = position.getZ();
        boolean result = false;
        for (int i = 0, j = points.length - 1; i < points.length; j = i++) {
            if ((points[i].y > posZ) != (points[j].y > posZ) &&
                (posX < (points[j].x - points[i].x) * (posZ - points[i].y) / (points[j].y - points[i].y) + points[i].x)) {
                result = !result;
            }
        }
        return result;
    }
}
