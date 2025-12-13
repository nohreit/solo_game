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

    // “I-frames” so you don’t take damage every tick while overlapping
    private int invulnTicks = 0;
    private static final int INVULN_TICKS_ON_HIT = 18; // ~0.3s at 60fps

    // prevent multiple hits from the same swing
    private int lastHitAttackId = -1;

    private final int maxHp = 30;
    private int hp = maxHp;

    private boolean dead = false;

    // Fade-out (ticks at 60 FPS)
    private int fadeTicks = 0;
    private static final int FADE_DURATION_TICKS = 36; // ~0.6s
    private boolean removed = false; // fully faded, safe to stop drawing/updating


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
    public float getColX() {
        return x - colW / 2f;
    }

    public float getColY() {
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

        if (invulnTicks > 0) invulnTicks--;
        // If dead, just fade out (no movement)
        if (dead) {
            fadeTicks++;
            if (fadeTicks >= FADE_DURATION_TICKS) {
                removed = true;
            }
            return;
        }

        if (removed) return;

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

    // Enemy hurtbox = feet collider
    public Rect getHurtbox() {
        return new Rect(
                (int) (x - colW / 2f),
                (int) (y + FOOT_OFFSET_Y - colH),
                colW,
                colH
        );
    }


    public boolean isDead() {
        return dead;
    }

    public boolean isRemoved() {
        return removed;
    }

    public int getHp() {
        return hp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public void takeHit(int dmg, int attackId) {
        if (dead || removed) return;
        if (invulnTicks > 0) return;
        if (attackId == lastHitAttackId) return;

        lastHitAttackId = attackId;
        invulnTicks = INVULN_TICKS_ON_HIT;
        hp -= dmg;

        System.out.println("Enemy hit! HP = " + hp);

        if (hp <= 0) {
            hp = 0;
            dead = true;
            fadeTicks = 0;
            System.out.println("Enemy defeated (fading out)");
        }
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
        if (removed) return;

        int sx = (int) (x - cam.x);
        int sy = (int) (y - cam.y);

        Animation anim = (currentAnimation != null) ? currentAnimation : idleDownAnim;
        BufferedImage frame = anim.getFrame();

        int fw = frame.getWidth();
        int fh = frame.getHeight();

        int drawX = sx - fw / 2;
        int drawY = sy - fh / 2;

        // --- Fade alpha ---
        float alpha = 1.0f;
        if (dead) {
            alpha = 1.0f - (fadeTicks / (float) FADE_DURATION_TICKS);
            if (alpha < 0f) alpha = 0f;
        }

        Composite oldComp = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        if (facingLeft) {
            g.drawImage(frame, drawX + fw, drawY, -fw, fh, null);
        } else {
            g.drawImage(frame, drawX, drawY, null);
        }

        g.setComposite(oldComp);

        // --- HP bar (don’t show when fully dead) ---
        if (!dead) {
            drawHpBar(g, cam, fw, fh);
        }
    }

    private void drawHpBar(Graphics2D g, Camera cam, int frameW, int frameH) {
        // Bar size
        int barW = 42;
        int barH = 6;

        // Screen position: above the head
        int sx = (int) (x - cam.x);
        int sy = (int) (y - cam.y);

        int barX = sx - barW / 2;
        int barY = sy - frameH / 2 - 12;

        // Background
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(barX, barY, barW, barH);

        // Fill
        float pct = (maxHp <= 0) ? 0f : (hp / (float) maxHp);
        int fillW = (int) (barW * pct);

        g.setColor(new Color(200, 50, 50, 220));
        g.fillRect(barX, barY, fillW, barH);

        // Border
        g.setColor(new Color(255, 255, 255, 200));
        g.drawRect(barX, barY, barW, barH);
    }


}
