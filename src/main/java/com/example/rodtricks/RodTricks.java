package com.example.rodtricks;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

public class RodTricks implements ModInitializer {
    public static final String MODID = "rodtricks";

    /** true = los endermites tambien quedan quietos (flotan en el aire si los pones ahi). */
    public static final boolean FREEZE_ENDERMITES = true;

    /** Silverfish (y endermites si FREEZE_ENDERMITES) son entidades estaticas. */
    public static boolean isFrozenType(Entity entity) {
        return entity.getType() == EntityTypes.SILVERFISH
                || (FREEZE_ENDERMITES && entity.getType() == EntityTypes.ENDERMITE);
    }

    @Override
    public void onInitialize() {
        // Sin IA = no se mueven, no atacan y no caen (huevo, /summon, bloques infestados).
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (isFrozenType(entity) && entity instanceof Mob mob) {
                mob.setNoAi(true);
            }
        });
    }
}
