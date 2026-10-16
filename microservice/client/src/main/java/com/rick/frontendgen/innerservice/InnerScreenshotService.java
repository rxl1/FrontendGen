package com.rick.frontendgen.innerservice;

/**
 * 内部截图服务
 */
public interface InnerScreenshotService {

    /**
     * 通用的截图服务，可以得到访问地址
     *
     * @param webUrl 网址
     * @return
     */
    String generateAndUploadScreenshot(String webUrl);
}
