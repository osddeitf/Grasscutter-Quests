package emu.grasscutter.game.modules;

import dev.morphia.annotations.*;
import org.bson.types.ObjectId;

@Entity(value = GameModuleData.COLLECTION)
@Indexes(
    @Index(
        fields = { @Field("ownerId"), @Field("_t") },
        options = @IndexOptions(disableValidation = true)
    )
)
public abstract class GameModuleData {
    public static final String COLLECTION = "modules";

    // for morphia only
    @Id
    protected ObjectId id;

    // persistence key
    protected int ownerId;

    public GameModuleData(int ownerId) {
        this.ownerId = ownerId;
    }
}
