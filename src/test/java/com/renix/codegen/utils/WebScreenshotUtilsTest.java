package com.renix.codegen.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebScreenshotUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void saveWebPageScreenshot2(){
        String url = System.getProperty("screenshot.test.url", "https://www.prcv.cn/web/#/registration");
        String screenshotPath = WebScreenshotUtils.saveWebPageScreenshot(url);
        try {
            assertNotNull(screenshotPath);
            assertTrue(Files.isRegularFile(Path.of(screenshotPath)));
        } finally {
            if (screenshotPath != null) {
                Path screenshotFile = Path.of(screenshotPath);
                try {
                    Files.deleteIfExists(screenshotFile);
                    Files.deleteIfExists(screenshotFile.getParent());
                } catch (Exception ignored) {
                    // 测试清理失败不影响截图功能断言
                }
            }
        }
    }

    @Test
    void saveWebPageScreenshot() throws Exception {
        Path htmlFile = tempDir.resolve("screenshot-test.html");
        Files.writeString(htmlFile, "<html><body><h1>Screenshot test</h1></body></html>");

        String screenshotPath = WebScreenshotUtils.saveWebPageScreenshot(htmlFile.toUri().toString());
        try {
            assertNotNull(screenshotPath);
            assertTrue(Files.isRegularFile(Path.of(screenshotPath)));
            assertTrue(Files.size(Path.of(screenshotPath)) > 0);
        } finally {
            if (screenshotPath != null) {
                Path screenshotFile = Path.of(screenshotPath);
                Files.deleteIfExists(screenshotFile);
                Files.deleteIfExists(screenshotFile.getParent());
            }
        }
    }


}
