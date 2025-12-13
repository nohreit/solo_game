package main.java.game;

import main.java.game.gfx.Camera;
import main.java.game.input.Input;
import main.java.game.map.TiledLoader;
import main.java.game.map.TiledMap;
import main.java.game.entity.Player;
import main.java.game.physics.Rect;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.List;

import main.java.game.entity.EnemyWarrior;

public class GamePanel extends JPanel implements Runnable {

    private final int vw, vh, scale;
    private Thread loopThread;
    private volatile boolean running;

    private BufferedImage backbuffer;
    private Graphics2D g;

    private Input input;
    private TiledMap map;
    private Camera camera;
    private Player player;
    private final List<EnemyWarrior> enemies = new ArrayList<>();


    public GamePanel(int virtualW, int virtualH, int scale) {
        this.vw = virtualW;
        this.vh = virtualH;
        this.scale = scale;
        setPreferredSize(new Dimension(vw * scale, vh * scale));
        setFocusable(true);
        requestFocusInWindow();
    }

    public void init() {
        backbuffer = new BufferedImage(vw, vh, BufferedImage.TYPE_INT_ARGB);
        String mapResourcePath = "/main/resources/maps/demo.json";

        g = backbuffer.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        input = new Input();
        addKeyListener(input);

        try {
            map = TiledLoader.loadJsonMap(mapResourcePath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load map: " + e.getMessage(), e);
        }

        camera = new Camera(0, 0, vw, vh, map.getPixelWidth(), map.getPixelHeight());

        try {
            BufferedImage playerSheet = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream(
                    "/main/resources/sprites/player/Black_Units/Warrior/Warrior_Idle.png"
            )));

            player = new Player(512f, 256f, playerSheet);
            spawnEnemies();
        } catch (IOException e) {
            player = new Player(512f, 256f, null); // fallback placeholder
            EnemyWarrior enemy = new EnemyWarrior(1024f, 580f, null); // fallback placeholder
        }
    }

    private void spawnEnemies() {
        String redBase = "/main/resources/sprites/player/Red_Units/Warrior/";

        enemies.clear();
//        enemies.add(new EnemyWarrior(512f, 256f, redBase));
//        enemies.add(new EnemyWarrior(420, 180, redBase));
//        enemies.add(new EnemyWarrior(320, 320, redBase));
//        enemies.add(new EnemyWarrior(160, 260, redBase));
        spawnEnemyTile(15,4);
        spawnEnemyTile(23, 6);
        spawnEnemyTile(18, 7);
        spawnEnemyTile(12, 10);
    }

    // Helper to spawn in tile coordinates.
    private void spawnEnemyTile(int tileX, int tileY) {
        int TILE = 64; // use your actual tile size
        String redBase = "/main/resources/sprites/player/Red_Units/Warrior/";
        enemies.add(new EnemyWarrior(tileX * TILE + TILE / 2f, tileY * TILE + TILE / 2f, redBase));
    }

    public void startLoop() {
        if (loopThread != null) return;
        running = true;
        loopThread = new Thread(this, "game-loop");
        loopThread.start();
    }

    @Override
    public void run() {
        final double targetFps = 60.0;
        final double nsPerUpdate = 1_000_000_000.0 / targetFps;
        long last = System.nanoTime();
        double acc = 0.0;

        while (running) {
            long now = System.nanoTime();
            acc += (now - last) / nsPerUpdate;
            last = now;

            while (acc >= 1.0) {
                update(1.0 / targetFps);
                acc -= 1.0;
            }
            render();
            Toolkit.getDefaultToolkit().sync(); // for smoother Linux rendering
        }
    }

    private void update(double dt) {
        // Movement (WASD / Arrow keys)
        float speed = 80f; // pixels per second
        float dx = 0, dy = 0;
        boolean guarding = input.isGuard();

        if (!guarding) {
            if (input.isUp()) dy -= (float) (speed * dt);
            if (input.isDown()) dy += (float) (speed * dt);
            if (input.isLeft()) dx -= (float) (speed * dt);
            if (input.isRight()) dx += (float) (speed * dt);
        }

        player.move(map, dx, dy);

        camera.centerOn(player.x, player.y);

        player.update(dx, dy, input.isAttack(), input.isGuard());

        Iterator<EnemyWarrior> it = enemies.iterator();

        while (it.hasNext()) {
            EnemyWarrior e = it.next();

            // Update AI unless fully removed
            if (!e.isRemoved()) {
                e.updateAI(map, player, dt);
            }

            if (!e.isDead() && player.isAttackActive()) {
                Rect hitbox = player.getAttackHitbox();
                if (hitbox != null) {
                    Rect enemyBox = e.getHurtbox();
                    if (enemyBox.intersects(hitbox.x, hitbox.y, hitbox.w, hitbox.h)) {
                        e.takeHit(10, player.getAttackId());
                    }
                }
            }

            // Clean up after the fade
            if (e.isRemoved()) {
                it.remove();
            }
        }


    }

    boolean DEBUG_COLLISIONS = true;

    private void render() {
        // clear
        g.setColor(new Color(24, 26, 29));
        g.fillRect(0, 0, vw, vh);

        // draw map (background + main layers only)
        map.draw(g, camera);

        // draw enemies if alive
        for (EnemyWarrior e : enemies) {
            if (!e.isRemoved()) e.draw(g, camera);
        }

        // draw player
        player.draw(g, camera);

        graphicDebugging();

        // HUD (debug)
        g.setColor(Color.WHITE);
        g.drawString("pos:" + (int) player.x + "," + (int) player.y, 4, 12);

        // debug draw hitboxes
        debugDrawAttackHitbox(g, camera);
        player.debugDrawAttackHitbox(g, camera);

        // blit to screen scaled
        Graphics2D g2 = (Graphics2D) getGraphics();
        if (g2 != null) {
            g2.drawImage(backbuffer, 0, 0, vw * scale, vh * scale, null);
            g2.dispose();
        }
    }

    private void graphicDebugging() {
        if (DEBUG_COLLISIONS) {
            // Draw map colliders in translucent red
            g.setColor(new Color(255, 0, 0, 100));
            for (Rect r : map.colliders) {
                int sx = (int) (r.x - camera.x);
                int sy = (int) (r.y - camera.y);
                g.fillRect(sx, sy, (int) r.w, (int) r.h);
            }

            // Draw player collision box in cyan
            player.debugDrawCollision(g, camera);
        }
    }

    public void debugDrawAttackHitbox(Graphics2D g, Camera cam) {
        Rect hb = player.getAttackHitbox();
        if (hb == null) return;

        int sx = (int) (hb.x - cam.x);
        int sy = (int) (hb.y - cam.y);

        g.setColor(new Color(255, 0, 0, 120));
        g.drawRect(sx, sy, hb.w, hb.h);
    }


}
