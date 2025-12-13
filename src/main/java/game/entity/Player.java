package main.java.game.entity;

import main.java.game.gfx.Camera;
import main.java.game.map.TiledMap;
import main.java.game.physics.Rect;

import java.awt.*;
import java.awt.image.BufferedImage;

import main.java.game.gfx.Animation;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.Objects;

public class Player {

    // World position in pixels (treated as center of the sprite)
    public float x, y;

    // Collision box size
    private final int w = 16, h = 16;

    // Tunable collision box (we’ll wire this just below)
    private final int colW = 16;
    private final int colH = 10;
    private final int FOOT_OFFSET_Y = 24; // pixels down from sprite center toward feet

    // Optional sprite reference (not strictly needed now)
    private final BufferedImage sheet;

    private boolean facingLeft = false;

    // Approximate combo window: ~1000 ms at 60 FPS
    private static final int COMBO_WINDOW_TICKS = 60;
    private boolean guarding = false;

    public void setGuarding(boolean guarding) {
        this.guarding = guarding;
    }

    // High-level animation state
    private enum AnimationType {
        IDLE, RUN, ATTACK, GUARD
    }

    // Direction for idle/run
    private enum MoveType {
        UP, DOWN, LEFT, RIGHT, NONE
    }

    // Attack phase
    private enum AttackPhase {
        NONE, ATTACK1, ATTACK2
    }

    private AnimationType currentAnimType = AnimationType.IDLE;
    private MoveType currentMoveType = MoveType.DOWN;
    private AttackPhase attackPhase = AttackPhase.NONE;

    // Animation sets
    private Animation idleDownAnim;
    private Animation idleUpAnim;
    private Animation idleLeftAnim;
    private Animation idleRightAnim;

    private Animation runDownAnim;
    private Animation runUpAnim;
    private Animation runLeftAnim;
    private Animation runRightAnim;

    private Animation attackAnimation;   // Attack 1
    private Animation attack2Animation;  // Attack 2 (combo)
    private Animation guardAnimation;

    private Animation currentAnimation;

    // Attack playback control
    private boolean attackPlaying = false;
    private int attackTicks = 0;
    private int attackDurationTicks1 = 0;
    private int attackDurationTicks2 = 0;
    private boolean lastAttackPressed = false;

    // Post-attack combo window (for Attack2)
    private boolean inComboWindow = false;
    private int comboWindowTicksRemaining = 0;

    public Player(float x, float y, BufferedImage sheet) {
        this.x = x;
        this.y = y;
        this.sheet = sheet; // optional sprite reference

        initAnimations();
    }

    // Initialize animations for the player
    private void initAnimations() {
        try {
            BufferedImage idleSheet = ImageIO.read(
                    Objects.requireNonNull(
                            getClass().getResource("/main/resources/sprites/player/Black_Units/Warrior/Warrior_Idle.png"),
                            "Missing player idle sprite sheet"
                    )
            );

            BufferedImage runSheet = ImageIO.read(
                    Objects.requireNonNull(
                            getClass().getResource("/main/resources/sprites/player/Black_Units/Warrior/Warrior_Run.png"),
                            "Missing player run sprite sheet"
                    )
            );

            int frameWidth = 192;
            int frameHeight = 192;
            int idleFramesCount = 8;
            int runFramesCount = 6;
            int guardFramesCount = 6; // set to the real count in your file


            // Slice idle
            BufferedImage[] idleFrames = new BufferedImage[idleFramesCount];
            for (int i = 0; i < idleFramesCount; i++) {
                idleFrames[i] = idleSheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight);
            }

            // Slice run
            BufferedImage[] runFrames = new BufferedImage[runFramesCount];
            for (int i = 0; i < runFramesCount; i++) {
                runFrames[i] = runSheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight);
            }


            // For now, all directions reuse same frames
            idleDownAnim = new Animation(idleFrames, 8);
            idleUpAnim = new Animation(idleFrames, 8);
            idleLeftAnim = new Animation(idleFrames, 8);
            idleRightAnim = new Animation(idleFrames, 8);

