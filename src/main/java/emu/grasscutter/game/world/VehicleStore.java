package emu.grasscutter.game.world;

import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.game.entity.create_config.CreateGadgetEntityConfig;
import emu.grasscutter.utils.Position;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.val;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * The vehicles kept by the world per scene, beyond the scene's lifetime: a scene is torn down when its last player
 * leaves, and on its next creation it gets copies of its vehicles back.
 * Simplified for now: the official server keeps skiffs indefinitely, and saurians until the daily or weekly reset.
 * Only skiffs and saurians are kept, other vehicle types live and die with their scene.
 */
public class VehicleStore {
    /** Reasonable limit, likely not the official one */
    public static final int MAX_NATSAURUS_PER_SCENE = 10;

    /** Scene id -> owner uid -> skiff. TODO: one per scene in practice, co-op isn't handled */
    private final Int2ObjectMap<Int2ObjectMap<EntityVehicle>> skiffs = new Int2ObjectOpenHashMap<>();
    /** Scene id -> saurians, the oldest first */
    private final Int2ObjectMap<LinkedList<EntityVehicle>> natsauruses = new Int2ObjectOpenHashMap<>();

    /**
     * Keeps a vehicle, if its type is kept.
     * @return The vehicles it evicts, to be killed: the owner's previous skiff, or the oldest saurians over the limit
     * (unoccupied first)
     */
    public synchronized List<EntityVehicle> add(EntityVehicle vehicle) {
        val sceneId = vehicle.getScene().getId();
        val evicted = new ArrayList<EntityVehicle>();
        switch (vehicle.getVehicleType()) {
            case Skiff -> {
                val previous = skiffs.computeIfAbsent(sceneId, k -> new Int2ObjectOpenHashMap<>())
                    .put(vehicle.getOwner().getUid(), vehicle);
                if (previous != null && previous != vehicle) evicted.add(previous);
            }
            case Natsaurus -> {
                val list = natsauruses.computeIfAbsent(sceneId, k -> new LinkedList<>());
                list.add(vehicle);
                while (list.size() > MAX_NATSAURUS_PER_SCENE) {
                    val oldest = list.stream().filter(v -> v.getVehicleMembers().isEmpty()).findFirst()
                        .orElse(list.getFirst());
                    list.remove(oldest);
                    evicted.add(oldest);
                }
            }
            default -> {}
        }
        return evicted;
    }

    public synchronized void remove(EntityVehicle vehicle) {
        val sceneId = vehicle.getScene().getId();

        val sceneSkiffs = skiffs.get(sceneId);
        if (sceneSkiffs != null) sceneSkiffs.values().remove(vehicle);

        val sceneNatsauruses = natsauruses.get(sceneId);
        if (sceneNatsauruses != null) sceneNatsauruses.remove(vehicle);
    }

    /**
     * Gives a newly created scene its kept vehicles, as copies in it: an entity can't move to another scene.
     * Nobody is inside them, the members left with the previous scene.
     */
    public synchronized void restore(Scene scene) {
        val sceneSkiffs = skiffs.get(scene.getId());
        if (sceneSkiffs != null) {
            sceneSkiffs.replaceAll((uid, vehicle) -> copyTo(scene, vehicle));
        }

        val sceneNatsauruses = natsauruses.get(scene.getId());
        if (sceneNatsauruses != null) {
            sceneNatsauruses.replaceAll(vehicle -> copyTo(scene, vehicle));
        }
    }

    private static EntityVehicle copyTo(Scene scene, EntityVehicle vehicle) {
        val createConfig = new CreateGadgetEntityConfig(vehicle.getGadgetId())
            .setBornPos(new Position(vehicle.getPosition()))
            .setBornRot(new Position(vehicle.getRotation()))
            .setPlayerOwner(vehicle.getOwner());
        val copy = new EntityVehicle(scene, createConfig);

        copy.setCurStamina(vehicle.getCurStamina());
        copy.setCurPhlogiston(vehicle.getCurPhlogiston());
        copy.setTransformEntityId(vehicle.getTransformEntityId());

        scene.addEntityDirectly(copy);
        return copy;
    }
}
