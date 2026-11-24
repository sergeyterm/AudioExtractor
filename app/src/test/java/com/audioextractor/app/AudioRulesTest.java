package com.audioextractor.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class AudioRulesTest {
    @Test public void sharedUrl() { assertEquals("https://youtu.be/abc123?si=xyz", AudioRules.validateUrl("Видео https://youtu.be/abc123?si=xyz\n")); }
    @Test public void fullUrl() { assertEquals("https://example.org/clip.mp4?token=a!",AudioRules.validateUrl("https://example.org/clip.mp4?token=a!")); }
    @Test public void unicodeNames() { assertEquals("Музыка_ тест.mp3",AudioRules.safeName("Музыка/ тест")); }
    @Test public void longNames() { assertTrue(AudioRules.safeName("🎵".repeat(200)).getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 214); }
    @Test public void duration() { assertTrue(AudioRules.fullDuration(59800,60)); assertFalse(AudioRules.fullDuration(30000,60)); assertFalse(AudioRules.fullDuration(0,0)); assertTrue(AudioRules.fullDuration(123,Double.NaN)); }
    @Test public void bitrates() { for(int k:new int[]{128,192,256,320}) assertEquals(k,AudioRules.validateBitrate(k)); }
    @Test(expected=IllegalArgumentException.class) public void unsupportedBitrate() { AudioRules.validateBitrate(400); }
    @Test(expected=IllegalArgumentException.class) public void rejectFile() { AudioRules.validateUrl("file:///etc/passwd"); }
    @Test(expected=IllegalArgumentException.class) public void rejectHttp() { AudioRules.validateUrl("http://example.com/media"); }
    @Test(expected=IllegalArgumentException.class) public void rejectCredentials() { AudioRules.validateUrl("https://user:secret@example.com/media"); }
    @Test(expected=IllegalStateException.class) public void rejectPlaylist() { AudioRules.validateUrl("https://www.youtube.com/playlist?list=abcd"); }
    @Test public void httpStatusCodes() {
        assertEquals("HTTP_403", AudioRules.errorCode("HTTP Error 403: Forbidden"));
        assertEquals("HTTP_429", AudioRules.errorCode("HTTP Error 429: Too Many Requests"));
        assertEquals("OTHER", AudioRules.errorCode("identifier 403xyz"));
    }
    @Test public void networkAndFragments() {
        assertEquals("NETWORK", AudioRules.errorCode("Connection reset by peer"));
        assertEquals("FRAGMENT", AudioRules.errorCode("fragment unavailable"));
        assertEquals("FORMAT", AudioRules.errorCode("Requested format is not available"));
    }
    @Test public void restrictedAndConversion() {
        assertEquals("AUTH_REQUIRED", AudioRules.errorCode("Sign in to confirm you are not a bot"));
        assertEquals("RESTRICTED", AudioRules.errorCode("private video"));
        assertEquals("CONVERSION", AudioRules.errorCode("ffmpeg postprocessing failed"));
    }
    @Test public void diagnosticRedaction() {
        String cleaned = AudioRules.sanitizeDiagnostic("https://example.org/a?token=hidden\nAuthorization: Bearer secret\npassword=private");
        assertFalse(cleaned.contains("hidden"));
        assertFalse(cleaned.contains("secret"));
        assertFalse(cleaned.contains("private"));
    }
    @Test public void fiveHourDuration() {
        assertTrue(AudioRules.fullDuration(17999990, 18000));
        assertFalse(AudioRules.fullDuration(14400000, 18000));
    }
}
