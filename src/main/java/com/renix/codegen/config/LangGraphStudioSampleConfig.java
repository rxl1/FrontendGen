package com.renix.codegen.config;

import com.renix.codegen.langgraph4j.CodeGenWorkflow;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.bsc.langgraph4j.studio.springboot.LangGraphStudioConfig;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 可视化调试配置（仅作为学习） <p>
 * 访问路径: http://localhost:8123/api/
 */
@Slf4j
@Configuration
public class LangGraphStudioSampleConfig extends LangGraphStudioConfig {

    @Override
    public Map<String, LangGraphStudioServer.Instance> instanceMap() {
        // createWorkflow() 返回 CompiledGraph，用 stateGraph 字段拿到底层 StateGraph
        var workflow = new CodeGenWorkflow().createWorkflow().stateGraph;

        var instance = LangGraphStudioServer.Instance.builder()
                .title("LangGraph Studio (FrontendGen)")
                .graph(workflow)
                .build();

        return Map.of("default", instance);
    }
}