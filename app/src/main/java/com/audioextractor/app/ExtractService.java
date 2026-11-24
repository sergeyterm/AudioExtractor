package com.audioextractor.app;

import android.app.*;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import org.json.JSONObject;
import kotlin.Unit;

public final class ExtractService extends Service {
    static final String START = "com.audioextractor.START", CANCEL = "com.audioextractor.CANCEL";
    static final String CHANNEL = "extract", PREFS = "audio_extractor";
    private static final int NOTIFICATION = 11, RESULT_NOTIFICATION = 12;
    public record State(boolean busy, int progress, String text, String uri, String name, long duration, long bytes) {}
    static volatile State state = new State(false, -1, "Вставьте ссылку, чтобы начать.", "", "", 0, 0);
    static volatile boolean requested;
    static volatile String diagnostic = "";
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;
    private volatile boolean timedOut;
    private volatile String processId;
    private volatile boolean done;
    private Thread worker;
    private PowerManager.WakeLock wakeLock;
    private long lastNotification;

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Извлечение аудио", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { requested = false; stopSelf(); return START_NOT_STICKY; }
        if (CANCEL.equals(intent.getAction())) {
            if (worker != null && !done) { if (!cancelled) cancel(false); } else stopSelf();
            return START_NOT_STICKY;
        }
        if (!START.equals(intent.getAction())) { requested = false; stopSelf(); return START_NOT_STICKY; }
        if (worker != null && !done) { requested = false; return START_NOT_STICKY; }
        done = false; cancelled = false; timedOut = false; lastNotification = 0;
        diagnostic = "";
        String url = intent.getStringExtra("url"); int bitrate = intent.getIntExtra("bitrate", 256);
        processId = UUID.randomUUID().toString();
        state = new State(true, -1, "Подготовка аудиодвижка…", "", "", 0, 0);
        requested = false;
        try {
            getSystemService(NotificationManager.class).cancel(RESULT_NOTIFICATION);
            startForeground(NOTIFICATION, notification(state), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } catch (RuntimeException e) {
            state = new State(false, -1, "Android не разрешил фоновую обработку. Откройте приложение и повторите.", "", "", 0, 0);
            stopSelf(); return START_NOT_STICKY;
        }
        worker = new Thread(() -> extract(url, bitrate), "audio-extractor");
        worker.start();
        return START_NOT_STICKY;
    }