            runDownAnim = new Animation(runFrames, 6); // slightly faster
            runUpAnim = new Animation(runFrames, 6);
            runLeftAnim = new Animation(runFrames, 6);
            runRightAnim = new Animation(runFrames, 6);


            // Load attack and guard animations
            attackAnimation = loadAnimation(
                    "/main/resources/sprites/player/Black_Units/Warrior/Warrior_Attack1.png",
                    4,
                    6
            );

            attack2Animation = loadAnimation(
                    "/main/resources/sprites/player/Black_Units/Warrior/Warrior_Attack2.png",
                    4,
                    6
            );

            guardAnimation = loadAnimation(
                    "/main/resources/sprites/player/Black_Units/Warrior/Warrior_Guard.png",
                    6,
                    10
            );

            // Pre-compute attack durations in ticks (frames * frameDelay)
            if (attackAnimation != null) {
                attackDurationTicks1 = attackAnimation.getFrameCount() * attackAnimation.getFrameDelay();
                System.out.println("Attack1 duration ticks: " + attackDurationTicks1);
            }
            if (attack2Animation != null) {
                attackDurationTicks2 = attack2Animation.getFrameCount() * attack2Animation.getFrameDelay();
                System.out.println("Attack2 duration ticks: " + attackDurationTicks2);
            }

            // Start in idle facing down
            currentAnimType = AnimationType.IDLE;
            currentMoveType = MoveType.DOWN;
            currentAnimation = idleDownAnim;

            System.out.println("Idle frames loaded: " + idleFrames.length);
            System.out.println("Run frames loaded: " + runFrames.length);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Warrior sprite sheets", e);
        }
    }

    private float getColX() {
        return x - colW / 2f;
    }

    private float getColY() {
        // place the box near the feet, not at the center
        return y + FOOT_OFFSET_Y - colH;
    }

    // Axis-by-axis movement with collider resolution
    public void move(TiledMap map, float dx, float dy) {

        // --- Horizontal movement ---
        if (dx != 0) {
            float newX = x + dx;

            // compute new collision box for proposed X
            float colX = newX - colW / 2f;
            float colY = getColY(); // uses current y

            for (Rect r : map.colliders) {
                if (r.intersects(colX, colY, colW, colH)) {
                    if (dx > 0) {
                        // moving right → place feet just to the left of collider
                        newX = r.x - colW / 2f;
                    } else if (dx < 0) {
                        // moving left → place feet just to the right of collider
                        newX = r.x + r.w + colW / 2f;
                    }
                    // recompute colX for any further checks
                    colX = newX - colW / 2f;
                }
            }

            x = newX;
        }

        // --- Vertical movement ---
        if (dy != 0) {
            float newY = y + dy;

            float colX = getColX(); // uses current x
            float colY = newY + FOOT_OFFSET_Y - colH; // same logic as getColY() but with newY

            for (Rect r : map.colliders) {
                if (r.intersects(colX, colY, colW, colH)) {
                    if (dy > 0) {
                        // moving down → feet hit top of cliff
                        newY = r.y - FOOT_OFFSET_Y;
                    } else if (dy < 0) {
                        // moving up → head hits something above (rare on cliffs)
                        newY = r.y + r.h - FOOT_OFFSET_Y + colH;
                    }
                    colY = newY + FOOT_OFFSET_Y - colH;
                }
            }

            y = newY;
        }
    }


    // Backwards-compatible overload: no attack/guard input
