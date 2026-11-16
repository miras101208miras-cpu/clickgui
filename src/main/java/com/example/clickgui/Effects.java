package com.example.clickgui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.*;

/** Визуальные эффекты. Рисуются с проверкой глубины: сквозь стены не видны. */
public final class Effects {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private record Circle(Vec3d pos, long start) {}
    private record TrailPoint(Vec3d pos, long time) {}

    private static final List<Circle> circles = new ArrayList<>();
    private static final ArrayDeque<TrailPoint> trail = new ArrayDeque<>();
    private static LivingEntity target;
    private static long targetTime;
    private static boolean wasOnGround = true;
    private static Vec3d cam = Vec3d.ZERO;
    private static Matrix4f mat;
    private static long now;
    private static final Map<LivingEntity, Long> attacked = new WeakHashMap<>();
    private static boolean fbApplied;
    private static int boltId = -1_000_000;

    public static void init() {
        // цель = существо, которое ты ударил или на которое смотришь
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (entity instanceof LivingEntity le) {
                target = le; targetTime = System.currentTimeMillis();
                attacked.put(le, targetTime);
                FxParticles.onHit(le);
            }
            return ActionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(c -> tick());
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(Effects::render);
    }

    private static void tick() {
        ClientPlayerEntity p = mc.player;
        if (p == null) { circles.clear(); trail.clear(); target = null; return; }
        long t = System.currentTimeMillis();

        boolean ground = p.isOnGround();
        if (ModuleManager.get("Jump Circles").enabled && wasOnGround && !ground && p.getVelocity().y > 0)
            circles.add(new Circle(p.getEntityPos(), t));
        wasOnGround = ground;

        Module tr = ModuleManager.get("Trails");
        if (tr.enabled) {
            Vec3d pos = p.getEntityPos();
            if (!trail.isEmpty() && trail.peekLast().pos().distanceTo(pos) > 4) trail.clear();
            trail.addLast(new TrailPoint(pos, t));
            long life = (long) (tr.num("Length") * 1000);
            while (!trail.isEmpty() && t - trail.peekFirst().time() > life) trail.pollFirst();
        } else trail.clear();

        if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) {
            target = le; targetTime = t;
        }
        if (target != null && (target.isRemoved() || !target.isAlive() || t - targetTime > 3000)) target = null;

        HitColor.update();