    private Notification notification(State s) {
        int immutable = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), immutable);
        Notification.Builder n = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Audio Extractor").setContentText(s.text()).setContentIntent(open).setOnlyAlertOnce(true)
            .setStyle(new Notification.BigTextStyle().bigText(s.text()));
        if (s.busy()) {
            PendingIntent cancel = PendingIntent.getService(this, 1, new Intent(this, ExtractService.class).setAction(CANCEL), immutable);
            n.setOngoing(true).setProgress(100, Math.max(0, s.progress()), s.progress() < 0)
                .addAction(new Notification.Action.Builder(null, "Отмена", cancel).build());
        } else n.setAutoCancel(true);
        return n.build();
    }

    private void notifySafely(int id, Notification notification) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        try { getSystemService(NotificationManager.class).notify(id, notification); } catch (SecurityException ignored) {}
    }

    private void progress(int p, String text) {
        if (cancelled || done) return;
        State s = new State(true, p, text, "", "", 0, 0); state = s;
        main.post(() -> {
            if (done || cancelled) return;
            long now = SystemClock.elapsedRealtime();
            if (now - lastNotification >= 1000) {
                lastNotification = now;
                notifySafely(NOTIFICATION, notification(state));
            }
        });
    }

    private void checkCancelled() { if (cancelled || Thread.currentThread().isInterrupted()) throw new CancellationException(); }

    private void cancel(boolean timeout) {
        cancelled = true; timedOut = timeout;
        state = new State(true, -1, timeout ? "Остановка по лимиту Android…" : "Отмена обработки…", "", "", 0, 0);
        final String cancellingId = processId;
        Thread stopper = new Thread(() -> {
            for (int i = 0; i < 20 && !done && cancellingId.equals(processId); i++) {
                try { YoutubeDL.getInstance().destroyProcessById(cancellingId); } catch (Exception ignored) {}
                try { Thread.sleep(250); } catch (InterruptedException e) { break; }
            }
        }, "cancel-extractor");
        stopper.setDaemon(true); stopper.start();
    }

    private void extract(String rawUrl, int bitrate) {
        File job = new File(getCacheDir(), "job-" + processId);
        State result;
        try {
            String url = AudioRules.validateUrl(rawUrl); AudioRules.validateBitrate(bitrate);
            File[] stale = getCacheDir().listFiles(f -> f.isDirectory() && f.getName().startsWith("job-") && System.currentTimeMillis() - f.lastModified() > 24L * 60 * 60 * 1000);
            if (stale != null) for (File old : stale) deleteTree(old);
            if (!job.mkdirs()) throw new IOException("Не удалось создать временную папку.");
            PowerManager pm = getSystemService(PowerManager.class);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AudioExtractor:conversion");
            wakeLock.setReferenceCounted(false); wakeLock.acquire(6L * 60 * 60 * 1000);
            YoutubeDL.getInstance().init(getApplicationContext());
            FFmpeg.getInstance().init(getApplicationContext());
            checkCancelled();
            progress(-1, "Анализ ссылки и поиск аудиопотока…");
            YoutubeDLRequest r = new YoutubeDLRequest(url);
            r.addOption("--ignore-config");
            r.addOption("--no-playlist");
            r.addOption("--playlist-items", "1");
            r.addOption("--match-filter", "!is_live & !is_upcoming");
            r.addOption("--format", "bestaudio/best/bestaudio*");
            r.addOption("--extract-audio");
            r.addOption("--audio-format", "mp3");
            r.addOption("--audio-quality", bitrate + "K");
            r.addOption("--output", new File(job, "%(title).160B [%(id).32B].%(ext)s").getAbsolutePath());
            r.addOption("--write-info-json");
            r.addOption("--no-mtime");
            r.addOption("--abort-on-unavailable-fragments");
            r.addOption("--retries", "4");
            r.addOption("--fragment-retries", "10");
            r.addOption("--socket-timeout", "90");
            r.addOption("--newline");
            r.addOption("--no-colors");
            checkCancelled();
            YoutubeDL.getInstance().execute(r, processId, (percent, eta, line) -> {
                if (!cancelled && line != null) {
                    if (line.contains("[ExtractAudio]") || line.contains("[ffmpeg]")) progress(-1, "Конвертация в MP3…");
                    else if (percent != null && percent >= 0 && line.contains("[download]")) {
                        int p = Math.max(0, Math.min(100, Math.round(percent)));
                        progress(p, "Загрузка аудио: " + p + "%");
                    }
                }
                return Unit.INSTANCE;
            });
            checkCancelled();
            progress(-1, "Проверка и сохранение MP3…");
            File[] files = job.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".mp3"));
            if (files == null || files.length != 1 || files[0].length() == 0)
                throw new IOException("Не получен один готовый MP3. Проверьте, что источник содержит доступную аудиодорожку и не является прямым эфиром.");
            File mp3 = files[0];
            String title = mp3.getName(); double expected = 0;
            File[] infos = job.listFiles((d,n) -> n.endsWith(".info.json"));
            if (infos != null && infos.length == 1 && infos[0].length() < 20_000_000) {
                try {
                    JSONObject metadata = new JSONObject(new String(Files.readAllBytes(infos[0].toPath()), StandardCharsets.UTF_8));
                    expected = metadata.optDouble("duration", 0);
                    title = metadata.optString("title", title);
                } catch (Exception ignored) {}
            }
            long duration;
            try (MediaMetadataRetriever mmr = new MediaMetadataRetriever()) {
                mmr.setDataSource(mp3.getAbsolutePath());
                String d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                duration = d == null ? 0 : Long.parseLong(d);
                String mime = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
                if (mime != null && !mime.equals("audio/mpeg") && !mime.equals("audio/mp3")) throw new IOException("Полученный файл не распознан как MP3.");
            }
            if (!AudioRules.fullDuration(duration, expected)) throw new IOException("Аудио неполное или повреждено. Повторите загрузку.");
            checkCancelled();
            String name = AudioRules.safeName(title);
            Uri uri = publish(mp3, name);
            try (android.database.Cursor cursor = getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
            } catch (RuntimeException ignored) {}
            result = new State(false, 100, "MP3 сохранён", uri.toString(), name, duration, mp3.length());
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("uri", result.uri()).putString("name", result.name())
                .putLong("duration", result.duration()).putLong("bytes", result.bytes()).apply();
        } catch (CancellationException e) {
            result = new State(false, -1, timedOut ? "Остановлено: лимит фоновой работы Android." : "Обработка отменена.", "", "", 0, 0);
        } catch (Exception | LinkageError e) {
            String details = exceptionDetails(e);
            String code = AudioRules.errorCode(details);
            if (!cancelled) {
                diagnostic = "Audio Extractor 1.0.1 / " + code
                    + "\nЭтап: " + state.text()
                    + "\nAndroid API: " + Build.VERSION.SDK_INT
                    + "\n" + AudioRules.sanitizeDiagnostic(details);
            }
            result = new State(false, -1, cancelled ? (timedOut ? "Остановлено: лимит фоновой работы Android." : "Обработка отменена.") : AudioRules.errorText(code), "", "", 0, 0);
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            wakeLock = null;
            deleteTree(job);
        }
        State finished = result;
        main.post(() -> {
            done = true;
            if (!timedOut) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                notifySafely(RESULT_NOTIFICATION, notification(finished));
                stopSelf();
            }
            state = finished; requested = false;
        });
    }

    private Uri publish(File mp3, String name) throws IOException {
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg");
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/AudioExtractor/");
        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        ContentResolver cr = getContentResolver();
        Uri uri = cr.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
        if (uri == null) throw new IOException("Не удалось создать файл в папке Music.");
        boolean success = false;
        try {
            long copied = 0;
            try (InputStream in = new FileInputStream(mp3); OutputStream out = cr.openOutputStream(uri, "w")) {
                if (out == null) throw new IOException("Хранилище недоступно.");
                byte[] buf = new byte[131072]; int n;
                while ((n = in.read(buf)) != -1) { checkCancelled(); out.write(buf, 0, n); copied += n; }
                out.flush();
            }
            if (copied != mp3.length()) throw new IOException("Сохранена не вся аудиодорожка.");
            checkCancelled();
            ContentValues ready = new ContentValues(); ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (cr.update(uri, ready, null, null) != 1) throw new IOException("Не удалось завершить сохранение.");
            success = true; return uri;
        } finally { if (!success) cr.delete(uri, null, null); }
    }
    private static void deleteTree(File f) {
        File[] children = f.listFiles(); if (children != null) for (File c : children) deleteTree(c);
        f.delete();
    }
    private static String exceptionDetails(Throwable e) {
        StringBuilder out = new StringBuilder();
        Throwable cause = e;
        for (int depth = 0; cause != null && depth < 6; depth++, cause = cause.getCause()) {
            if (depth > 0) out.append("\nCause: ");
            out.append(cause.getClass().getSimpleName()).append(": ");
            if (cause.getMessage() != null) out.append(cause.getMessage());
        }
        return out.toString();
    }
    @Override public void onTimeout(int startId, int fgsType) {
        if (!done) cancel(true);
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    @Override public void onDestroy() {
        if (!done && worker != null && !cancelled) cancel(false);
        super.onDestroy();
    }
}
