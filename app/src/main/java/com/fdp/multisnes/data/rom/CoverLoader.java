package com.fdp.multisnes.data.rom;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MÓDULO 1: Sistema Inteligente de Carátulas (LibRetro + Fuzzy Matching + Caché Local).
 * No modifica jamás los archivos .zip / .sfc / .smc originales para preservar intacto
 * el hash CRC32/MD5 utilizado en el modo Online Hotspot.
 */
public final class CoverLoader {

    public static final String LIBRETRO_BOXARTS_BASE_URL =
            "https://raw.githubusercontent.com/libretro-thumbnails/Nintendo_-_Super_Nintendo_Entertainment_System/master/Named_Boxarts/";

    private static final String GITHUB_TREES_API_URL =
            "https://api.github.com/repos/libretro-thumbnails/Nintendo_-_Super_Nintendo_Entertainment_System/git/trees/master?recursive=1";

    private static final String[] REGION_SUFFIX_CASCADE = new String[]{
            "",
            " (USA)",
            " (Europe)",
            " (Japan)",
            " (USA, Europe)",
            " (World)"
    };

    private static final ExecutorService DOWNLOAD_EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Set<String> IN_FLIGHT_REQUESTS =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private static volatile List<String> cachedRepoIndex = null;
    private static final Object INDEX_LOCK = new Object();

    public interface CoverCallback {
        void onCoverLoaded(File cachedFile, Bitmap bitmap);
        void onCoverUnavailable();
    }

    private CoverLoader() {
    }

