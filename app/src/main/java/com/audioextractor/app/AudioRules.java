package com.audioextractor.app;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java rules, shared by the Activity, foreground service and JVM tests. */
public final class AudioRules {
    private static final Pattern LINK = Pattern.compile("https://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);
    private AudioRules() {}

    public static String extractUrl(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        Matcher m = LINK.matcher(trimmed);
        if (m.matches()) return trimmed;
        m.reset();
        if (!m.find()) return "";
        return m.group().replaceFirst("[).,;!]+$", "");
    }

    public static String validateUrl(String text) {
        String url = extractUrl(text);
        if (url.length() > 8192) throw new IllegalArgumentException("Ссылка слишком длинная.");
        try {
            URI u = URI.create(url);
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null || u.getUserInfo() != null)
                throw new IllegalArgumentException();
            String host = u.getHost().toLowerCase(Locale.ROOT);
            if ((host.equals("youtube.com") || host.endsWith(".youtube.com")) && "/playlist".equals(u.getPath()))
                throw new IllegalStateException("Укажите ссылку на отдельное видео, а не плейлист.");
            return url;
        } catch (IllegalStateException e) { throw e; }
          catch (IllegalArgumentException e) { throw new IllegalArgumentException("Вставьте корректную HTTPS-ссылку на видео."); }
    }

    public static int validateBitrate(int k) {
        if (k != 128 && k != 192 && k != 256 && k != 320) throw new IllegalArgumentException("Неподдерживаемый битрейт.");
        return k;
    }

    public static String safeName(String raw) {
        String s = raw == null ? "audio" : raw.replaceAll("(?i)\\.mp3$", "");
        s = s.replaceAll("[\\p{Cntrl}/\\\\:*?\"<>|]", "_").trim().replaceFirst("^[. ]+", "").replaceFirst("[. ]+$", "");
        if (s.isEmpty()) s = "audio";
        StringBuilder out = new StringBuilder(); int bytes = 0;
        for (int i = 0; i < s.length();) {
            int cp = s.codePointAt(i); String c = new String(Character.toChars(cp));
            int n = c.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (bytes + n > 210) break;
            out.append(c); bytes += n; i += Character.charCount(cp);
        }
        return out.toString() + ".mp3";
    }

    public static boolean fullDuration(long actualMs, double expectedSeconds) {
        if (actualMs <= 0) return false;
        if (!Double.isFinite(expectedSeconds) || expectedSeconds <= 0) return true;
        double expectedMs = expectedSeconds * 1000;
        double tolerance = Math.min(expectedMs * 0.10, Math.max(150, Math.min(10000, expectedMs * 0.015 + 250)));
        return actualMs >= expectedMs - tolerance;
    }

    public static String duration(long ms) {
        long sec = Math.max(0, ms / 1000);
        return sec >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60)
            : String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
    }
}
