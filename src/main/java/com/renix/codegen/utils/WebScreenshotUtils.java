package com.renix.codegen.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.renix.codegen.exception.BusinessException;
import com.renix.codegen.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 截图工具类
 */
@Slf4j
public class WebScreenshotUtils {

    private static final int DEFAULT_WIDTH = 1600;

    private static final int DEFAULT_HEIGHT = 900;

    private static final String CHROME_BINARY_PROPERTY = "chrome.binary";

    private static final String CHROME_BINARY_ENV = "CHROME_BINARY";

    private static final String CHROME_VERSION_PROPERTY = "chrome.version";

    /**
     * Selenium 4.46.0 正式支持的最高 CDP 主版本。
     * 等 Selenium 正式支持 Chrome 151 后可改回 stable。
     */
    private static final String DEFAULT_CHROME_VERSION = "150";

    private static final String SELENIUM_CACHE_PATH_PROPERTY = "SE_CACHE_PATH";

    private static final String CHROME_DRIVER_PROPERTY = "webdriver.chrome.driver";

    /**
     * 生成网页截图
     *
     * @param webUrl 要截图的网址
     * @return 压缩后的截图文件路径，失败返回 null
     */
    public static String saveWebPageScreenshot(String webUrl) {
        // 非空校验
        if (StrUtil.isBlank(webUrl)) {
            log.error("网页截图失败，url为空");
            return null;
        }
        WebDriver webDriver = null;
        try {
            // 按次创建驱动，避免 ChromeDriver 在并发调用时共享状态
            webDriver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);
            // 创建临时目录
            String rootPath = System.getProperty("user.dir") + "/tmp/screenshots/" + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);
            // 图片后缀
            final String IMAGE_SUFFIX = ".png";
            // 原始图片保存路径
            String imageSavePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + IMAGE_SUFFIX;
            // 访问网页
            webDriver.get(webUrl);
            // 等待网页加载
            waitForPageLoad(webDriver);
            // 截图
            byte[] screenshotBytes = ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.BYTES);
            // 保存原始图片
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始截图保存成功：{}", imageSavePath);
            // 压缩图片
            final String COMPRESS_SUFFIX = "_compressed.jpg";
            String compressedImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + COMPRESS_SUFFIX;
            compressImage(imageSavePath, compressedImagePath);
            log.info("压缩图片保存成功：{}", compressedImagePath);
            // 删除原始图片
            FileUtil.del(imageSavePath);
            return compressedImagePath;
        } catch (Exception e) {
            log.error("网页截图失败：{}", webUrl, e);
            return null;
        } finally {
            if (webDriver != null) {
                try {
                    webDriver.quit();
                } catch (Exception e) {
                    log.warn("关闭 Chrome 浏览器失败", e);
                }
            }
        }
    }

    /**
     * 初始化 Chrome 浏览器驱动
     */
    private static WebDriver initChromeDriver(int width, int height) {
        try {
            // 配置 Chrome 选项
            ChromeOptions options = new ChromeOptions();
            configureChromeBinary(options);
            // 无头模式
            options.addArguments("--headless=new");
            // 禁用GPU（在某些环境下避免问题）
            options.addArguments("--disable-gpu");
            // 禁用沙盒模式（Docker环境需要）
            options.addArguments("--no-sandbox");
            // 禁用开发者shm使用
            options.addArguments("--disable-dev-shm-usage");
            // 设置窗口大小
            options.addArguments(String.format("--window-size=%d,%d", width, height));
            // 禁用扩展
            options.addArguments("--disable-extensions");
            // 设置用户代理
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            // 创建驱动
            WebDriver driver = new ChromeDriver(options);
            // 设置页面加载超时
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            // 设置隐式等待
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return driver;
        } catch (Exception e) {
            log.error("初始化 Chrome 浏览器失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 Chrome 浏览器失败");
        }
    }

    /**
     * 优先使用显式配置的 Chrome，其次复用 Selenium 已缓存且版本匹配的
     * Chrome for Testing 和 ChromeDriver；仅在缓存不存在时访问网络下载。
     */
    private static void configureChromeBinary(ChromeOptions options) {
        String chromeBinary = System.getProperty(CHROME_BINARY_PROPERTY);
        if (StrUtil.isBlank(chromeBinary)) {
            chromeBinary = System.getenv(CHROME_BINARY_ENV);
        }
        if (StrUtil.isNotBlank(chromeBinary)) {
            File binaryFile = FileUtil.file(chromeBinary);
            if (!binaryFile.isFile() || !binaryFile.canExecute()) {
                throw new BusinessException(
                        ErrorCode.SYSTEM_ERROR,
                        "Chrome 可执行文件不存在或不可执行：" + binaryFile.getAbsolutePath()
                );
            }
            options.setBinary(binaryFile);
            log.info("使用已配置的 Chrome：{}", binaryFile.getAbsolutePath());
            return;
        }

        String chromeVersion = System.getProperty(CHROME_VERSION_PROPERTY, DEFAULT_CHROME_VERSION);
        if (configureCachedChrome(options, chromeVersion)) {
            return;
        }
        options.setBrowserVersion(chromeVersion);
        log.info("本地没有可用的 Selenium Chrome 缓存，由 Selenium Manager 自动管理 {} 版本", chromeVersion);
    }

    /**
     * Selenium Manager 每次解析 stable 版本都可能访问网络。在网络不通时，
     * 即使浏览器已经下载，也会等待网络超时数分钟。这里直接复用版本匹配的缓存。
     */
    private static boolean configureCachedChrome(ChromeOptions options, String requestedVersion) {
        String cachePath = System.getProperty(SELENIUM_CACHE_PATH_PROPERTY);
        if (StrUtil.isBlank(cachePath)) {
            cachePath = System.getenv(SELENIUM_CACHE_PATH_PROPERTY);
        }
        if (StrUtil.isBlank(cachePath)) {
            cachePath = System.getProperty("user.home") + File.separator + ".cache" + File.separator + "selenium";
        }

        Path cacheRoot = Path.of(cachePath).toAbsolutePath().normalize();
        Path chromeRoot = cacheRoot.resolve("chrome");
        if (!Files.isDirectory(chromeRoot)) {
            return false;
        }

        try (Stream<Path> paths = Files.walk(chromeRoot, 4)) {
            List<Path> cachedChromeBinaries = paths
                    .filter(Files::isRegularFile)
                    .filter(Files::isExecutable)
                    .filter(path -> "chrome".equals(path.getFileName().toString())
                            || "chrome.exe".equalsIgnoreCase(path.getFileName().toString()))
                    .sorted(Comparator.comparing(
                            path -> path.getParent().getFileName().toString(),
                            Comparator.reverseOrder()
                    ))
                    .toList();

            for (Path chromeBinary : cachedChromeBinaries) {
                Path versionDirectory = chromeBinary.getParent();
                Path platformDirectory = versionDirectory.getParent();
                if (platformDirectory == null) {
                    continue;
                }
                String cachedVersion = versionDirectory.getFileName().toString();
                if (StrUtil.isNotBlank(requestedVersion)
                        && !"stable".equalsIgnoreCase(requestedVersion)
                        && !cachedVersion.startsWith(requestedVersion)) {
                    continue;
                }

                String driverFileName = chromeBinary.getFileName().toString().endsWith(".exe")
                        ? "chromedriver.exe"
                        : "chromedriver";
                Path driverBinary = cacheRoot.resolve("chromedriver")
                        .resolve(platformDirectory.getFileName().toString())
                        .resolve(cachedVersion)
                        .resolve(driverFileName);
                if (!Files.isRegularFile(driverBinary) || !Files.isExecutable(driverBinary)) {
                    continue;
                }

                options.setBinary(chromeBinary.toFile());
                System.setProperty(CHROME_DRIVER_PROPERTY, driverBinary.toString());
                log.info("使用 Selenium 本地缓存的 Chrome {}，无需联网检查版本：{}",
                        cachedVersion, chromeBinary);
                return true;
            }
        } catch (Exception e) {
            log.warn("读取 Selenium Chrome 缓存失败，将交由 Selenium Manager 处理：{}", cacheRoot, e);
        }
        return false;
    }

    /**
     * 保存图片到文件
     *
     * @param imageBytes
     * @param imagePath
     */
    private static void saveImage(byte[] imageBytes, String imagePath) {
        try {
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("保存图片失败：{}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }

    /**
     * 压缩图片
     *
     * @param originImagePath
     * @param compressedImagePath
     */
    private static void compressImage(String originImagePath, String compressedImagePath) {
        // 压缩图片质量（0.1 = 10% 质量）
        final float COMPRESSION_QUALITY = 0.5f;
        try {
            ImgUtil.compress(
                    FileUtil.file(originImagePath),
                    FileUtil.file(compressedImagePath),
                    COMPRESSION_QUALITY
            );
        } catch (Exception e) {
            log.error("压缩图片失败：{} -> {}", originImagePath, compressedImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片失败");
        }
    }

    /**
     * 等待页面加载完成
     *
     * @param webDriver
     */
    private static void waitForPageLoad(WebDriver webDriver) {
        try {
            // 创建等待页面加载对象
            WebDriverWait wait = new WebDriverWait(webDriver, Duration.ofSeconds(10));
            // 等待 document.readyState 为 complete
            wait.until(driver -> ((JavascriptExecutor) driver)
                    .executeScript("return document.readyState").
                    equals("complete")
            );
            // 额外等待一段时间，确保动态内容加载完成
            Thread.sleep(2000);
            log.info("页面加载完成");
        } catch (Exception e) {
            log.error("等待页面加载时出现异常，继续执行截图", e);
        }
    }
}
