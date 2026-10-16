package com.rick.frontendgen.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.rick.frontendgen.ai.AiCodeGenTypeRoutingService;
import com.rick.frontendgen.ai.AiCodeGenTypeRoutingServiceFactory;
import com.rick.frontendgen.constant.AppConstant;
import com.rick.frontendgen.core.AiCodeGeneratorFacade;
import com.rick.frontendgen.core.builder.VueProjectBuilder;
import com.rick.frontendgen.core.handler.StreamHandlerExecutor;
import com.rick.frontendgen.dto.app.AppAddRequest;
import com.rick.frontendgen.dto.app.AppQueryRequest;
import com.rick.frontendgen.entity.App;
import com.rick.frontendgen.entity.User;
import com.rick.frontendgen.enums.ChatHistoryMessageTypeEnum;
import com.rick.frontendgen.enums.CodeGenTypeEnum;
import com.rick.frontendgen.exception.BusinessException;
import com.rick.frontendgen.exception.ErrorCode;
import com.rick.frontendgen.exception.ThrowUtils;
import com.rick.frontendgen.innerservice.InnerScreenshotService;
import com.rick.frontendgen.innerservice.InnerUserService;
import com.rick.frontendgen.mapper.AppMapper;
import com.rick.frontendgen.service.AppService;
import com.rick.frontendgen.service.ChatHistoryService;
import com.rick.frontendgen.vo.AppVO;
import com.rick.frontendgen.vo.UserVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 应用服务层实现
 */
