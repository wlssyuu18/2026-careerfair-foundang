package kr.hckt;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

class WindowsOcrTest {
    @Test @EnabledIfEnvironmentVariable(named = "HCKT_LIVE_OCR", matches = "true")
    void readsKoreanScheduleWithoutSavingImage() throws Exception {
        var image = new BufferedImage(1200, 400, BufferedImage.TYPE_INT_RGB);
        var draw = image.createGraphics();
        draw.setColor(Color.WHITE); draw.fillRect(0, 0, 1200, 400);
        draw.setColor(Color.BLACK); draw.setFont(new Font("Malgun Gothic", Font.PLAIN, 38));
        draw.drawString("화상면접 안내", 50, 80);
        draw.drawString("일시: 2099-11-27 13:00~14:00", 50, 160);
        draw.drawString("준비물: 웹캠, 마이크", 50, 240); draw.dispose();
        var bytes = new ByteArrayOutputStream(); ImageIO.write(image, "png", bytes);
        long start = System.nanoTime();
        String text = WindowsOcr.read(bytes.toByteArray());
        System.out.println("Native OCR duration ms: " + (System.nanoTime() - start) / 1_000_000);
        assertTrue(text.contains("2099"), "Native OCR must read the schedule");
        var parsed = RequestConditions.read(text, java.time.LocalDate.now(), "서울 마포구", 10000, java.util.List.of());
        assertEquals("13:00", parsed.start());
        assertEquals("14:00", parsed.end());
    }
}
