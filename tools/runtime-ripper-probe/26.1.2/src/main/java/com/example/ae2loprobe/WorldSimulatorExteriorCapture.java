package com.example.ae2loprobe;

import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/** Native cardinal and oblique renderer probe for the isolated north-facing block. */
final class WorldSimulatorExteriorCapture {
    private static final double DISTANCE = 2.45;
    private static final int SETTLE_TICKS = 12;
    private final EnumSide[] sides = EnumSide.values();
    private int index;
    private int remaining;
    private int waiting;
    private int refillTick;
    private boolean requested;
    private Entity previousCamera;
    private ArmorStand camera;
    private CameraType previousCameraType;
    private boolean previousHideGui;
    private int previousFov;

    private enum EnumSide {
        FRONT("front", Direction.NORTH, 0, 0),
        RIGHT("right", Direction.EAST, 90, 0),
        BACK("back", Direction.SOUTH, 180, 0),
        LEFT("left", Direction.WEST, -90, 0),
        TOP("top", Direction.UP, 180, 90),
        BOTTOM("bottom", Direction.DOWN, 180, -90),
        OBLIQUE("oblique", null, 23, 33),
        FRONT_ACTIVE("front-active", Direction.NORTH, 0, 0),
        TOP_ACTIVE("top-active", Direction.UP, 180, 90),
        OBLIQUE_ACTIVE("oblique-active", null, 23, 33);

        final String file;
        final Direction direction;
        final float yaw;
        final float pitch;

        EnumSide(String suffix, Direction direction, float yaw, float pitch) {
            this.file = "ae2lo-ripper-exterior-" + suffix + ".png";
            this.direction = direction;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private boolean activeShot() {
        return index < sides.length && sides[index].ordinal() >= EnumSide.FRONT_ACTIVE.ordinal();
    }

    /** The GPU frame must show the client-synchronized ACTIVE state before the image is requested. */
    void onFrame(Consumer<String> capture) {
        if (camera == null || !activeShot() || requested || remaining != 0) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.screen != null || mc.getOverlay() != null || mc.getCameraEntity() != camera) return;
        var state = mc.level.getBlockState(WorldSimulatorFixture.EXTERIOR_POS);
        var lamp = com.example.ae2lightoptimizer.world.WorldSimulatorBlock.ACTIVE;
        if (state.hasProperty(lamp) && state.getValue(lamp)) {
            requested = true;
            capture.accept(sides[index].file);
        }
    }

    /** Call on client end tick after the UI is closed. Completes only after all screenshot callbacks. */
    boolean tick(Set<String> captured, Consumer<String> capture) {
        var mc = Minecraft.getInstance();
        if (index >= sides.length) return true;
        if (mc.level == null || mc.player == null || mc.screen != null || mc.getOverlay() != null) {
            restore(mc);
            return false;
        }
        var pos = WorldSimulatorFixture.EXTERIOR_POS;
        if (!mc.level.getBlockState(pos).is(com.example.ae2lightoptimizer.block.ModBlocks.WORLD_SIMULATOR.get())) {
            restore(mc);
            return false;
        }
        if (camera == null) {
            previousCamera = mc.getCameraEntity();
            previousCameraType = mc.options.getCameraType();
            previousHideGui = mc.options.hideGui;
            previousFov = mc.options.fov().get();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = true;
            mc.options.fov().set(43);
            // Deliberately not added to any level: no server sync, AI, hitbox or fixture mutation.
            camera = new ArmorStand(mc.level, 0, 0, 0);
            mc.setCameraEntity(camera);
            aim(Vec3.atCenterOf(pos), sides[index]);
        }
        if (captured.contains(sides[index].file)) {
            if (++index == sides.length) {
                restore(mc);
                return true;
            }
            aim(Vec3.atCenterOf(pos), sides[index]);
            return false;
        }
        if (activeShot()) {
            var server = mc.getSingleplayerServer();
            if (server != null && ++refillTick % 5 == 1) {
                server.execute(() -> WorldSimulatorFixture.maintainExteriorRecipe(server));
            }
        }
        if (remaining > 0) --remaining;
        if (remaining == 0 && !requested && !activeShot()) {
            requested = true;
            capture.accept(sides[index].file);
        } else if ((requested || activeShot()) && ++waiting > 400) {
            String missing = sides[index].file;
            restore(mc);
            throw new IllegalStateException("Native exterior screenshot did not complete: " + missing);
        }
        return false;
    }

    private void aim(Vec3 center, EnumSide face) {
        var d = face.direction;
        if (d != null) {
            camera.setPos(center.x + d.getStepX() * DISTANCE,
                          center.y + d.getStepY() * DISTANCE - camera.getEyeHeight(),
                          center.z + d.getStepZ() * DISTANCE);
        } else {
            // Upper north-east orbit: the complete front and top are visible in one native frame.
            double angle = Math.toRadians(face.yaw);
            double elevation = Math.toRadians(face.pitch);
            double orbit = 3.35;
            double horizontal = Math.cos(elevation) * orbit;
            camera.setPos(center.x + Math.sin(angle) * horizontal,
                          center.y + Math.sin(elevation) * orbit - camera.getEyeHeight(),
                          center.z - Math.cos(angle) * horizontal);
        }
        camera.setYRot(face.yaw);
        camera.setXRot(face.pitch);
        camera.setOldPosAndRot();
        // LivingEntity camera yaw interpolates the head, not the body.
        camera.setYHeadRot(face.yaw);
        camera.yHeadRotO = face.yaw;
        if (camera.getViewYRot(0.5F) != face.yaw || camera.getViewXRot(0.5F) != face.pitch) {
            throw new IllegalStateException("Native exterior camera did not face " + face.name());
        }
        remaining = SETTLE_TICKS;
        waiting = 0;
        requested = false;
    }

    private void restore(Minecraft mc) {
        if (camera == null) return;
        if (mc.getCameraEntity() == camera) {
            mc.setCameraEntity(previousCamera != null && !previousCamera.isRemoved() ? previousCamera : mc.player);
        }
        mc.options.setCameraType(previousCameraType);
        mc.options.hideGui = previousHideGui;
        mc.options.fov().set(previousFov);
        camera = null;
        previousCamera = null;
    }
}
