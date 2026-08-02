package com.renix.codegen.ai.tools;

import com.renix.codegen.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;

/**
 * 文件写入工具
 * 支持 AI 通过工具调用的方式写入文件
 */
@Slf4j
public class FileWriteTool {

    private final String outputDirectoryPrefix;

    public FileWriteTool(String outputDirectoryPrefix) {
        this.outputDirectoryPrefix = outputDirectoryPrefix;
    }

    @Tool("写入文件到指定路径")
    public String writeFile(
            @P("文件的相对路径")
            String relativeFilePath,
            @P("要写入文件的内容")
            String content,
            @ToolMemoryId Long appId
    ) {
        try {
            Path requestedPath = Paths.get(relativeFilePath);
            if (requestedPath.isAbsolute()) {
                return "文件写入失败: 只允许使用项目内的相对路径";
            }
            String projectDirName = outputDirectoryPrefix + "_" + appId;
            Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName)
                    .toAbsolutePath()
                    .normalize();
            Path path = projectRoot.resolve(requestedPath).normalize();
            if (!path.startsWith(projectRoot)) {
                return "文件写入失败: 路径不能超出项目目录";
            }
            // 创建父目录（如果不存在）
            Path parentDir = path.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            // 写入文件内容
            Files.write(path, content.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功写入文件: {}", path.toAbsolutePath());
            // 注意要返回相对路径，不能让 AI 把文件绝对路径返回给用户
            return "文件写入成功: " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "文件写入失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }
}