@Slf4j
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Value("${code.deploy-host:http://localhost}")
    private String deployHost;

    @DubboReference
    private InnerUserService userService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @DubboReference
    private InnerScreenshotService screenshotService;

    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;

    @Value("${server.port:8123}")
    private int serverPort;

    @Value("${server.servlet.context-path:}")
    private String serverContextPath;

    /** 防止同一应用在短时间内重复执行首次封面生成任务。 */
    private final Set<Long> previewCoverTasks = ConcurrentHashMap.newKeySet();

    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1.参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "提示词不能为空");

        // 2.查询应用信息
        App app = getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        // 3.权限校验,仅本人可以和自己的应用对话
        if(!app.getUserId().equals(loginUser.getId())){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }

        // 4.获取应用的代码生成类型
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if(codeGenTypeEnum == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用代码生成类型错误");
        }
        
        // 5. 在调用 AI 前，先保存用户消息到数据库中
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        
        // 6. 调用 AI 生成代码（流式）
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                message, codeGenTypeEnum, appId, codeGenType);
        
        // 7. 收集 AI 响应的内容，并且在完成后保存记录到对话历史
        Flux<String> handledStream = streamHandlerExecutor.doExecute(
                codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum);
        
        return handledStream;
        
        //// 8. Vue 预览依赖 dist，必须在 SSE 完成前等待项目构建成功。
        //Flux<String> previewReadyStream = waitForVueProjectBuild(
        //        handledStream, appId, codeGenTypeEnum);
        //
        //// 9. 预览就绪后再结束 SSE；封面生成仍异步执行，不阻塞用户查看预览。
        //
        //// doOnComplete 不会在模型生成失败、Vue 构建失败或客户端取消连接时执行。
        //return previewReadyStream.doOnComplete(
        //        () -> generatePreviewCoverAsync(appId, codeGenTypeEnum));
    }

    ///**
    // * Vue 项目生成完成后执行构建，并将下游完成信号推迟到构建成功之后。
    // * npm install 和 npm run build 都是阻塞操作，因此放到 boundedElastic 调度器执行，
    // * 避免占用 LangChain4j 的流式响应线程。
    // */
    //private Flux<String> waitForVueProjectBuild(Flux<String> handledStream,
    //                                             Long appId,
    //                                             CodeGenTypeEnum codeGenType) {
    //    if (codeGenType != CodeGenTypeEnum.VUE_PROJECT) {
    //        return handledStream;
    //    }
    //
    //    String projectDirName = codeGenType.getValue() + "_" + appId;
    //    String projectPath = new File(
    //            AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName).getAbsolutePath();
    //    Mono<String> buildCompletion = Mono.fromCallable(() -> {
    //                log.info("AI 代码生成完成，开始同步等待 Vue 项目构建，appId：{}，目录：{}",
    //                        appId, projectPath);
    //                boolean buildSuccess = vueProjectBuilder.buildProject(projectPath);
    //                if (!buildSuccess) {
    //                    throw new BusinessException(
    //                            ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请检查生成代码后重试");
    //                }
    //                log.info("Vue 项目构建完成，即将结束生成流，appId：{}", appId);
    //                return "";
    //            })
    //            .subscribeOn(Schedulers.boundedElastic())
    //            // 构建阶段只控制完成时机，不向前端额外发送正文消息。
    //            .then(Mono.<String>empty());
    //    return handledStream.concatWith(buildCompletion);
    //}

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        
        // 应用名称暂时为 initPrompt 前 12 位
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
        
        // 使用 AI 智能选择代码生成类型（多例模式）
        AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        CodeGenTypeEnum selectedCodeGenType = aiCodeGenTypeRoutingService.routeCodeGenType(initPrompt);
        app.setCodeGenType(selectedCodeGenType.getValue());
        
        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1.参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        
        // 2.查询应用信息
        App app = getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        
        // 3.权限校验,仅本人可以部署自己的应用
        if(!app.getUserId().equals(loginUser.getId())){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        
        // 4.检查是否已有deployKey
        String deployKey = app.getDeployKey();
        // 若没有,则生成6位的deployKey(字母➕数字)
        if(StrUtil.isBlank(deployKey)){
            deployKey = RandomUtil.randomString(6);
        }
        
        // 5.获取代码生成类型,获取原始代码生成路径
        String codeGenType = app.getCodeGenType();
        String sourceDirname = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirname;
        
        // 6.检查路径是否存在
        File sourceDir = new File(sourceDirPath);
        if(!sourceDir.exists() || !sourceDir.isDirectory()){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用代码路径不存在,请先生成应用");
        }
        
        // 7. Vue 项目特殊处理：执行构建
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            // Vue 项目需要构建
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请重试");
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            // 构建完成后，需要将构建后的文件复制到部署目录
            sourceDir = distDir;
        }
        
        // 8. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用部署失败：" + e.getMessage());
        }
        
        // 9. 更新数据库
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        
        // 10. 得到可访问的 URL 地址
        String appDeployUrl = String.format("%s/%s", AppConstant.CODE_DEPLOY_HOST, deployKey);
        
        // 11. 异步生成截图并且更新应用封面
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;
    }



    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        // 使用虚拟线程并执行
        Thread.startVirtualThread(() -> {
            // 调用截图服务生成截图并上传
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
            // 更新数据库的封面
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updated = this.updateById(updateApp);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
        });
        //Thread.ofVirtual().name("app-screenshot-" + appId).start(() -> {
        //    log.info("开始异步生成应用封面，appId：{}，url：{}", appId, appUrl);
        //    try {
        //        generateAndUpdateAppCover(appId, appUrl);
        //    } catch (Exception e) {
        //        log.error("异步生成应用封面失败，appId：{}，url：{}", appId, appUrl, e);
        //    }
        //});
    }

    ///**
    // * 预览文件准备完成后，异步创建应用的首次封面。
    // * Vue 项目已经在生成流结束前完成构建，此处不得重复构建。
    // */
    //private void generatePreviewCoverAsync(Long appId, CodeGenTypeEnum codeGenType) {
    //    Thread.ofVirtual().name("app-generation-post-process-" + appId).start(() -> {
    //        try {
    //            String projectDirName = codeGenType.getValue() + "_" + appId;
    //            File projectDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
    //            log.info("预览文件已准备完成，开始异步生成应用封面，appId：{}，类型：{}",
    //                    appId, codeGenType.getValue());
    //
    //            App currentApp = getById(appId);
    //            if (currentApp == null) {
    //                log.warn("生成应用封面时应用不存在，appId：{}", appId);
    //                return;
    //            }
    //            if (StrUtil.isNotBlank(currentApp.getCover())) {
    //                log.debug("应用已有封面，跳过首次封面生成，appId：{}", appId);
    //                return;
    //            }
    //            if (!previewCoverTasks.add(appId)) {
    //                log.debug("应用封面生成任务已在执行，跳过重复任务，appId：{}", appId);
    //                return;
    //            }
    //
    //            File previewEntry = codeGenType == CodeGenTypeEnum.VUE_PROJECT
    //                    ? new File(projectDir, "dist/index.html")
    //                    : new File(projectDir, "index.html");
    //            try {
    //                if (!previewEntry.isFile()) {
    //                    log.error("应用预览入口不存在，无法生成封面，appId：{}，文件：{}",
    //                            appId, previewEntry.getAbsolutePath());
    //                    return;
    //                }
    //
    //                String previewUrl = buildGeneratedAppPreviewUrl(
    //                        serverPort, serverContextPath, codeGenType, appId);
    //                log.info("开始生成预览应用封面，appId：{}，类型：{}，url：{}",
    //                        appId, codeGenType.getValue(), previewUrl);
    //                generateAndUpdateAppCover(appId, previewUrl);
    //            } finally {
    //                previewCoverTasks.remove(appId);
    //            }
    //        } catch (Exception e) {
    //            log.error("生成预览应用封面失败，appId：{}，类型：{}",
    //                    appId, codeGenType.getValue(), e);
    //        }
    //    });
    //}
    //
    //private void generateAndUpdateAppCover(Long appId, String appUrl) {
    //    String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
    //    App updateApp = new App();
    //    updateApp.setId(appId);
    //    updateApp.setCover(screenshotUrl);
    //    boolean updated = this.updateById(updateApp);
    //    ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
    //    log.info("应用封面生成并更新成功，appId：{}，cover：{}", appId, screenshotUrl);
    //}

    //static String buildGeneratedAppPreviewUrl(int port,
    //                                          String contextPath,
    //                                          CodeGenTypeEnum codeGenType,
    //                                          Long appId) {
    //    String normalizedContextPath = StrUtil.blankToDefault(contextPath, "").trim();
    //    if (!normalizedContextPath.isEmpty() && !normalizedContextPath.startsWith("/")) {
    //        normalizedContextPath = "/" + normalizedContextPath;
    //    }
    //    while (normalizedContextPath.endsWith("/")) {
    //        normalizedContextPath = normalizedContextPath.substring(0, normalizedContextPath.length() - 1);
    //    }
    //    String projectKey = codeGenType.getValue() + "_" + appId;
    //    String entryPath = codeGenType == CodeGenTypeEnum.VUE_PROJECT
    //            ? "/dist/index.html"
    //            : "/";
    //    return "http://127.0.0.1:" + port + normalizedContextPath
    //            + "/static/" + projectKey + entryPath;
    //}

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    /**
     * 删除应用时，关联删除对话历史
     *
     * @param id 应用id
     * @return 是否删除成功
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        long appId = Long.parseLong(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            log.error("删除应用关联的对话历史失败：{}", e.getMessage());
        }
        // 删除应用: 调用父类 com.mybatisflex.spring.service.impl.ServiceImpl 中定义的 removeById 方法
        return super.removeById(id);
    }
}
