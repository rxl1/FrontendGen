package com.renix.codegen.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.renix.codegen.model.dto.app.AppQueryRequest;
import com.renix.codegen.model.entity.App;
import com.renix.codegen.model.entity.User;
import com.renix.codegen.model.vo.AppVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 */
public interface AppService extends IService<App> {


    /**
     * 获取应用封装类
     *
     * @param app
     * @return
     */
    AppVO getAppVO(App app);

    /**
     * 获取应用封装类列表
     *
     * @param appList
     * @return
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 构造应用查询条件
     *
     * @param appQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 通过聊天对话生成应用代码
     * @param appId 应用id
     * @param message 提示词
     * @param loginUser 登录用户
     * @return 代码流
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);
}