//    public void update(float dx, float dy) {
//        update(dx, dy, false, false);
//    }

    public void update(float dx, float dy, boolean attackPressed, boolean guardPressed) {
        AnimationType animType;
        MoveType moveType = currentMoveType;

        // Edge detection for attack button
        boolean attackJustPressed = attackPressed && !lastAttackPressed;

        // Update guarding state from input (hold-to-guard)
        guarding = guardPressed;

        // If guarding, block starting attacks + clear combo window (optional but recommended)
        if (guarding) {
            attackJustPressed = false;          // <-- prevents new attacks
            inComboWindow = false;              // optional: don’t allow Attack2 window while guarding
            comboWindowTicksRemaining = 0;

            // If you want guard to CANCEL an attack instantly, uncomment:
            // attackPlaying = false;
            // attackPhase = AttackPhase.NONE;
            // attackTicks = 0;

            // Guard animation wins (as long as not currently in an attack)
            if (!attackPlaying && guardAnimation != null) {
                animType = AnimationType.GUARD;
                setAnimation(animType, moveType);
                if (currentAnimation != null) currentAnimation.update();
                lastAttackPressed = attackPressed; // keep edge detection stable
                return; // <-- IMPORTANT: stop here so nothing overrides guard
            }
            // If an attack is currently playing, attack will continue (attack has priority)
        }

        // 1. Handle attack start and combo window usage
        if (attackJustPressed) {
            if (!attackPlaying) {
                if (inComboWindow && attack2Animation != null) {
                    // Start Attack2 from the combo window
                    attackPlaying = true;
                    attackPhase = AttackPhase.ATTACK2;
                    attackTicks = 0;
                    inComboWindow = false;
                    comboWindowTicksRemaining = 0;
                    System.out.println("ATTACK2 started from combo window");
                } else if (attackAnimation != null) {
                    // Fresh Attack1
                    attackPlaying = true;
                    attackPhase = AttackPhase.ATTACK1;
                    attackTicks = 0;
                    inComboWindow = false;
                    comboWindowTicksRemaining = 0;
                    System.out.println("ATTACK1 started");
                }
            }
            // If an attack is already playing (ATTACK1 or ATTACK2), ignore extra presses
        }

        // 2. If an attack is playing, it overrides other states
        if (attackPlaying) {
            animType = AnimationType.ATTACK;

            attackTicks++;

            int currentDuration = (attackPhase == AttackPhase.ATTACK2)
                    ? attackDurationTicks2
                    : attackDurationTicks1;

            if (attackTicks >= currentDuration) {
                AttackPhase finishedPhase = attackPhase;

                // Attack (or combo) finished
                System.out.println("Attack phase " + finishedPhase + " finished");
                attackPlaying = false;
                attackPhase = AttackPhase.NONE;
                attackTicks = 0;

                // Open combo window only after ATTACK1 completes
                if (finishedPhase == AttackPhase.ATTACK1 && attack2Animation != null) {
                    inComboWindow = true;
                    comboWindowTicksRemaining = COMBO_WINDOW_TICKS;
                    System.out.println("Combo window opened for ATTACK2");
                } else {
                    inComboWindow = false;
                    comboWindowTicksRemaining = 0;
                }
            }
        } else if (guardPressed && guardAnimation != null) {
            // 3. Guard: hold-based state
            animType = AnimationType.GUARD;
        } else {
            // 4. Normal movement-based states (RUN / IDLE)
            boolean isMoving = (dx != 0 || dy != 0);

            if (isMoving) {
                animType = AnimationType.RUN;

                // Choose facing based on dominant axis
                if (Math.abs(dx) > Math.abs(dy)) {
                    if (dx > 0) {
                        moveType = MoveType.RIGHT;
                        facingLeft = false;
                    } else {
                        moveType = MoveType.LEFT;
                        facingLeft = true;
                    }
                } else {
                    moveType = (dy > 0) ? MoveType.DOWN : MoveType.UP;
                }
            } else {
                animType = AnimationType.IDLE;
                // keep last moveType to idle facing last direction
            }
        }

        // 5. Tick down the combo window if active
        if (inComboWindow) {
            comboWindowTicksRemaining--;
            if (comboWindowTicksRemaining <= 0) {
                inComboWindow = false;
                comboWindowTicksRemaining = 0;
                System.out.println("Combo window expired");
            }
        }

        // 6. Apply animation change (only resets if anim type or direction changed)
        setAnimation(animType, moveType);

        // 7. Advance current animation frame
        if (currentAnimation != null) {
            currentAnimation.update();
        }

        // Remember previous attack state for edge detection
        lastAttackPressed = attackPressed;
    }

    // Helper to choose animation based on {AnimationType, MoveType}
    private Animation getAnimation(AnimationType type, MoveType move) {
        // Attack / guard ignore direction for now; we just flip with facingLeft
        if (type == AnimationType.ATTACK) {
            if (attackPhase == AttackPhase.ATTACK2 && attack2Animation != null) {
                return attack2Animation;
            }
            return (attackAnimation != null) ? attackAnimation : idleDownAnim;
        }
        if (type == AnimationType.GUARD) {
            return (guardAnimation != null) ? guardAnimation : idleDownAnim;
        }

        if (type == AnimationType.IDLE) {
            return switch (move) {
                case UP -> idleUpAnim;
                case LEFT -> idleLeftAnim;
                case RIGHT -> idleRightAnim;
                case DOWN, NONE -> idleDownAnim;
            };
        } else { // RUN
            return switch (move) {
                case UP -> (runUpAnim != null ? runUpAnim : idleUpAnim);
                case DOWN -> (runDownAnim != null ? runDownAnim : idleDownAnim);
                case LEFT -> (runLeftAnim != null ? runLeftAnim : idleLeftAnim);
                case RIGHT -> (runRightAnim != null ? runRightAnim : idleRightAnim);
                case NONE -> idleDownAnim;
            };
        }
    }

    private void setAnimation(AnimationType type, MoveType move) {
        if (type == currentAnimType && move == currentMoveType && currentAnimation != null) {
            // Same animation as before, don't reset
            return;
        }
        currentAnimType = type;
        currentMoveType = move;
        currentAnimation = getAnimation(type, move);
        if (currentAnimation != null) {
            currentAnimation.reset();
        }
    }

    public void draw(Graphics2D g, Camera cam) {
        int sx = (int) (x - cam.x);
        int sy = (int) (y - cam.y);

        Animation anim = (currentAnimation != null) ? currentAnimation : idleDownAnim;
        BufferedImage frame = anim.getFrame();
        int fw = frame.getWidth();
        int fh = frame.getHeight();

        // Draw centered
        int drawX = sx - fw / 2;
        int drawY = sy - fh / 2;

        if (facingLeft) {
            // Flip horizontally
            g.drawImage(
                    frame,
                    drawX + fw,  // start at right side
                    drawY,
                    -fw,         // negative width flips
                    fh,
                    null
            );
        } else {
            // Normal drawing
            g.drawImage(frame, drawX, drawY, null);
        }
    }

    // Generic loader for animations from a horizontal strip
    private Animation loadAnimation(String path, int frameCount, int frameDelay) {
        try {
            BufferedImage sheet = ImageIO.read(Objects.requireNonNull(
                    getClass().getResourceAsStream(path)
            ));
            int frameWidth = sheet.getWidth() / frameCount;
            int frameHeight = sheet.getHeight();

            BufferedImage[] frames = new BufferedImage[frameCount];
            for (int i = 0; i < frameCount; i++) {
                frames[i] = sheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight);
            }
            return new Animation(frames, frameDelay);
        } catch (IOException e) {
            e.printStackTrace();
            // Fallback: 1x1 dummy frame to avoid crashes
            BufferedImage dummy = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = dummy.createGraphics();
            g2.setColor(Color.MAGENTA);
            g2.fillRect(0, 0, 16, 16);
            g2.dispose();
            return new Animation(new BufferedImage[]{dummy}, frameDelay);
        }
    }

    //----- DEBUGGING -----
    public void debugDrawCollision(Graphics2D g, Camera cam) {
        float colX = getColX();
        float colY = getColY();

        int sx = (int) (colX - cam.x);
        int sy = (int) (colY - cam.y);

        g.setColor(new Color(0, 255, 255, 120));
        g.drawRect(sx, sy, colW, colH);
    }


}
