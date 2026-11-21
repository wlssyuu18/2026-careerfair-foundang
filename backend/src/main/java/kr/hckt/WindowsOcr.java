package kr.hckt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.*;

final class WindowsOcr {
    // shortcut: Windows 한국어 OCR을 우선 사용, 다른 OS나 인식 실패는 기존 이미지 모델로 분석.
    static String read(byte[] image) {
        if (!System.getProperty("os.name").startsWith("Windows")) return "";
        Process process = null;
        try (var resource = WindowsOcr.class.getResourceAsStream("/windows-ocr.ps1"); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            String script = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-EncodedCommand", Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE)))
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            Process child = process;
            try {
                var output = workers.submit(() -> new String(child.getInputStream().readNBytes(60001), StandardCharsets.UTF_8));
                try (var input = process.getOutputStream()) { input.write(Base64.getEncoder().encode(image)); }
                if (!process.waitFor(6, TimeUnit.SECONDS)) return "";
                String text = output.get();
                return process.exitValue() == 0 && text.length() <= 20000 ? text : "";
            } finally { if (child.isAlive()) child.destroyForcibly(); }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("작업이 중단되었어요."); }
        catch (IOException | ExecutionException e) { return ""; }
        finally { if (process != null && process.isAlive()) process.destroyForcibly(); }
    }
    private WindowsOcr() {}
}
