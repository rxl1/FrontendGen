package com.rick.frontendgen.controller;

import com.rick.frontendgen.constant.AppConstant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;

/**
 * 静态资源控制器
 */
@RestController
@RequestMapping("/static")
public class StaticResourceController {

    // 应用部署根目录（用于部署后的访问）
    private static final String DEPLOY_ROOT_DIR = AppConstant.CODE_DEPLOY_ROOT_DIR;

    // 应用生成根目录（用于预览）
    private static final String OUTPUT_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 提供静态资源访问，支持目录重定向
     * 访问格式：http://localhost:8123/api/static/{key}[/{fileName}]
     * 支持两种场景：
     * 1. 预览：key = {codeGenType}_{appId}，从 code_output 目录读取
     * 2. 部署：key = deployKey，从 code_deploy 目录读取
     */
    @GetMapping("/{key}/**")
    public ResponseEntity<Resource> serveStaticResource(
            @PathVariable String key,
            HttpServletRequest request) {
        try {
            // 获取资源路径
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            resourcePath = resourcePath.substring(("/static/" + key).length());
            // 如果是目录访问（不带斜杠），重定向到带斜杠的URL
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            // 默认返回 index.html
            if (resourcePath.equals("/")) {
                resourcePath = "/index.html";
            }
            // 先从部署目录查找
            String filePath = DEPLOY_ROOT_DIR + "/" + key + resourcePath;
            File file = new File(filePath);
            // 如果部署目录不存在，从代码生成目录查找
            if (!file.exists()) {
                filePath = OUTPUT_ROOT_DIR + "/" + key + resourcePath;
                file = new File(filePath);
            }
            // 检查文件是否存在
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            // 返回文件资源
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header("Content-Type", getContentTypeWithCharset(filePath))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据文件扩展名返回带字符编码的 Content-Type
     */
    private String getContentTypeWithCharset(String filePath) {
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filePath.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