    /**
     * Normalización oficial de nombres para LibRetro:
     * - Quita rutas y extensiones (.zip, .sfc, .smc, .swc, .fig)
     * - Elimina etiquetas entre paréntesis (...) o corchetes [...]
     * - Reemplaza caracteres prohibidos por LibRetro (: & / * ? \ " < > |) por guiones bajos '_'
     */
    public static String cleanRomName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "Unknown_SNES_ROM";
        }

        String decoded = Uri.decode(fileName);
        int lastSlash = Math.max(decoded.lastIndexOf('/'), decoded.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash + 1 < decoded.length()) {
            decoded = decoded.substring(lastSlash + 1);
        }
        int lastColon = decoded.lastIndexOf(':');
        if (lastColon >= 0 && lastColon + 1 < decoded.length()) {
            decoded = decoded.substring(lastColon + 1);
        }

        // Quitar extensiones de ROM o archivo comprimido (.zip, .sfc, .smc, .swc, .fig)
        decoded = decoded.replaceFirst("(?i)\\.(zip|sfc|smc|swc|fig|png|jpg|jpeg|webp)$", "");

        // Eliminar etiquetas entre paréntesis (...) y corchetes [...]
        decoded = decoded.replaceAll("\\([^)]*\\)", "");
        decoded = decoded.replaceAll("\\[[^]]*\\]", "");

        // Reemplazar caracteres prohibidos por el repositorio de miniaturas de LibRetro por '_'
        decoded = decoded.replaceAll("[:&/*?\\\\\"<>|]", "_");

        // Normalizar espacios múltiples
        decoded = decoded.replaceAll("\\s+", " ").trim();

        return decoded.isEmpty() ? "SNES_Game" : decoded;
    }

    /**
     * Devuelve el archivo destino en la caché externa local:
     * new File(context.getExternalFilesDir(null), "covers/" + cleanName + ".png")
     */
    public static File getLocalCoverFile(Context context, String cleanName) {
        File baseDir = context.getExternalFilesDir(null);
        if (baseDir == null) {
            baseDir = context.getFilesDir();
        }
        File coversDir = new File(baseDir, "covers");
        if (!coversDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            coversDir.mkdirs();
        }
        return new File(coversDir, cleanName + ".png");
    }

    /**
     * Verifica de forma síncrona si ya existe una carátula en la caché local o junto a la ROM.
     */
    public static File findExistingLocalCover(Context context, String romFileNameOrPath, String fallbackTitle) {
        String cleanFromFile = cleanRomName(romFileNameOrPath);
        File primaryCache = getLocalCoverFile(context, cleanFromFile);
        if (primaryCache.exists() && primaryCache.length() > 64) {
            return primaryCache;
        }

        if (fallbackTitle != null && !fallbackTitle.isEmpty()) {
            String cleanFromTitle = cleanRomName(fallbackTitle);
            File secondaryCache = getLocalCoverFile(context, cleanFromTitle);
            if (secondaryCache.exists() && secondaryCache.length() > 64) {
                return secondaryCache;
            }
        }

        // Verificar si existe en la misma carpeta física de la ROM del usuario
        if (romFileNameOrPath != null && !romFileNameOrPath.startsWith("content://")) {
            File romFile = new File(romFileNameOrPath);
            File parent = romFile.getParentFile();
            if (parent != null && parent.exists() && parent.canRead()) {
                String[] exts = new String[]{".png", ".jpg", ".jpeg", ".webp"};
                String rawBase = romFile.getName().replaceFirst("(?i)\\.(zip|sfc|smc|swc|fig)$", "");
                for (String ext : exts) {
                    File sibling1 = new File(parent, rawBase + ext);
                    if (sibling1.exists() && sibling1.length() > 64) return sibling1;
                    File sibling2 = new File(parent, cleanFromFile + ext);
                    if (sibling2.exists() && sibling2.length() > 64) return sibling2;
                }
            }
        }

        return null;
    }

    /**
     * Carga la carátula en un ImageView clásico de Android (ocultando el placeholder de texto al cargar)
     * o notifica por callback para integración híbrida con Jetpack Compose.
     */
    public static void loadCover(
            final Context context,
            final String romFileNameOrPath,
            final String fallbackHeaderTitle,
            final ImageView targetImageView,
            final View placeholderTextView,
            final CoverCallback callback
    ) {
        final Context appContext = context.getApplicationContext();
        final String cleanPrimary = cleanRomName(romFileNameOrPath);

        // Prioridad 1: Caché Local inmediata (Offline)
        File existing = findExistingLocalCover(appContext, romFileNameOrPath, fallbackHeaderTitle);
        if (existing != null) {
            final Bitmap cachedBmp = BitmapFactory.decodeFile(existing.getAbsolutePath());
            if (cachedBmp != null) {
                final File foundFile = existing;
                MAIN_HANDLER.post(new Runnable() {
                    @Override
                    public void run() {
                        if (targetImageView != null) {
                            targetImageView.setImageBitmap(cachedBmp);
                            targetImageView.setVisibility(View.VISIBLE);
                        }
                        if (placeholderTextView != null) {
                            placeholderTextView.setVisibility(View.GONE);
                        }
                        if (callback != null) {
                            callback.onCoverLoaded(foundFile, cachedBmp);
                        }
                    }
                });
                return;
            }
        }

        // Mientras descarga o si no hay internet, mantener visible el diseño fallback con degradado/título
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                if (placeholderTextView != null) {
                    placeholderTextView.setVisibility(View.VISIBLE);
                }
            }
        });

        // Evitar descargas duplicadas simultáneas para la misma ROM
        if (!IN_FLIGHT_REQUESTS.add(cleanPrimary)) {
            return;
        }

        // Prioridad 2: Descarga inteligente en hilo secundario (Cascada directa -> Índice + Fuzzy Matching)
        DOWNLOAD_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    File destFile = getLocalCoverFile(appContext, cleanPrimary);
                    Bitmap downloaded = downloadWithCascadeAndFuzzy(
                            appContext,
                            cleanPrimary,
                            fallbackHeaderTitle != null ? cleanRomName(fallbackHeaderTitle) : null,
                            destFile
                    );

                    if (downloaded != null && destFile.exists()) {
                        final Bitmap finalBmp = downloaded;
                        final File finalFile = destFile;
                        MAIN_HANDLER.post(new Runnable() {
                            @Override
                            public void run() {
                                if (targetImageView != null) {
                                    targetImageView.setImageBitmap(finalBmp);
                                    targetImageView.setVisibility(View.VISIBLE);
                                }
                                if (placeholderTextView != null) {
                                    placeholderTextView.setVisibility(View.GONE);
                                }
                                if (callback != null) {
                                    callback.onCoverLoaded(finalFile, finalBmp);
                                }
                            }
                        });
                    } else {
                        MAIN_HANDLER.post(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) {
                                    callback.onCoverUnavailable();
                                }
                            }
                        });
                    }
                } finally {
                    IN_FLIGHT_REQUESTS.remove(cleanPrimary);
                }
            }
        });
    }

    private static Bitmap downloadWithCascadeAndFuzzy(
            Context context,
            String cleanPrimary,
            String cleanHeaderFallback,
            File outTargetFile
    ) {
        List<String> candidateNames = new ArrayList<String>();
        candidateNames.add(cleanPrimary);
        if (cleanHeaderFallback != null
                && !cleanHeaderFallback.isEmpty()
                && !cleanHeaderFallback.equalsIgnoreCase(cleanPrimary)) {
            candidateNames.add(cleanHeaderFallback);
        }

        // PASO A: Cascada directa probando "", " (USA)", " (Europe)", " (Japan)", " (USA, Europe)", " (World)" + ".png"
        for (String baseName : candidateNames) {
            for (String suffix : REGION_SUFFIX_CASCADE) {
                String fileName = baseName + suffix + ".png";
                String encodedFile = Uri.encode(fileName);
                String fullUrl = LIBRETRO_BOXARTS_BASE_URL + encodedFile;
                Bitmap bmp = tryDownloadPngToFile(fullUrl, outTargetFile);
                if (bmp != null) {
                    return bmp;
                }
            }
        }

        // PASO B: Índice de GitHub Trees API + Emparejamiento Difuso (Fuzzy Matching)
        List<String> index = getOrFetchRepositoryIndex(context);
        if (index != null && !index.isEmpty()) {
            for (String baseName : candidateNames) {
                String matchedRepoFile = findBestFuzzyMatch(index, baseName);
                if (matchedRepoFile != null) {
                    String encodedFile = Uri.encode(matchedRepoFile);
                    String fullUrl = LIBRETRO_BOXARTS_BASE_URL + encodedFile;
                    Bitmap bmp = tryDownloadPngToFile(fullUrl, outTargetFile);
                    if (bmp != null) {
                        return bmp;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Busca en el índice del repositorio LibRetro la mejor entrada que comience con cleanName
     * (ignorando mayúsculas/minúsculas y signos), priorizando (USA) y (Europe).
     */
    public static String findBestFuzzyMatch(List<String> repoFiles, String cleanName) {
        if (repoFiles == null || repoFiles.isEmpty() || cleanName == null || cleanName.isEmpty()) {
            return null;
        }

        String lowerTarget = cleanName.toLowerCase(Locale.ROOT).trim();
        String alphaNumTarget = lowerTarget.replaceAll("[^a-z0-9]", "");

        String bestMatch = null;
        int bestScore = -1;

        for (String entry : repoFiles) {
            String lowerEntry = entry.toLowerCase(Locale.ROOT);
            boolean startsExact = lowerEntry.startsWith(lowerTarget);
            boolean startsAlphaNum = false;

            if (!startsExact && alphaNumTarget.length() >= 4) {
                String entryNoExt = lowerEntry.replaceFirst("\\.png$", "");
                String entryBase = entryNoExt.replaceAll("\\([^)]*\\)", "").replaceAll("[^a-z0-9]", "");
                startsAlphaNum = entryBase.startsWith(alphaNumTarget) || alphaNumTarget.startsWith(entryBase);
            }

            if (!startsExact && !startsAlphaNum) {
                continue;
            }

            int score = startsExact ? 100 : 60;

            // Si después del nombre viene inmediatamente " (" (ej. "Top Gear (USA).png"), darle máxima prioridad
            if (lowerEntry.startsWith(lowerTarget + " (") || lowerEntry.equals(lowerTarget + ".png")) {
                score += 50;
            }

            // Prioridad regional solicitada: (USA) > (Europe) > (USA, Europe) / (World) > (Japan)
            if (lowerEntry.contains("(usa)")) {
                score += 40;
            } else if (lowerEntry.contains("(europe)")) {
                score += 35;
            } else if (lowerEntry.contains("(usa, europe)") || lowerEntry.contains("(world)")) {
                score += 30;
            } else if (lowerEntry.contains("(japan)")) {
                score += 20;
            }

            // Penalizar betas/protos/demos si existe la versión comercial
            if (lowerEntry.contains("(beta") || lowerEntry.contains("(proto") || lowerEntry.contains("(sample")) {
                score -= 25;
            }

            if (score > bestScore) {
                bestScore = score;
                bestMatch = entry;
            }
        }

        return bestMatch;
    }

    private static List<String> getOrFetchRepositoryIndex(Context context) {
        if (cachedRepoIndex != null && !cachedRepoIndex.isEmpty()) {
            return cachedRepoIndex;
        }

        synchronized (INDEX_LOCK) {
            if (cachedRepoIndex != null && !cachedRepoIndex.isEmpty()) {
                return cachedRepoIndex;
            }

            File baseDir = context.getExternalFilesDir(null);
            if (baseDir == null) baseDir = context.getFilesDir();
            File coversDir = new File(baseDir, "covers");
            if (!coversDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                coversDir.mkdirs();
            }
            File indexCacheFile = new File(coversDir, "libretro_boxarts_index.txt");

            // 1. Leer índice desde caché en disco si tiene menos de 14 días
            long maxAgeMs = 14L * 24L * 3600L * 1000L;
            if (indexCacheFile.exists()
                    && indexCacheFile.length() > 1024
                    && (System.currentTimeMillis() - indexCacheFile.lastModified() < maxAgeMs)) {
                List<String> fromDisk = readIndexFromDisk(indexCacheFile);
                if (!fromDisk.isEmpty()) {
                    cachedRepoIndex = fromDisk;
                    return cachedRepoIndex;
                }
            }

            // 2. Descargar índice desde GitHub Trees API y guardarlo en caché local
            HttpURLConnection conn = null;
            try {
                URL url = new URL(GITHUB_TREES_API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(7000);
                conn.setReadTimeout(12000);
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "MultiSnesByFdp-Android");

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    StringBuilder sb = new StringBuilder(256 * 1024);
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
                    );
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject root = new JSONObject(sb.toString());
                    JSONArray tree = root.optJSONArray("tree");
                    if (tree != null) {
                        List<String> parsed = new ArrayList<String>(tree.length());
                        String prefix = "Named_Boxarts/";
                        for (int i = 0; i < tree.length(); i++) {
                            JSONObject item = tree.optJSONObject(i);
                            if (item == null) continue;
                            String path = item.optString("path", "");
                            if (path.startsWith(prefix) && path.endsWith(".png")) {
                                parsed.add(path.substring(prefix.length()));
                            }
                        }

                        if (!parsed.isEmpty()) {
                            writeIndexToDisk(indexCacheFile, parsed);
                            cachedRepoIndex = parsed;
                            return cachedRepoIndex;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Si falla la red, intentar usar caché en disco aunque sea antigua
                if (indexCacheFile.exists()) {
                    List<String> fallbackDisk = readIndexFromDisk(indexCacheFile);
                    if (!fallbackDisk.isEmpty()) {
                        cachedRepoIndex = fallbackDisk;
                        return cachedRepoIndex;
                    }
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }

            return Collections.emptyList();
        }
    }

    private static List<String> readIndexFromDisk(File file) {
        List<String> list = new ArrayList<String>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    list.add(trimmed);
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private static void writeIndexToDisk(File file, List<String> entries) {
        try (FileWriter fw = new FileWriter(file, false)) {
            for (String s : entries) {
                fw.write(s);
                fw.write('\n');
            }
        } catch (Exception ignored) {
        }
    }

    private static Bitmap tryDownloadPngToFile(String urlString, File destinationFile) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "MultiSnesByFdp-Android");

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                return null;
            }

            File tempFile = new File(destinationFile.getParentFile(), destinationFile.getName() + ".tmp");
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }

            Bitmap decoded = BitmapFactory.decodeFile(tempFile.getAbsolutePath());
            if (decoded != null && decoded.getWidth() > 16 && decoded.getHeight() > 16) {
                if (destinationFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    destinationFile.delete();
                }
                //noinspection ResultOfMethodCallIgnored
                tempFile.renameTo(destinationFile);
                return decoded;
            } else {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
                return null;
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
