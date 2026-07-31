package com.renix.codegen.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.renix.codegen.exception.BusinessException;
import com.renix.codegen.exception.ErrorCode;
import com.renix.codegen.exception.ThrowUtils;
import com.renix.codegen.mapper.AppMapper;
import com.renix.codegen.model.dto.AppAddRequest;
import com.renix.codegen.model.dto.AppAdminUpdateRequest;
import com.renix.codegen.model.dto.AppQueryRequest;
import com.renix.codegen.model.dto.AppUpdateRequest;
import com.renix.codegen.model.entity.App;
import com.renix.codegen.model.entity.User;
import com.renix.codegen.model.enums.UserRoleEnum;
import com.renix.codegen.model.vo.AppVO;
import com.renix.codegen.service.AppService;
import com.renix.codegen.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Resource
    private UserService userService;

    @Override
    public long addApp(AppAddRequest appAddRequest, HttpServletRequest request) {
        // 1. 校验参数
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "initPrompt 不能为空");

        // 2. 获取当前登录用户
        User loginUser = userService.getLoginUser(request);

        // 3. 创建应用
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        app.setPriority(0);

        // 4. 保存应用
        boolean saveResult = this.save(app);
        ThrowUtils.throwIf(!saveResult, ErrorCode.OPERATION_ERROR, "创建应用失败");

        return app.getId();
    }

    @Override
    public boolean updateApp(AppUpdateRequest appUpdateRequest, HttpServletRequest request) {
        // 1. 校验参数
        ThrowUtils.throwIf(appUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = appUpdateRequest.getId();
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);

        // 2. 获取当前登录用户
        User loginUser = userService.getLoginUser(request);

        // 3. 查询应用是否存在
        App app = this.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        // 4. 校验是否是应用作者
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限修改该应用");
        }

        // 5. 更新应用（只更新应用名称）
        App updateApp = new App();
        updateApp.setId(id);
        updateApp.setAppName(appUpdateRequest.getAppName());

        return this.updateById(updateApp);
    }

    @Override
    public boolean deleteApp(long id, HttpServletRequest request) {
        // 1. 校验参数
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);

        // 2. 获取当前登录用户
        User loginUser = userService.getLoginUser(request);

        // 3. 查询应用是否存在
        App app = this.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        // 4. 校验是否是应用作者
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限删除该应用");
        }

        // 5. 删除应用
        return this.removeById(id);
    }

    @Override
    public AppVO getAppVO(long id) {
        // 1. 查询应用
        App app = this.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        // 2. 转换为 VO
        return getAppVO(app);
    }

    @Override
    public Page<AppVO> listMyApps(AppQueryRequest appQueryRequest, HttpServletRequest request) {
        // 1. 校验参数
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);

        // 2. 获取当前登录用户
        User loginUser = userService.getLoginUser(request);

        // 3. 限制每页最多 20 个
        long pageNum = appQueryRequest.getPageNum();
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多 20 个");

        // 4. 构建查询条件（只查询当前用户的应用）
        QueryWrapper queryWrapper = getQueryWrapper(appQueryRequest);
        queryWrapper.eq("userId", loginUser.getId());

        // 5. 分页查询
        Page<App> appPage = this.page(Page.of(pageNum, pageSize), queryWrapper);

        // 6. 转换为 VO
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);

        return appVOPage;
    }

    @Override
    public Page<AppVO> listFeaturedApps(AppQueryRequest appQueryRequest) {
        // 1. 校验参数
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);

        // 2. 限制每页最多 20 个
        long pageNum = appQueryRequest.getPageNum();
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多 20 个");

        // 3. 构建查询条件（只查询精选应用，priority = 99）
        QueryWrapper queryWrapper = getQueryWrapper(appQueryRequest);
        queryWrapper.eq("priority", 99);

        // 4. 分页查询
        Page<App> appPage = this.page(Page.of(pageNum, pageSize), queryWrapper);

        // 5. 转换为 VO
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);

        return appVOPage;
    }

    @Override
    public boolean adminDeleteApp(long id) {
        // 1. 校验参数
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);

        // 2. 查询应用是否存在
        App app = this.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        // 3. 删除应用
        return this.removeById(id);
    }

    @Override
    public boolean adminUpdateApp(AppAdminUpdateRequest appAdminUpdateRequest) {
        // 1. 校验参数
        ThrowUtils.throwIf(appAdminUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = appAdminUpdateRequest.getId();
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);

        // 2. 查询应用是否存在
        App app = this.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        // 3. 更新应用
        App updateApp = new App();
        BeanUtil.copyProperties(appAdminUpdateRequest, updateApp);

        return this.updateById(updateApp);
    }

    @Override
    public Page<AppVO> adminListApps(AppQueryRequest appQueryRequest) {
        // 1. 校验参数
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);

        // 2. 不限制每页数量
        long pageNum = appQueryRequest.getPageNum();
        long pageSize = appQueryRequest.getPageSize();

        // 3. 构建查询条件
        QueryWrapper queryWrapper = getQueryWrapper(appQueryRequest);

        // 4. 分页查询
        Page<App> appPage = this.page(Page.of(pageNum, pageSize), queryWrapper);

        // 5. 转换为 VO
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);

        return appVOPage;
    }

    @Override
    public AppVO adminGetAppVO(long id) {
        return getAppVO(id);
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        return appList.stream()
                .map(this::getAppVO)
                .collect(Collectors.toList());
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
}
