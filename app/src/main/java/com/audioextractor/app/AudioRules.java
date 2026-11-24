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


    public static String errorCode(String details) {
        String text = details == null ? "" : details.toLowerCase(Locale.ROOT);
        if (text.contains("no space left") || text.contains("enospc") || text.contains("недостаточно свободного места")) return "STORAGE";
        if (text.contains("drm protected") || text.contains("drm-protected") || text.contains("private video") || text.contains("members-only") || text.contains("not available in your country") || text.contains("age-restricted")) return "RESTRICTED";
        if (text.contains("sign in to confirm") || text.contains("sign in to view") || text.contains("login required") || text.contains("authentication required") || text.contains("not a bot")) return "AUTH_REQUIRED";
        if (httpStatus(text, 429) || text.contains("too many requests")) return "HTTP_429";
        if (httpStatus(text, 403) || text.contains("403 forbidden")) return "HTTP_403";
        if (httpStatus(text, 404) || text.contains("video unavailable")) return "UNAVAILABLE";
        if (httpStatus(text, 401)) return "AUTH_REQUIRED";
        if (text.contains("timed out") || text.contains("timeout") || text.contains("connection reset") || text.contains("connection refused") || text.contains("network is unreachable") || text.contains("unable to resolve host") || text.contains("temporary failure in name resolution") || text.contains("unknownhostexception") || text.contains("unexpected eof") || text.contains("incompleteread") || text.contains("remote end closed")) return "NETWORK";
        if (text.contains("fragment") && (text.contains("unavailable") || text.contains("failed") || text.contains("missing") || text.contains("not found") || text.contains("unable to download"))) return "FRAGMENT";
        if (text.contains("ffmpeg") || text.contains("ffprobe") || text.contains("postprocessing") || text.contains("не распознан как mp3")) return "CONVERSION";
        if (text.contains("requested format is not available") || text.contains("no video formats found") || text.contains("signature extraction failed")) return "FORMAT";
        if (text.contains("аудио неполное") || text.contains("сохранена не вся")) return "INCOMPLETE";
        return "OTHER";
    }

    private static boolean httpStatus(String text, int code) {
        return Pattern.compile("(?:http(?:\\s+error|\\s+status(?:\\s+code)?)?\\s*[:=]?\\s*|status(?:\\s+code)?\\s*[:=]?\\s*)" + code + "\\b").matcher(text).find();
    }

    public static String errorText(String code) {
        switch (code) {
            case "HTTP_403": return "HTTP 403: сервер отклонил запрос. Возможны ограничения доступа или недействительная ссылка на поток.";
            case "HTTP_429": return "HTTP 429: источник ограничил частоту запросов. Повторите позднее.";
            case "AUTH_REQUIRED": return "Источник требует авторизацию или проверку доступа.";
            case "RESTRICTED": return "Видео ограничено или защищено. Нужен разрешённый источник.";
            case "UNAVAILABLE": return "Видео или аудиопоток недоступны.";
            case "NETWORK": return "Сетевой сбой или истёк тайм-аут. Проверьте соединение.";
            case "FRAGMENT": return "Не удалось получить все фрагменты аудио. Неполный файл не сохранён.";
            case "CONVERSION": return "Ошибка конвертации аудио в MP3.";
            case "FORMAT": return "Не удалось определить доступный формат аудио.";
            case "STORAGE": return "Недостаточно свободного места. Освободите память и повторите.";
            case "INCOMPLETE": return "Не удалось подтвердить полную длительность аудиодорожки.";
            default: return "Ошибка обработки. Нажмите «Скопировать диагностику», чтобы получить подробности.";
        }
    }

    public static String sanitizeDiagnostic(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("(?i)https?://[^\\s<>\"']+", "[URL]")
            .replaceAll("(?im)^\\s*(?:authorization|cookie|set-cookie)\\s*:[^\\r\\n]*", "[скрыто]")
            .replaceAll("(?i)\\b(?:token|access_token|auth|password|signature|sig|cookie|authorization)\\s*[=:]\\s*[^\\s&,;]+", "[скрыто]")
            .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
            .trim();
        return cleaned.length() > 6000 ? cleaned.substring(0, 6000) + "\n[сокращено]" : cleaned;
    }

    public static String duration(long ms) {
        long sec = Math.max(0, ms / 1000);
        return sec >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60)
            : String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
    }
}