        // ---- Full Bright: ночное зрение без иконки, только на клиенте ----
        if (ModuleManager.get("Full Bright").enabled) {
            StatusEffectInstance cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (cur == null || !cur.isInfinite()) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, StatusEffectInstance.INFINITE, 0, false, false, false));
                fbApplied = true;
            }
        } else if (fbApplied) {
            p.removeStatusEffect(StatusEffects.NIGHT_VISION);
            fbApplied = false;
        }

        // ---- Kill Effect: смерть существа, которое ты бил последние 5 сек ----
        Iterator<Map.Entry<LivingEntity, Long>> it = attacked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Long> en = it.next();
            LivingEntity le = en.getKey();
            if (le.isDead() || le.deathTime > 0) { killEffect(le); it.remove(); }
            else if (le.isRemoved() || t - en.getValue() > 5000) it.remove();
        }
    }

    private static void killEffect(LivingEntity le) {
        Module m = ModuleManager.get("Kill Effect");
        if (!m.enabled || mc.world == null) return;
        Vec3d pos = le.getEntityPos();
        if (m.has("Effect", "Lightning")) {
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(mc.world, SpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.setId(boltId--); // отрицательный id, чтобы не пересечься с серверными
                bolt.refreshPositionAfterTeleport(pos);
                bolt.setCosmetic(true);
                mc.world.addEntity(bolt);
            }
        }
        if (m.has("Effect", "Particles")) {
            FxParticles.burst(pos.add(0, le.getHeight() * 0.5, 0), 40, null, 5.0);
            mc.particleManager.addParticle(ParticleTypes.EXPLOSION, pos.x, pos.y + 1, pos.z, 0, 0, 0);
        }
    }

    private static void render(WorldRenderContext ctx) {
        if (mc.player == null) return;
        now = System.currentTimeMillis();
        float td = mc.getRenderTickCounter().getTickProgress(true);
        cam = ctx.worldState().cameraRenderState.pos;
        mat = ctx.matrices().peek().getPositionMatrix();
        VertexConsumerProvider vcp = ctx.consumers();

        VertexConsumer glow = vcp.getBuffer(RenderLayers.lightning()); // аддитивное свечение
        Module esp = ModuleManager.get("Target ESP");
        if (esp.enabled && target != null) drawTarget(glow, esp, target, td);
        if (ModuleManager.get("Trails").enabled) drawTrail(glow, td);
        drawCircles(glow);

        Module hat = ModuleManager.get("China Hat");
        if (hat.enabled && !mc.options.getPerspective().isFirstPerson())
            drawHat(vcp.getBuffer(RenderLayers.debugQuads()), hat, mc.player, td);

        FxParticles.render(vcp, cam, mat, ctx.worldState().cameraRenderState.orientation, now);
    }

    // ---------------- Target ESP ----------------
    private static void drawTarget(VertexConsumer vc, Module m, LivingEntity e, float td) {
        Vec3d c = e.getLerpedPos(td);
        double h = e.getHeight(), r = e.getWidth() * 0.75 + 0.15;
        double t = now / 1000.0 * m.num("Speed");
        int seg = 48;
        if (m.has("Style", "Ring")) {
            double y = c.y + (Math.sin(t * 2.5) + 1) / 2 * h;
            double tail = -Math.cos(t * 2.5) * 0.35; // шлейф тянется за движением кольца
            for (int i = 0; i < seg; i++) {
                double a1 = Math.PI * 2 * i / seg, a2 = Math.PI * 2 * (i + 1) / seg;
                Vec3d p1 = new Vec3d(c.x + Math.cos(a1) * r, y, c.z + Math.sin(a1) * r);
                Vec3d p2 = new Vec3d(c.x + Math.cos(a2) * r, y, c.z + Math.sin(a2) * r);
                int c1 = color(off(a1), 220), c2 = color(off(a2), 220);
                quad(vc, p1, p2, p2.add(0, tail, 0), p1.add(0, tail, 0), c1, c2, c2 & 0xFFFFFF, c1 & 0xFFFFFF);
            }
        }
        if (m.has("Style", "Orbit")) {
            for (int k = 0; k < 3; k++) {
                double base = t * 3 + k * Math.PI * 2 / 3;
                double y = c.y + h * (0.5 + 0.3 * Math.sin(t * 2 + k * 2));
                int steps = 16;
                for (int i = 0; i < steps; i++) {
                    double a1 = base - i * 0.12, a2 = base - (i + 1) * 0.12;
                    double f1 = 1 - (double) i / steps, f2 = 1 - (double) (i + 1) / steps;
                    double w1 = 0.06 * f1 + 0.01, w2 = 0.06 * f2 + 0.01;
                    Vec3d p1 = new Vec3d(c.x + Math.cos(a1) * r, y, c.z + Math.sin(a1) * r);
                    Vec3d p2 = new Vec3d(c.x + Math.cos(a2) * r, y, c.z + Math.sin(a2) * r);
                    int c1 = color(k / 3f, (int) (230 * f1)), c2 = color(k / 3f, (int) (230 * f2));
                    quad(vc, p1.add(0, w1, 0), p2.add(0, w2, 0), p2.add(0, -w2, 0), p1.add(0, -w1, 0), c1, c2, c2, c1);
                }
            }
        }
    }

    // ---------------- Jump Circles ----------------
    private static void drawCircles(VertexConsumer vc) {
        Module m = ModuleManager.get("Jump Circles");
        double life = m.num("Time") * 1000, maxR = m.num("Radius");
        circles.removeIf(cl -> now - cl.start() > life);
        int seg = 48;
        for (Circle cl : circles) {
            double age = (now - cl.start()) / life;
            double r = maxR * (1 - Math.pow(1 - age, 3)), in = r * 0.75;
            int alpha = (int) (220 * (1 - age));
            Vec3d c = cl.pos();
            double y = c.y + 0.02;
            for (int i = 0; i < seg; i++) {
                double a1 = Math.PI * 2 * i / seg, a2 = Math.PI * 2 * (i + 1) / seg;
                Vec3d o1 = new Vec3d(c.x + Math.cos(a1) * r, y, c.z + Math.sin(a1) * r);
                Vec3d o2 = new Vec3d(c.x + Math.cos(a2) * r, y, c.z + Math.sin(a2) * r);
                Vec3d i1 = new Vec3d(c.x + Math.cos(a1) * in, y, c.z + Math.sin(a1) * in);
                Vec3d i2 = new Vec3d(c.x + Math.cos(a2) * in, y, c.z + Math.sin(a2) * in);
                int c1 = color(off(a1), alpha), c2 = color(off(a2), alpha);
                quad(vc, i1, o1, o2, i2, c1 & 0xFFFFFF, c1, c2, c2 & 0xFFFFFF);
            }
        }
    }

    // ---------------- Trails ----------------
    private static void drawTrail(VertexConsumer vc, float td) {
        Module m = ModuleManager.get("Trails");
        double life = m.num("Length") * 1000, hgt = m.num("Height");
        List<TrailPoint> pts = new ArrayList<>(trail);
        pts.add(new TrailPoint(mc.player.getLerpedPos(td), now));
        for (int i = 0; i < pts.size() - 1; i++) {
            TrailPoint a = pts.get(i), b = pts.get(i + 1);
            if (a.pos().squaredDistanceTo(b.pos()) < 1e-6) continue;
            double fa = clamp(1 - (now - a.time()) / life), fb = clamp(1 - (now - b.time()) / life);
            int ta = color(timeOff(a.time()), (int) (200 * fa)), tb = color(timeOff(b.time()), (int) (200 * fb));
            int ba = (ta & 0xFFFFFF) | ((int) (50 * fa) << 24), bb = (tb & 0xFFFFFF) | ((int) (50 * fb) << 24);
            quad(vc, a.pos().add(0, 0.05, 0), b.pos().add(0, 0.05, 0), b.pos().add(0, hgt, 0), a.pos().add(0, hgt, 0),
                    ba, bb, tb, ta);
        }
    }

    // ---------------- China Hat ----------------
    private static void drawHat(VertexConsumer vc, Module m, Entity e, float td) {
        Vec3d c = e.getLerpedPos(td);
        double top = c.y + e.getHeight() + 0.02, r = m.num("Radius"), h = m.num("Height");
        Vec3d apex = new Vec3d(c.x, top + h, c.z);
        double spin = now / 2000.0;
        int seg = 32, ca = color(0.5f, 170);
        for (int i = 0; i < seg; i++) {
            double a1 = spin + Math.PI * 2 * i / seg, a2 = spin + Math.PI * 2 * (i + 1) / seg;
            Vec3d p1 = new Vec3d(c.x + Math.cos(a1) * r, top, c.z + Math.sin(a1) * r);
            Vec3d p2 = new Vec3d(c.x + Math.cos(a2) * r, top, c.z + Math.sin(a2) * r);
            int c1 = color(off(a1 - spin), 110), c2 = color(off(a2 - spin), 110);
            quad(vc, apex, p2, p1, p1, ca, c2, c1, c1); // треугольник как вырожденный quad
        }
    }

    // ---------------- утилиты ----------------
    private static void v(VertexConsumer vc, Vec3d p, int argb) {
        vc.vertex(mat, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).color(argb);
    }

    /** Двусторонний quad. */
    private static void quad(VertexConsumer vc, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int ca, int cb, int cc, int cd) {
        v(vc, a, ca); v(vc, b, cb); v(vc, c, cc); v(vc, d, cd);
        v(vc, d, cd); v(vc, c, cc); v(vc, b, cb); v(vc, a, ca);
    }

    private static float off(double angle) { return (float) (0.5 - 0.5 * Math.cos(angle)); }
    private static float timeOff(long t) { return (float) (0.5 + 0.5 * Math.sin(t / 500.0)); }
    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    /** Цвет из текущей темы. */
    static int color(float offset, int alpha) { return Theme.color(now, offset, alpha); }
}
