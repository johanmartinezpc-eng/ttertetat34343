package com.example.rodtricks;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class RodTricksClient implements ClientModInitializer {

    // ---- Ajustes ----
    private static final double SILVERFISH_JUMP = 1.05; // ~6.5 bloques de altura
    private static final int HOLD_THRESHOLD = 6;        // ticks (0.3 s) para distinguir tap de mantener
    private static final double DASH_BASE = 0.6;
    private static final double DASH_PER_BLOCK = 0.15;
    private static final double DASH_MAX = 2.0;
    private static final double PULL_SPEED = 0.25;      // empuje por tick al mantener
    private static final double STOP_DISTANCE = 1.5;

    private static int holdTicks = 0;
    private static boolean selfUse = false; // true mientras el propio mod usa la cana

    private static InteractionHand rodHand(LocalPlayer p) {
        if (p.getMainHandItem().is(Items.FISHING_ROD)) return InteractionHand.MAIN_HAND;
        if (p.getOffhandItem().is(Items.FISHING_ROD)) return InteractionHand.OFF_HAND;
        return null;
    }

    /** Usa la cana (lanzar o recoger) saltandose el bloqueo del click derecho. */
    private static void useRod(Minecraft mc, LocalPlayer p) {
        InteractionHand hand = rodHand(p);
        if (hand == null || mc.gameMode == null) return;
        selfUse = true;
        try {
            mc.gameMode.useItem(p, hand);
        } finally {
            selfUse = false;
        }
    }

    @Override
    public void onInitializeClient() {
        ClientTickEvents.START_CLIENT_TICK.register(RodTricksClient::onStartTick);
        ClientTickEvents.END_CLIENT_TICK.register(RodTricksClient::onEndTick);

        // Click derecho con cana: bloqueado (solo se usa para endermites). Solo en el cliente.
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level.isClientSide() && !selfUse && player.getItemInHand(hand).is(Items.FISHING_ROD)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // Con la cana lanzada, mantener click derecho no debe interactuar con bloques.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() && player.getItemInHand(hand).is(Items.FISHING_ROD)
                    && player instanceof LocalPlayer lp && lp.fishing != null) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // Mantener click izquierdo con cana no rompe bloques.
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (player.getMainHandItem().is(Items.FISHING_ROD)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    /** Click izquierdo = lanzar/recoger la cana. Se consume antes de que vanilla lo trate como ataque. */
    private static void onStartTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null) return;
        if (rodHand(p) == null) return;
        while (mc.options.keyAttack.consumeClick()) {
            useRod(mc, p);
        }
    }

    private static void onEndTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null) { holdTicks = 0; return; }

        var hook = p.fishing;
        if (hook == null || rodHand(p) == null) { holdTicks = 0; return; }

        Entity hooked = hook.getHookedIn();
        if (hooked == null) { holdTicks = 0; return; }

        // ---- Silverfish: recoger solo + gran salto ----
        if (hooked.getType() == EntityTypes.SILVERFISH) {
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x, SILVERFISH_JUMP, v.z);
            p.fallDistance = 0;
            useRod(mc, p);
            holdTicks = 0;
            return;
        }

        // ---- Endermite ----
        if (hooked.getType() == EntityTypes.ENDERMITE && hooked.isAlive()) {
            Vec3 toMite = hooked.position().add(0, hooked.getBbHeight() / 2.0, 0).subtract(p.getEyePosition());
            double dist = toMite.length();
            boolean down = mc.options.keyUse.isDown();

            if (down) {
                holdTicks++;
                if (holdTicks > HOLD_THRESHOLD) {
                    // Mantener: arrastre gradual
                    if (dist < STOP_DISTANCE) {
                        useRod(mc, p);
                        holdTicks = 0;
                        return;
                    }
                    Vec3 push = toMite.normalize().scale(PULL_SPEED);
                    p.setDeltaMovement(p.getDeltaMovement().scale(0.6).add(push));
                    p.fallDistance = 0;
                }
            } else if (holdTicks > 0) {
                if (holdTicks <= HOLD_THRESHOLD) {
                    // Click rapido: impulso directo hacia el endermite
                    double speed = Math.min(DASH_MAX, DASH_BASE + dist * DASH_PER_BLOCK);
                    p.setDeltaMovement(toMite.normalize().scale(speed));
                    p.fallDistance = 0;
                }
                // Tap o soltar tras mantener: se recoge la cana
                useRod(mc, p);
                holdTicks = 0;
            }
        } else {
            holdTicks = 0;
        }
    }
}
