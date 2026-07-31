package com.renix.codegen.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.renix.codegen.model.dto.AppAddRequest;
import com.renix.codegen.model.dto.AppAdminUpdateRequest;
import com.renix.codegen.model.dto.AppQueryRequest;
import com.renix.codegen.model.dto.AppUpdateRequest;
import com.renix.codegen.model.entity.App;
import com.renix.codegen.model.vo.AppVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 应用 服务层。
 */
public interface AppService extends IService<App> {

    /**
     * 创建应用
     *
     * @param appAddRequest 应用添加请求
     * @param request       请求对象
     * @return 新应用 id
     */
    long addApp(AppAddRequest appAddRequest, HttpServletRequest request);

    /**
     * 用户更新应用（仅支持修改应用名称）
     *
     * @param appUpdateRequest 应用更新请求
     * @param request          请求对象
     * @return 更新结果
     */
    boolean updateApp(AppUpdateRequest appUpdateRequest, HttpServletRequest request);

    /**
     * 用户删除应用
     *
     * @param id      应用 id
     * @param request 请求对象
     * @return 删除结果
     */
    boolean deleteApp(long id, HttpServletRequest request);

    /**
     * 获取应用详情
     *
     * @param id 应用 id
     * @return 应用详情
     */
    AppVO getAppVO(long id);

    /**
     * 查询我的应用列表（分页）
     *
     * @param appQueryRequest 查询请求
     * @param request         请求对象
     * @return 分页结果
     */
    Page<AppVO> listMyApps(AppQueryRequest appQueryRequest, HttpServletRequest request);

    /**
     * 查询精选应用列表（分页）
     *
     * @param appQueryRequest 查询请求
     * @return 分页结果
     */
    Page<AppVO> listFeaturedApps(AppQueryRequest appQueryRequest);

    /**
     * 管理员删除应用
     *
     * @param id 应用 id
     * @return 删除结果
     */
    boolean adminDeleteApp(long id);

    /**
     * 管理员更新应用
     *
     * @param appAdminUpdateRequest 管理员更新请求
     * @return 更新结果
     */
    boolean adminUpdateApp(AppAdminUpdateRequest appAdminUpdateRequest);

    /**
     * 管理员查询应用列表（分页）
     *
     * @param appQueryRequest 查询请求
     * @return 分页结果
     */
    Page<AppVO> adminListApps(AppQueryRequest appQueryRequest);

    /**
     * 管理员获取应用详情
     *
     * @param id 应用 id
     * @return 应用详情
     */
    AppVO adminGetAppVO(long id);

    /**
     * 将应用转换为视图对象
     *
     * @param app 应用实体
     * @return 应用视图对象
     */
    AppVO getAppVO(App app);

    /**
     * 将应用列表转换为视图对象列表
     *
     * @param appList 应用列表
     * @return 应用视图对象列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 根据查询条件构造数据查询参数
     *
     * @param appQueryRequest 查询请求
     * @return 查询条件
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);
}
