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

/** Gerçek skin piksellerini analiz eder; uydurma eşit puan üretmez. */
public final class SkinAnalyzer {
    private static final int CLASS_COUNT = 5;
    private static final int[][] WARDEN_COLORS = {
        {6, 12, 18}, {17, 29, 48}, {31, 26, 67}, {63, 27, 88}, {15, 61, 78}, {25, 126, 132}
    };
    private static final int[][] FLIGHT_COLORS = {
        {15, 4, 24}, {48, 8, 72}, {83, 18, 122}, {128, 45, 181}, {190, 91, 244}, {229, 174, 255}
    };
    private static final int[][] FIRE_COLORS = {
        {235, 35, 20}, {246, 96, 13}, {255, 181, 24}, {173, 20, 9}, {249, 221, 54}
    };
    private static final int[][] NATURE_COLORS = {
        {46, 125, 50}, {76, 148, 63}, {31, 92, 43}, {104, 159, 56}, {91, 67, 39}, {126, 92, 49}, {59, 104, 53}
    };
    private static final int[][] ANOMALY_COLORS = {
        {12, 3, 20}, {52, 12, 82}, {116, 38, 170}, {182, 92, 255},
        {69, 220, 224}, {234, 75, 99}, {238, 240, 248}
    };

    private static final java.util.concurrent.ConcurrentHashMap<UUID, Result> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final ExecutorService ANALYSIS_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SkinPowers-SkinAnalyzer");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    // Skin analizi ile ağ istekleri aynı tek iş parçacığını paylaşırsa blocking send()
    // birbirini bekleyerek analizin sonsuza kadar "bulunamadı" kalmasına yol açabilir.
    // Ağ işlemleri bu nedenle ayrı, daemon bir havuzda çalışır.
    private static final ExecutorService HTTP_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "SkinPowers-SkinHttp");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(6))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .executor(HTTP_EXECUTOR)
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
                String skinUrl = normalizeSkinUrl(findSkinUrl(profile, HTTP_CLIENT));
                if (skinUrl == null) return Result.unavailable();
                HttpRequest request = HttpRequest.newBuilder(URI.create(skinUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "SkinPowers/1.0.10")
                    .GET()
                    .build();
                HttpResponse<byte[]> response = sendBytesWithRetry(request, 2);
                if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) return Result.unavailable();
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
                if (image == null || image.getWidth() < 64 || image.getHeight() < 32) return Result.unavailable();
                Result analyzed = analyze(image);
                if (profileId != null && analyzed.fromSkin()) CACHE.put(profileId, analyzed);
                return analyzed;
            } catch (Exception ignored) {
                return Result.unavailable();
            }
        }, ANALYSIS_EXECUTOR);
    }

    static Result analyze(BufferedImage image) {
        double[] totals = new double[CLASS_COUNT];
        double weightedCount = 0.0;
        int counted = 0;
        int matched = 0;
        int purplePixels = 0;
        int darkPixels = 0;
        int cyanPixels = 0;
        int redGlitchPixels = 0;
        int whitePixels = 0;
        Map<Integer, Integer> quantized = new HashMap<>();

        int width = image.getWidth();
        int height = image.getHeight();
        int[] skinPixels = image.getRGB(0, 0, width, height, null, 0, width);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = skinPixels[y * width + x];
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha < 24) continue;

                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                double weight = pixelWeight(x, y, width, height);
                counted++;
                weightedCount += weight;

                double[] scores = pixelScores(red, green, blue);
                double best = 0.0;
                for (double score : scores) best = Math.max(best, score);
                if (best >= 0.08) matched++;
                for (int i = 0; i < scores.length; i++) {
                    // Yakın renkler birden fazla sınıfa puan verebilir; güçlü eşleşmeler daha fazla ağırlık alır.
                    totals[i] += Math.pow(scores[i], 2.05) * weight;
                }

                double hue = hueDegrees(red, green, blue);
                double max = Math.max(red, Math.max(green, blue)) / 255.0;
                double min = Math.min(red, Math.min(green, blue)) / 255.0;
                double saturation = max <= 0.0001 ? 0.0 : (max - min) / max;
                if (hue >= 265 && hue <= 325 && saturation > 0.35 && max > 0.25) purplePixels++;
                if (max < 0.18) darkPixels++;
                if (hue >= 172 && hue <= 196 && saturation > 0.35 && max > 0.42) cyanPixels++;
                if ((hue <= 12 || hue >= 348) && saturation > 0.55 && max > 0.55) redGlitchPixels++;
                if (max > 0.82 && saturation < 0.16) whitePixels++;

                int qr = (red / 24) * 24;
                int qg = (green / 24) * 24;
                int qb = (blue / 24) * 24;
                quantized.merge((qr << 16) | (qg << 8) | qb, 1, Integer::sum);
            }
        }

        if (counted == 0 || weightedCount <= 0.0) return Result.unavailable();

        // Anomali tek bir renkle değil, bozuk palet birlikteliğiyle öne çıkar.
        // Sadece siyah veya sadece beyaz bir skin bu nedenle Anomali sayılmaz.
        int chromatic = purplePixels + cyanPixels + redGlitchPixels;
        int contrast = darkPixels + whitePixels;
        double pairFraction = Math.min(chromatic, contrast) / (double) counted;
        int activeGroups = (purplePixels > counted * 0.015 ? 1 : 0)
            + (cyanPixels > counted * 0.012 ? 1 : 0)
            + (redGlitchPixels > counted * 0.008 ? 1 : 0)
            + (darkPixels > counted * 0.025 ? 1 : 0)
            + (whitePixels > counted * 0.012 ? 1 : 0);
        double diversityBonus = Math.max(0, activeGroups - 1) * 0.035;
        totals[4] += (pairFraction * 2.8 + diversityBonus) * weightedCount;

        double sum = 0.0;
        for (int i = 0; i < totals.length; i++) {
            totals[i] = Math.max(0.000001, totals[i] / weightedCount);
            sum += totals[i];
        }
        if (sum <= 0.0) return Result.unavailable();
        for (int i = 0; i < totals.length; i++) totals[i] /= sum;

        int[] dominant = quantized.entrySet().stream()
            .sorted(Map.Entry.<Integer, Integer>comparingByValue(Comparator.reverseOrder()))
            .limit(5)
            .mapToInt(Map.Entry::getKey)
            .toArray();
        if (dominant.length < 5) {
            int[] filled = {0x233044, 0x8FCBE8, 0xE95818, 0x4F8B3B, 0x742AAA};
            System.arraycopy(dominant, 0, filled, 0, dominant.length);
            dominant = filled;
        }

        return new Result(totals, dominant, true, skinPixels, width, height, matched / (double) counted);
    }

    private static double pixelWeight(int x, int y, int width, int height) {
        if (width < 64 || height < 32) return 1.0;
        // Baş/ten bölgesi sonucu tek başına ele geçirmesin. Gövde ve ikinci katmanlar daha önemli.
        if (y < 16) return 0.42;
        if (y < 32) return 1.10;
        return 1.38;
    }

    private static double[] pixelScores(int r, int g, int b) {
        double warden = nearestSimilarity(r, g, b, WARDEN_COLORS, 70.0);
        double flight = nearestSimilarity(r, g, b, FLIGHT_COLORS, 68.0);
        double fire = nearestSimilarity(r, g, b, FIRE_COLORS, 66.0);
        double nature = nearestSimilarity(r, g, b, NATURE_COLORS, 68.0);
        double anomaly = nearestSimilarity(r, g, b, ANOMALY_COLORS, 54.0) * 0.72;

        double max = Math.max(r, Math.max(g, b)) / 255.0;
        double min = Math.min(r, Math.min(g, b)) / 255.0;
        double saturation = max <= 0.0001 ? 0.0 : (max - min) / max;
        double hue = hueDegrees(r, g, b);

        if (max < 0.34 && (b >= r * 0.82 || hue >= 220.0 && hue <= 310.0)) warden = Math.max(warden, 0.72);
        if (hue >= 245.0 && hue <= 286.0 && saturation > 0.38 && max > 0.22) flight = Math.max(flight, 0.76);
        if (hue >= 286.0 && hue <= 306.0 && saturation > 0.34 && max > 0.30) flight = Math.max(flight, 0.66);
        if (saturation > 0.42 && max > 0.30 && (hue <= 35.0 || hue >= 345.0)) fire = Math.max(fire, 0.78);
        if (saturation > 0.28 && max > 0.20 && hue >= 72.0 && hue <= 155.0) nature = Math.max(nature, 0.75);
        if (r > g && g > b && hue >= 22.0 && hue <= 52.0 && max < 0.72 && saturation > 0.22) nature = Math.max(nature, 0.58);
        if (hue >= 305.0 && hue <= 334.0 && saturation > 0.56 && max > 0.38) anomaly = Math.max(anomaly, 0.68);
        if (hue >= 174.0 && hue <= 194.0 && saturation > 0.48 && max > 0.48) anomaly = Math.max(anomaly, 0.64);
        if ((hue <= 10.0 || hue >= 350.0) && saturation > 0.68 && max > 0.62) anomaly = Math.max(anomaly, 0.57);
        if (max < 0.15 || max > 0.88 && saturation < 0.10) anomaly = Math.min(anomaly, 0.38);

        return new double[]{clamp01(warden), clamp01(flight), clamp01(fire), clamp01(nature), clamp01(anomaly)};
    }

    private static String normalizeSkinUrl(String url) {
        if (url == null || url.isBlank()) return null;
        if (url.startsWith("http://textures.minecraft.net/")) return "https://" + url.substring("http://".length());
        if (url.startsWith("https://")) return url;
        return null;
    }

    private static double hueDegrees(int r, int g, int b) {
        double rd = r / 255.0, gd = g / 255.0, bd = b / 255.0;
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
            double dr = r - prototype[0], dg = g - prototype[1], db = b - prototype[2];
            bestDistanceSquared = Math.min(bestDistanceSquared, dr * dr + dg * dg + db * db);
        }
        return Math.exp(-bestDistanceSquared / (2.0 * sigma * sigma));
    }

    private static String findSkinUrl(GameProfile profile, HttpClient client) throws Exception {
        if (profile == null) return null;
        String embedded = findEmbeddedSkinUrl(profile);
        if (embedded != null) return embedded;

        UUID profileId = extractProfileId(profile);
        String byProfileId = profileId == null ? null : findSkinUrlForUuid(profileId, client);
        if (byProfileId != null) return byProfileId;

        // Çevrimdışı UUID veya eski profil yapısında UUID oturum sunucusunda bulunamayabilir.
        // Son çare olarak oyuncu adından resmî UUID çözülüp skin tekrar istenir.
        String profileName = extractProfileName(profile);
        if (profileName == null || profileName.isBlank() || !profileName.matches("[A-Za-z0-9_]{1,16}")) return null;
        HttpRequest nameRequest = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + profileName))
            .timeout(Duration.ofSeconds(8)).header("User-Agent", "SkinPowers/1.0.10").GET().build();
        HttpResponse<String> nameResponse = sendStringWithRetry(nameRequest, 2);
        if (nameResponse == null || nameResponse.statusCode() < 200 || nameResponse.statusCode() >= 300 || nameResponse.body().isBlank()) return null;
        JsonObject profileJson = JsonParser.parseString(nameResponse.body()).getAsJsonObject();
        if (!profileJson.has("id")) return null;
        String compact = profileJson.get("id").getAsString();
        if (compact.length() != 32) return null;
        UUID officialId = UUID.fromString(compact.substring(0, 8) + "-" + compact.substring(8, 12) + "-" + compact.substring(12, 16) + "-" + compact.substring(16, 20) + "-" + compact.substring(20));
        return findSkinUrlForUuid(officialId, client);
    }

    private static String findSkinUrlForUuid(UUID profileId, HttpClient client) throws Exception {
        String compactUuid = profileId.toString().replace("-", "");
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + compactUuid + "?unsigned=false"))
            .timeout(Duration.ofSeconds(8)).header("User-Agent", "SkinPowers/1.0.10").GET().build();
        HttpResponse<String> response = sendStringWithRetry(request, 2);
        if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) return null;
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!root.has("properties")) return null;
        for (var element : root.getAsJsonArray("properties")) {
            JsonObject property = element.getAsJsonObject();
            if (!property.has("name") || !"textures".equals(property.get("name").getAsString()) || !property.has("value")) continue;
            String skinUrl = skinUrlFromTextureValue(property.get("value").getAsString());
            if (skinUrl != null) return skinUrl;
        }
        return null;
    }

    private static HttpResponse<String> sendStringWithRetry(HttpRequest request, int attempts) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < Math.max(1, attempts); attempt++) {
            try {
                return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (Exception exception) {
                last = exception;
                if (attempt + 1 < attempts) Thread.sleep(150L * (attempt + 1));
            }
        }
        if (last != null) throw last;
        return null;
    }

    private static HttpResponse<byte[]> sendBytesWithRetry(HttpRequest request, int attempts) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < Math.max(1, attempts); attempt++) {
            try {
                return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            } catch (Exception exception) {
                last = exception;
                if (attempt + 1 < attempts) Thread.sleep(150L * (attempt + 1));
            }
        }
        if (last != null) throw last;
        return null;
    }

    private static String findEmbeddedSkinUrl(GameProfile profile) {
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
            try { return UUID.fromString(string); } catch (IllegalArgumentException ignored) { return null; }
        }
        return null;
    }

    private static String extractProfileName(GameProfile profile) {
        Object value = invokeNoArg(profile, "name", "getName");
        return value instanceof String string ? string : null;
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
            for (Object value : iterable) if (!filterByName || "textures".equals(extractPropertyName(value))) target.add(value);
        } else if (source.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(source);
            for (int i = 0; i < length; i++) {
                Object value = java.lang.reflect.Array.get(source, i);
                if (!filterByName || "textures".equals(extractPropertyName(value))) target.add(value);
            }
        }
    }

    private static Object invokeNoArg(Object target, String... names) {
        for (String name : names) {
            try { return target.getClass().getMethod(name).invoke(target); }
            catch (ReflectiveOperationException ignored) { }
        }
        return null;
    }

    private static Object invokeOneArg(Object target, String name, Object argument) {
        for (java.lang.reflect.Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
            try { return method.invoke(target, argument); }
            catch (ReflectiveOperationException | IllegalArgumentException ignored) { }
        }
        return null;
    }

    private static String extractPropertyName(Object property) {
        if (property == null) return null;
        for (String methodName : new String[]{"name", "getName"}) {
            try {
                Object result = property.getClass().getMethod(methodName).invoke(property);
                if (result instanceof String string) return string;
            } catch (ReflectiveOperationException ignored) { }
        }
        return null;
    }

    private static String extractPropertyValue(Object property) {
        if (property == null) return null;
        for (String methodName : new String[]{"value", "getValue"}) {
            try {
                Object result = property.getClass().getMethod(methodName).invoke(property);
                if (result instanceof String string) return string;
            } catch (ReflectiveOperationException ignored) { }
        }
        return null;
    }

    private static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }

    public record Result(
        double[] scores,
        int[] dominantColors,
        boolean fromSkin,
        int[] skinPixels,
        int skinWidth,
        int skinHeight,
        double matchedFraction
    ) {
        public static Result unavailable() {
            return new Result(new double[CLASS_COUNT], new int[]{0x233044, 0x8FCBE8, 0xE95818, 0x4F8B3B, 0xD5AF42}, false, new int[0], 0, 0, 0.0);
        }

        public double score(int index) {
            return scores == null || index < 0 || index >= scores.length ? 0.0 : scores[index];
        }

        public int bestIndex() { return rankedIndex(0); }
        public int secondIndex() { return rankedIndex(1); }

        private int rankedIndex(int rank) {
            if (!fromSkin || scores == null || scores.length == 0) return -1;
            Integer[] indices = new Integer[scores.length];
            for (int i = 0; i < indices.length; i++) indices[i] = i;
            java.util.Arrays.sort(indices, (a, b) -> Double.compare(scores[b], scores[a]));
            return rank >= 0 && rank < indices.length ? indices[rank] : -1;
        }

        public boolean hasRecommendation() {
            return fromSkin && skinPixels != null && skinPixels.length > 0;
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
