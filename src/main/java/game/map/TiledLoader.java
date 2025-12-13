package main.java.game.map;

import com.google.gson.*;
import main.java.game.ResourcePathResolver;
import main.java.game.physics.Rect;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;


public class TiledLoader {
    public static TiledMap loadJsonMap(String resource) {
        try (InputStream in = TiledLoader.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalArgumentException("Missing resource: " + resource);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();


            // Basic map fields
            int width = root.get("width").getAsInt();
            int height = root.get("height").getAsInt();
            int tileW = root.get("tilewidth").getAsInt();
            int tileH = root.get("tileheight").getAsInt();
            TiledMap map = new TiledMap(width, height, tileW, tileH);


            // --- Tileset handling ---
            // Support either embedded image tileset or external JSON tileset via `source`
            JsonObject ts0 = root.getAsJsonArray("tilesets").get(0).getAsJsonObject();
            int firstGid = ts0.get("firstgid").getAsInt();
            String image;
            int columns;

            if (ts0.has("source")) {
                // External tileset JSON (type: "tileset"). Path is relative to the map file.
                String source = ts0.get("source").getAsString();

                // Normalize to a classpath-style resource by joining with the map's folder.
                String mapFolder = resource.substring(0, resource.lastIndexOf('/') + 1);
                String tilesetRes = ResourcePathResolver.resolve(mapFolder, source);

                try (InputStream tsIn = TiledLoader.class.getResourceAsStream(tilesetRes)) {
                    if (tsIn == null) throw new IllegalArgumentException("Missing tileset resource: " + tilesetRes);
                    JsonObject tsRoot = JsonParser.parseReader(new InputStreamReader(tsIn, StandardCharsets.UTF_8)).getAsJsonObject();
                    image = ResourcePathResolver.resolve(mapFolder,tsRoot.get("image").getAsString());
                    columns = tsRoot.get("columns").getAsInt();
                    // Optional override if tile sizes differ from map (rare but supported)
                    if (tsRoot.has("tilewidth") && tsRoot.has("tileheight")) {
                        // We rely on map tileW/tileH for rendering grid; most cases match.
                    }
                }
            } else {
                // Embedded tileset
                image = ts0.get("image").getAsString();
                columns = ts0.get("columns").getAsInt();
            }


            // Make sure the path starts with '/' for classpath resource resolution.
            if (!image.startsWith("/")) image = "/" + image;
            map.setTileset(image, firstGid, columns);


            // --- Layers ---
            for (JsonElement e : root.getAsJsonArray("layers")) {
                JsonObject lay = e.getAsJsonObject();
                String type = lay.get("type").getAsString();


                if (type.equals("tilelayer")) {
                    JsonArray arr = lay.get("data").getAsJsonArray();
                    int[] data = new int[arr.size()];
                    for (int i = 0; i < arr.size(); i++) data[i] = arr.get(i).getAsInt();
                    map.addLayer(data);
                } else if (type.equals("objectgroup") && lay.get("name").getAsString().equalsIgnoreCase("colliders")) {
                    for (JsonElement oe : lay.get("objects").getAsJsonArray()) {
                        JsonObject o = oe.getAsJsonObject();
                        int x = o.get("x").getAsInt();
                        int y = o.get("y").getAsInt();
                        int w = o.get("width").getAsInt();
                        int h = o.get("height").getAsInt();
                        map.addCollider(new Rect(x, y, w, h));
                    }
                }
            }
            return map;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}