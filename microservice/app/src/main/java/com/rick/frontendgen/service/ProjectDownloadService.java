package com.rick.frontendgen.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 项目下载服务
 */
public interface ProjectDownloadService {


    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);
}
