import com.audioextractor.app.AudioRules;
import java.nio.charset.StandardCharsets;

public class RulesSmoke {
    static int count;
    static void check(boolean x) { if (!x) throw new AssertionError("case " + (count+1)); count++; }
    static void bad(Runnable r) { try { r.run(); } catch (IllegalArgumentException | IllegalStateException e) { count++; return; } throw new AssertionError("accepted bad input"); }
    public static void main(String[] args) {
        check(AudioRules.validateUrl("Видео https://youtu.be/abc123?si=xyz\n").equals("https://youtu.be/abc123?si=xyz"));
        check(AudioRules.validateUrl("https://example.org/v.mp4?token=x!").endsWith("!"));
        check(AudioRules.validateUrl("https://www.youtube.com/watch?v=abc&list=123").contains("watch"));
        check(AudioRules.validateUrl("https://example.org/stream.m3u8").endsWith("m3u8"));
        bad(() -> AudioRules.validateUrl("http://example.org/v.mp4")); bad(() -> AudioRules.validateUrl("file:///a"));
        bad(() -> AudioRules.validateUrl("https://user:pass@example.org/a")); bad(() -> AudioRules.validateUrl("https://"));
        bad(() -> AudioRules.validateUrl("https://www.youtube.com/playlist?list=1")); bad(() -> AudioRules.validateUrl(""));
        check(AudioRules.safeName("Музыка/ тест").equals("Музыка_ тест.mp3")); check(AudioRules.safeName(".. ").equals("audio.mp3"));
        check(AudioRules.safeName("hello.mp3").equals("hello.mp3")); check(AudioRules.safeName("🎵".repeat(200)).getBytes(StandardCharsets.UTF_8).length <= 214);
        check(AudioRules.fullDuration(59800,60)); check(!AudioRules.fullDuration(30000,60)); check(!AudioRules.fullDuration(0,0)); check(AudioRules.fullDuration(1000,Double.NaN));
        check(AudioRules.duration(3661000).equals("1:01:01"));
        for(int k:new int[]{128,192,256,320}) check(AudioRules.validateBitrate(k)==k);
        bad(() -> AudioRules.validateBitrate(400));
        System.out.println("PASS: " + count + " checks");
    }
}
