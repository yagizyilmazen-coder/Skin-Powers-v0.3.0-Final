package com.yagiz.skinpowers.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class SkinAnalyzer {
    private static final int[][] WARDEN_COLORS = {
        {12, 16, 22}, {20, 32, 55}, {31, 27, 64}, {55, 28, 82}, {20, 58, 77}
    };
    private static final int[][] FLIGHT_COLORS = {
        {245, 248, 255}, {192, 210, 225}, {150, 205, 235}, {205, 205, 210}, {112, 176, 225}
    };
    private static final int[][] FIRE_COLORS = {
        {235, 35, 20}, {245, 100, 15}, {255, 185, 25}, {170, 20, 10}, {250, 225, 60}
    };

    private SkinAnalyzer() {}

    public static CompletableFuture<Result> analyzeAsync(GameProfile profile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String skinUrl = findSkinUrl(profile);
                if (skinUrl == null || !skinUrl.startsWith("https://")) return Result.fallback();

                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(skinUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "SkinPowers/0.3.0")
                    .GET()
                    .build();
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() < 200 || response.statusCode() >= 300) return Result.fallback();
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
                if (image == null) return Result.fallback();
                return analyze(image);
            } catch (Exception ignored) {
                return Result.fallback();
            }
        });
    }

    static Result analyze(BufferedImage image) {
        double warden = 0.0;
        double flight = 0.0;
        double fire = 0.0;
        int counted = 0;
        Map<Integer, Integer> quantized = new HashMap<>();

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha < 16) continue;
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;

                warden += nearestSimilarity(red, green, blue, WARDEN_COLORS);
                flight += nearestSimilarity(red, green, blue, FLIGHT_COLORS);
                fire += nearestSimilarity(red, green, blue, FIRE_COLORS);
                counted++;

                int qr = (red / 32) * 32;
                int qg = (green / 32) * 32;
                int qb = (blue / 32) * 32;
                int key = (qr << 16) | (qg << 8) | qb;
                quantized.merge(key, 1, Integer::sum);
            }
        }

        if (counted == 0) return Result.fallback();
        double sum = Math.max(0.0001, warden + flight + fire);
        int[] dominant = quantized.entrySet().stream()
            .sorted(Map.Entry.<Integer, Integer>comparingByValue(Comparator.reverseOrder()))
            .limit(3)
            .mapToInt(Map.Entry::getKey)
            .toArray();
        if (dominant.length < 3) {
            int[] filled = {0x233044, 0x8FCBE8, 0xE95818};
            System.arraycopy(dominant, 0, filled, 0, dominant.length);
            dominant = filled;
        }
        return new Result(warden / sum, flight / sum, fire / sum, dominant, true);
    }

    private static double nearestSimilarity(int r, int g, int b, int[][] prototypes) {
        double bestDistanceSquared = Double.MAX_VALUE;
        for (int[] prototype : prototypes) {
            double dr = r - prototype[0];
            double dg = g - prototype[1];
            double db = b - prototype[2];
            double distanceSquared = dr * dr + dg * dg + db * db;
            if (distanceSquared < bestDistanceSquared) bestDistanceSquared = distanceSquared;
        }
        return Math.exp(-bestDistanceSquared / (2.0 * 92.0 * 92.0));
    }

    private static String findSkinUrl(GameProfile profile) throws Exception {
        if (profile == null) return null;

        // Authlib 7 (Minecraft 26.1.x) uses record-style accessors, while older
        // versions used getProperties(). Reflection keeps this code compatible
        // with both forms without directly compiling against a removed method.
        Object propertyContainer = invokeNoArg(profile, "properties", "getProperties");
        if (propertyContainer == null) return null;

        for (Object property : textureProperties(propertyContainer)) {
            String value = extractPropertyValue(property);
            if (value == null || value.isBlank()) continue;
            String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(decoded).getAsJsonObject();
            JsonObject textures = root.has("textures") ? root.getAsJsonObject("textures") : null;
            JsonObject skin = textures != null && textures.has("SKIN") ? textures.getAsJsonObject("SKIN") : null;
            if (skin != null && skin.has("url")) return skin.get("url").getAsString();
        }
        return null;
    }

    private static List<Object> textureProperties(Object propertyContainer) {
        List<Object> result = new ArrayList<>();

        // PropertyMap/Multimap biçimi: properties.get("textures")
        Object named = invokeOneArg(propertyContainer, "get", "textures");
        addAll(result, named, false);
        if (!result.isEmpty()) return result;

        // Yeni Authlib biçimi: doğrudan Property listesi.
        addAll(result, propertyContainer, true);
        return result;
    }

    private static void addAll(List<Object> target, Object source, boolean filterByName) {
        if (source == null) return;
        if (source instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                if (!filterByName || "textures".equals(extractPropertyName(value))) target.add(value);
            }
            return;
        }
        if (source.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(source);
            for (int i = 0; i < length; i++) {
                Object value = java.lang.reflect.Array.get(source, i);
                if (!filterByName || "textures".equals(extractPropertyName(value))) target.add(value);
            }
        }
    }

    private static Object invokeNoArg(Object target, String... names) {
        for (String name : names) {
            try {
                return target.getClass().getMethod(name).invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // Sıradaki uyumlu method adı denenir.
            }
        }
        return null;
    }

    private static Object invokeOneArg(Object target, String name, Object argument) {
        for (java.lang.reflect.Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
            try {
                return method.invoke(target, argument);
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                // Aynı adlı başka overload denenir.
            }
        }
        return null;
    }

    private static String extractPropertyName(Object property) {
        if (property == null) return null;
        for (String methodName : new String[]{"name", "getName"}) {
            try {
                Object result = property.getClass().getMethod(methodName).invoke(property);
                if (result instanceof String string) return string;
            } catch (ReflectiveOperationException ignored) {
                // Authlib sürümleri arasında method adı değişebiliyor.
            }
        }
        return null;
    }

    private static String extractPropertyValue(Object property) {
        for (String methodName : new String[]{"value", "getValue"}) {
            try {
                Object result = property.getClass().getMethod(methodName).invoke(property);
                if (result instanceof String string) return string;
            } catch (ReflectiveOperationException ignored) {
                // Authlib sürümleri arasında method adı değişebiliyor.
            }
        }
        return null;
    }

    public record Result(double warden, double flight, double fire, int[] dominantColors, boolean fromSkin) {
        public static Result fallback() {
            return new Result(0.34, 0.33, 0.33, new int[]{0x233044, 0x8FCBE8, 0xE95818}, false);
        }

        public double score(int index) {
            return switch (index) {
                case 0 -> warden;
                case 1 -> flight;
                default -> fire;
            };
        }

        public int bestIndex() {
            if (warden >= flight && warden >= fire) return 0;
            if (flight >= fire) return 1;
            return 2;
        }
    }
}
