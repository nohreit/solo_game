package main.java.game.entity;

import main.java.game.gfx.Animation;
import main.java.game.gfx.Camera;
import main.java.game.map.TiledMap;
import main.java.game.physics.Rect;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

public class EnemyWarrior {

    public float x, y;

    // Same “feet collider” idea as your Player
    private final int colW = 16;
    private final int colH = 10;
    private final int FOOT_OFFSET_Y = 24;

    private boolean facingLeft = false;

    // Simple AI tuning
    private float speed = 60f;          // px/sec
    private float aggroRange = 220f;    // start chasing
    private float stopRange = 44f;     // stop near player

    // Animation state
    private enum AnimationType {IDLE, RUN}

    private enum MoveType {UP, DOWN, LEFT, RIGHT, NONE}

    private AnimationType currentAnimType = AnimationType.IDLE;
    private MoveType currentMoveType = MoveType.DOWN;

    private Animation idleDownAnim, idleUpAnim, idleLeftAnim, idleRightAnim;
    private Animation runDownAnim, runUpAnim, runLeftAnim, runRightAnim;

    private Animation currentAnimation;

    // Base folder where the RED warrior sprites live
    // Example:
    // "/main/resources/sprites/player/Red_Units/Warrior/"
    private final String spriteBasePath;

    public EnemyWarrior(float x, float y, String spriteBasePath) {
        this.x = x;
        this.y = y;
        this.spriteBasePath = spriteBasePath.endsWith("/") ? spriteBasePath : (spriteBasePath + "/");
        initAnimations();
    }

    private void initAnimations() {
        try {
            BufferedImage idleSheet = ImageIO.read(Objects.requireNonNull(
                    getClass().getResource(spriteBasePath + "Warrior_Idle.png"),
                    "Missing enemy idle sprite sheet: " + spriteBasePath + "Warrior_Idle.png"
            ));

            BufferedImage runSheet = ImageIO.read(Objects.requireNonNull(
                    getClass().getResource(spriteBasePath + "Warrior_Run.png"),
                    "Missing enemy run sprite sheet: " + spriteBasePath + "Warrior_Run.png"
            ));

            int frameWidth = 192;
            int frameHeight = 192;

            int idleFramesCount = 8;
            int runFramesCount = 6;

            BufferedImage[] idleFrames = new BufferedImage[idleFramesCount];
            for (int i = 0; i < idleFramesCount; i++) {
                idleFrames[i] = idleSheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight);
            }

            BufferedImage[] runFrames = new BufferedImage[runFramesCount];
            for (int i = 0; i < runFramesCount; i++) {
                runFrames[i] = runSheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight);
            }

            // Reuse same frames for all directions for now (same as your current approach)
            idleDownAnim = new Animation(idleFrames, 8);
            idleUpAnim = new Animation(idleFrames, 8);
            idleLeftAnim = new Animation(idleFrames, 8);
            idleRightAnim = new Animation(idleFrames, 8);

            runDownAnim = new Animation(runFrames, 6);
            runUpAnim = new Animation(runFrames, 6);
            runLeftAnim = new Animation(runFrames, 6);
            runRightAnim = new Animation(runFrames, 6);

            currentAnimation = idleDownAnim;

        } catch (IOException e) {
            throw new RuntimeException("Failed to load Enemy Warrior sprite sheets", e);
        }
    }

    // --- collider helpers (same idea as Player) ---
    private float getColX() {
        return x - colW / 2f;
    }

    private float getColY() {
        return y + FOOT_OFFSET_Y - colH;
    }

    // --- Movement with collision (same axis-by-axis resolution idea) ---
    public void move(TiledMap map, float dx, float dy) {
        if (dx != 0) {
            float newX = x + dx;
            float colX = newX - colW / 2f;
            float colY = getColY();

            for (Rect r : map.colliders) {
                if (r.intersects(colX, colY, colW, colH)) {
                    if (dx > 0) newX = r.x - colW / 2f;
                    else newX = r.x + r.w + colW / 2f;
                    colX = newX - colW / 2f;
                }
            }
            x = newX;
        }

        if (dy != 0) {
            float newY = y + dy;
            float colX = getColX();
            float colY = newY + FOOT_OFFSET_Y - colH;

            for (Rect r : map.colliders) {
                if (r.intersects(colX, colY, colW, colH)) {
                    if (dy > 0) newY = r.y - FOOT_OFFSET_Y;
                    else newY = r.y + r.h - FOOT_OFFSET_Y + colH;
                    colY = newY + FOOT_OFFSET_Y - colH;
                }
            }
            y = newY;
        }
    }

    // --- AI update: chase player in range, idle otherwise ---
    public void updateAI(TiledMap map, Player player, double dt) {
        float px = player.x;
        float py = player.y;

        float vx = px - x;
        float vy = py - y;

        float distSq = vx * vx + vy * vy;
        float aggroSq = aggroRange * aggroRange;
        float stopSq = stopRange * stopRange;

        float dx = 0f, dy = 0f;

        if (distSq <= aggroSq && distSq >= stopSq) {
            float dist = (float) Math.sqrt(distSq);
            float nx = vx / dist;
            float ny = vy / dist;

            dx = nx * speed * (float) dt;
            dy = ny * speed * (float) dt;

            // Facing (flip) based on dx (dominant)
            if (Math.abs(dx) >= Math.abs(dy)) {
                facingLeft = dx < 0;
            }
        }

        // Move + animate based on dx/dy
        move(map, dx, dy);
        updateAnimation(dx, dy);
    }

    private void updateAnimation(float dx, float dy) {
        boolean isMoving = (dx != 0 || dy != 0);

        AnimationType animType = isMoving ? AnimationType.RUN : AnimationType.IDLE;
        MoveType moveType = currentMoveType;

        if (isMoving) {
            if (Math.abs(dx) > Math.abs(dy)) {
                moveType = (dx > 0) ? MoveType.RIGHT : MoveType.LEFT;
            } else {
                moveType = (dy > 0) ? MoveType.DOWN : MoveType.UP;
            }
        }

        setAnimation(animType, moveType);
        if (currentAnimation != null) currentAnimation.update();
    }

    private Animation getAnimation(AnimationType type, MoveType move) {
        if (type == AnimationType.IDLE) {
            return switch (move) {
                case UP -> idleUpAnim;
                case LEFT -> idleLeftAnim;
                case RIGHT -> idleRightAnim;
                case DOWN, NONE -> idleDownAnim;
            };
        } else {
            return switch (move) {
                case UP -> runUpAnim;
                case LEFT -> runLeftAnim;
                case RIGHT -> runRightAnim;
                case DOWN, NONE -> runDownAnim;
            };
        }
    }

    private void setAnimation(AnimationType type, MoveType move) {
        if (type == currentAnimType && move == currentMoveType && currentAnimation != null) return;

        currentAnimType = type;
        currentMoveType = move;
        currentAnimation = getAnimation(type, move);
        if (currentAnimation != null) currentAnimation.reset();
    }

    public void draw(Graphics2D g, Camera cam) {
        int sx = (int) (x - cam.x);
        int sy = (int) (y - cam.y);

        Animation anim = (currentAnimation != null) ? currentAnimation : idleDownAnim;
        BufferedImage frame = anim.getFrame();

        int fw = frame.getWidth();
        int fh = frame.getHeight();

        int drawX = sx - fw / 2;
        int drawY = sy - fh / 2;

        if (facingLeft) {
            g.drawImage(frame, drawX + fw, drawY, -fw, fh, null);
        } else {
            g.drawImage(frame, drawX, drawY, null);
        }
    }
}
