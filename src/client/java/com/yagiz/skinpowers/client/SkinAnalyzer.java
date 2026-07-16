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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SkinAnalyzer {
    private static final int[][] WARDEN_COLORS = {
        {8, 12, 18}, {17, 29, 48}, {31, 26, 67}, {63, 27, 88}, {15, 61, 78}, {25, 126, 132}
    };
    private static final int[][] FLIGHT_COLORS = {
        {248, 250, 255}, {205, 216, 228}, {157, 207, 235}, {188, 190, 200}, {105, 176, 226}
    };
    private static final int[][] FIRE_COLORS = {
        {235, 35, 20}, {246, 96, 13}, {255, 181, 24}, {173, 20, 9}, {249, 221, 54}
    };
    private static final int[][] NATURE_COLORS = {
        {46, 125, 50}, {76, 148, 63}, {31, 92, 43}, {104, 159, 56}, {91, 67, 39}, {126, 92, 49}, {59, 104, 53}
    };
    private static final java.util.concurrent.ConcurrentHashMap<UUID, Result> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final ExecutorService ANALYSIS_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SkinPowers-SkinAnalyzer");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .executor(ANALYSIS_EXECUTOR)
        .build();

    private SkinAnalyzer() {}

    public static CompletableFuture<Result> analyzeAsync(GameProfile profile) {
        UUID profileId = profile == null ? null : extractProfileId(profile);
        if (profileId != null) {
            Result cached = CACHE.get(profileId);
            if (cached != null) return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                String skinUrl = findSkinUrl(profile, HTTP_CLIENT);
                if (skinUrl == null || !skinUrl.startsWith("https://")) return Result.unavailable();

                HttpRequest request = HttpRequest.newBuilder(URI.create(skinUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "SkinPowers/0.3.7")
                    .GET()
                    .build();
                HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() < 200 || response.statusCode() >= 300) return Result.unavailable();
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
                if (image == null) return Result.unavailable();
                Result analyzed = analyze(image);
                if (profileId != null && analyzed.fromSkin()) CACHE.put(profileId, analyzed);
                return analyzed;
            } catch (Exception ignored) {
                return Result.unavailable();
            }
        }, ANALYSIS_EXECUTOR);
    }

    static Result analyze(BufferedImage image) {
        double warden = 0.0;
        double flight = 0.0;
        double fire = 0.0;
        double nature = 0.0;
        int counted = 0;
        int matched = 0;
        Map<Integer, Integer> quantized = new HashMap<>();

        int width = image.getWidth();
        int height = image.getHeight();
        int[] skinPixels = image.getRGB(0, 0, width, height, null, 0, width);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = skinPixels[y * width + x];
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha < 32) continue;

                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                counted++;

                double[] scores = pixelScores(red, green, blue);
                double best = Math.max(Math.max(scores[0], scores[1]), Math.max(scores[2], scores[3]));
                if (best >= 0.18) {
                    warden += Math.pow(scores[0], 2.6);
                    flight += Math.pow(scores[1], 2.6);
                    fire += Math.pow(scores[2], 2.6);
                    nature += Math.pow(scores[3], 2.6);
                    matched++;
                }

                int qr = (red / 24) * 24;
                int qg = (green / 24) * 24;
                int qb = (blue / 24) * 24;
                int key = (qr << 16) | (qg << 8) | qb;
                quantized.merge(key, 1, Integer::sum);
            }
        }

        if (counted == 0) return Result.unavailable();
        int[] dominant = quantized.entrySet().stream()
            .sorted(Map.Entry.<Integer, Integer>comparingByValue(Comparator.reverseOrder()))
            .limit(4)
            .mapToInt(Map.Entry::getKey)
            .toArray();
        if (dominant.length < 4) {
            int[] filled = {0x233044, 0x8FCBE8, 0xE95818, 0x4F8B3B};
            System.arraycopy(dominant, 0, filled, 0, dominant.length);
            dominant = filled;
        }

        return new Result(
            warden / counted,
            flight / counted,
            fire / counted,
            nature / counted,
            dominant,
            true,
            skinPixels,
            width,
            height,
            matched / (double) counted
        );
    }

    private static double[] pixelScores(int r, int g, int b) {
        double warden = nearestSimilarity(r, g, b, WARDEN_COLORS, 58.0);
        double flight = nearestSimilarity(r, g, b, FLIGHT_COLORS, 52.0);
        double fire = nearestSimilarity(r, g, b, FIRE_COLORS, 52.0);
        double nature = nearestSimilarity(r, g, b, NATURE_COLORS, 52.0);

        double max = Math.max(r, Math.max(g, b)) / 255.0;
        double min = Math.min(r, Math.min(g, b)) / 255.0;
        double saturation = max <= 0.0001 ? 0.0 : (max - min) / max;
        double hue = hueDegrees(r, g, b);

        if (max < 0.34 && (b >= r * 0.82 || hue >= 220.0 && hue <= 310.0)) {
            warden = Math.max(warden, 0.72 + (0.34 - max) * 0.65);
        }
        if (max > 0.68 && saturation < 0.30) {
            flight = Math.max(flight, 0.72 + (max - 0.68) * 0.70);
        }
        if (max > 0.68 && hue >= 198.0 && hue <= 230.0 && saturation < 0.55) {
            flight = Math.max(flight, 0.66 + Math.min(0.24, (1.0 - saturation) * 0.28));
        }
        if (saturation > 0.42 && max > 0.30 && (hue <= 67.0 || hue >= 345.0)) {
            fire = Math.max(fire, 0.74 + Math.min(0.22, saturation * 0.22));
        }
        if (saturation > 0.28 && max > 0.20 && hue >= 72.0 && hue <= 155.0) {
            nature = Math.max(nature, 0.70 + Math.min(0.27, saturation * 0.25));
        }
        // Kahverengi/toprak tonları da Doğa sınıfına katkı verir.
        if (r > g && g > b && hue >= 22.0 && hue <= 52.0 && max < 0.72 && saturation > 0.22) {
            nature = Math.max(nature, 0.55 + Math.min(0.28, saturation * 0.24));
        }

        return new double[]{clamp01(warden), clamp01(flight), clamp01(fire), clamp01(nature)};
    }

    private static double hueDegrees(int r, int g, int b) {
        double rd = r / 255.0;
        double gd = g / 255.0;
        double bd = b / 255.0;
        double max = Math.max(rd, Math.max(gd, bd));
        double min = Math.min(rd, Math.min(gd, bd));
        double delta = max - min;
        if (delta < 0.00001) return 0.0;
        double hue;
        if (max == rd) hue = 60.0 * (((gd - bd) / delta) % 6.0);
        else if (max == gd) hue = 60.0 * (((bd - rd) / delta) + 2.0);
        else hue = 60.0 * (((rd - gd) / delta) + 4.0);
        return hue < 0.0 ? hue + 360.0 : hue;
    }

    private static double nearestSimilarity(int r, int g, int b, int[][] prototypes, double sigma) {
        double bestDistanceSquared = Double.MAX_VALUE;
        for (int[] prototype : prototypes) {
            double dr = r - prototype[0];
            double dg = g - prototype[1];
            double db = b - prototype[2];
            double distanceSquared = dr * dr + dg * dg + db * db;
            if (distanceSquared < bestDistanceSquared) bestDistanceSquared = distanceSquared;
        }
        return Math.exp(-bestDistanceSquared / (2.0 * sigma * sigma));
    }

    private static String findSkinUrl(GameProfile profile, HttpClient client) throws Exception {
        if (profile == null) return null;

        String embedded = findEmbeddedSkinUrl(profile);
        if (embedded != null) return embedded;

        UUID profileId = extractProfileId(profile);
        if (profileId == null) return null;
        String compactUuid = profileId.toString().replace("-", "");
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + compactUuid + "?unsigned=false"))
            .timeout(Duration.ofSeconds(7))
            .header("User-Agent", "SkinPowers/0.3.7")
            .GET()
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) return null;
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!root.has("properties")) return null;
        for (var element : root.getAsJsonArray("properties")) {
            JsonObject property = element.getAsJsonObject();
            if (!property.has("name") || !"textures".equals(property.get("name").getAsString())) continue;
            if (!property.has("value")) continue;
            String skinUrl = skinUrlFromTextureValue(property.get("value").getAsString());
            if (skinUrl != null) return skinUrl;
        }
        return null;
    }

    private static String findEmbeddedSkinUrl(GameProfile profile) throws Exception {
        Object propertyContainer = invokeNoArg(profile, "properties", "getProperties");
        if (propertyContainer == null) return null;
        for (Object property : textureProperties(propertyContainer)) {
            String value = extractPropertyValue(property);
            if (value == null || value.isBlank()) continue;
            String skinUrl = skinUrlFromTextureValue(value);
            if (skinUrl != null) return skinUrl;
        }
        return null;
    }

    private static String skinUrlFromTextureValue(String value) {
        try {
            String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(decoded).getAsJsonObject();
            JsonObject textures = root.has("textures") ? root.getAsJsonObject("textures") : null;
            JsonObject skin = textures != null && textures.has("SKIN") ? textures.getAsJsonObject("SKIN") : null;
            return skin != null && skin.has("url") ? skin.get("url").getAsString() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static UUID extractProfileId(GameProfile profile) {
        Object value = invokeNoArg(profile, "id", "getId");
        if (value instanceof UUID uuid) return uuid;
        if (value instanceof String string) {
            try {
                return UUID.fromString(string);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private static List<Object> textureProperties(Object propertyContainer) {
        List<Object> result = new ArrayList<>();
        Object named = invokeOneArg(propertyContainer, "get", "textures");
        addAll(result, named, false);
        if (!result.isEmpty()) return result;
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
        if (property == null) return null;
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

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public record Result(
        double warden,
        double flight,
        double fire,
        double nature,
        int[] dominantColors,
        boolean fromSkin,
        int[] skinPixels,
        int skinWidth,
        int skinHeight,
        double matchedFraction
    ) {
        public static Result unavailable() {
            return new Result(0.0, 0.0, 0.0, 0.0, new int[]{0x233044, 0x8FCBE8, 0xE95818, 0x4F8B3B}, false, new int[0], 0, 0, 0.0);
        }

        public double score(int index) {
            return switch (index) {
                case 0 -> warden;
                case 1 -> flight;
                case 2 -> fire;
                default -> nature;
            };
        }

        public int bestIndex() {
            if (!hasRecommendation()) return -1;
            double[] values = {warden, flight, fire, nature};
            int best = 0;
            for (int i = 1; i < values.length; i++) {
                if (values[i] > values[best]) best = i;
            }
            return best;
        }

        public boolean hasRecommendation() {
            if (!fromSkin || matchedFraction < 0.035) return false;
            double[] values = {warden, flight, fire, nature};
            double best = -1.0;
            double second = -1.0;
            for (double value : values) {
                if (value > best) {
                    second = best;
                    best = value;
                } else if (value > second) {
                    second = value;
                }
            }
            return best >= 0.075 && best - second >= 0.015;
        }

        public boolean hasSkinImage() {
            return fromSkin && skinPixels != null && skinPixels.length == skinWidth * skinHeight && skinWidth >= 64 && skinHeight >= 32;
        }

        public int argbAt(int x, int y) {
            if (!hasSkinImage() || x < 0 || y < 0 || x >= skinWidth || y >= skinHeight) return 0;
            return skinPixels[y * skinWidth + x];
        }
    }
}
