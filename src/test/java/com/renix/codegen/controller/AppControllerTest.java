package com.renix.codegen.controller;

import com.mybatisflex.core.paginate.Page;
import com.renix.codegen.common.BaseResponse;
import com.renix.codegen.common.DeleteRequest;
import com.renix.codegen.constant.UserConstant;
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
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 应用控制器层测试
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AppControllerTest {

    @Resource
    private AppController appController;

    @Resource
    private AppService appService;

    @Resource
    private UserService userService;

    private static Long testUserId;

    /**
     * 创建测试用户（如果不存在）
     */
    private void ensureTestUser() {
        if (testUserId == null) {
            // 创建测试用户
            User testUser = new User();
            testUser.setUserAccount("testuser_controller_" + System.currentTimeMillis());
            testUser.setUserPassword(userService.getEncryptPassword("12345678"));
            testUser.setUserName("测试用户");
            testUser.setUserRole(UserRoleEnum.USER.getValue());
            userService.save(testUser);
            testUserId = testUser.getId();
        }
    }

    /**
     * 创建模拟请求（带登录用户）
     */
    private MockHttpServletRequest createMockRequest() {
        ensureTestUser();
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = request.getSession();

        // 从数据库获取测试用户
        User testUser = userService.getById(testUserId);
        session.setAttribute(UserConstant.USER_LOGIN_STATE, testUser);

        return request;
    }

    /**
     * 测试创建应用
     */
    @Test
    @Order(1)
    void testAddApp() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setAppName("控制器测试应用");
        appAddRequest.setInitPrompt("生成一个计算器");
        appAddRequest.setCodeGenType("html");

        // 执行测试
        BaseResponse<Long> response = appController.addApp(appAddRequest, request);

        // 验证结果
        assertNotNull(response);
        assertEquals(0, response.getCode());
        assertTrue(response.getData() > 0);

        // 清理测试数据
        appService.removeById(response.getData());
    }

    /**
     * 测试用户更新应用
     */
    @Test
    @Order(2)
    void testUpdateApp() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 先创建一个应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setAppName("原始名称");
        appAddRequest.setInitPrompt("测试");
        appAddRequest.setCodeGenType("html");
        BaseResponse<Long> addResponse = appController.addApp(appAddRequest, request);
        long appId = addResponse.getData();

        // 更新应用
        AppUpdateRequest updateRequest = new AppUpdateRequest();
        updateRequest.setId(appId);
        updateRequest.setAppName("新名称");

        BaseResponse<Boolean> response = appController.updateApp(updateRequest, request);

        // 验证结果
        assertNotNull(response);
        assertEquals(0, response.getCode());
        assertTrue(response.getData());

        // 清理测试数据
        appService.removeById(appId);
    }

    /**
     * 测试用户删除应用
     */
    @Test
    @Order(3)
    void testDeleteApp() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 先创建一个应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setAppName("待删除应用");
        appAddRequest.setInitPrompt("测试");
        appAddRequest.setCodeGenType("html");
        BaseResponse<Long> addResponse = appController.addApp(appAddRequest, request);
        long appId = addResponse.getData();

        // 删除应用
        DeleteRequest deleteRequest = new DeleteRequest();
        deleteRequest.setId(appId);

        BaseResponse<Boolean> response = appController.deleteApp(deleteRequest, request);

        // 验证结果
        assertNotNull(response);
        assertEquals(0, response.getCode());
        assertTrue(response.getData());
    }

    /**
     * 测试获取应用详情
     */
    @Test
    @Order(4)
    void testGetAppVO() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 先创建一个应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setAppName("详情测试应用");
        appAddRequest.setInitPrompt("测试");
        appAddRequest.setCodeGenType("html");
        BaseResponse<Long> addResponse = appController.addApp(appAddRequest, request);
        long appId = addResponse.getData();

        // 获取应用详情
        BaseResponse<AppVO> response = appController.getAppVO(appId);

        // 验证结果
        assertNotNull(response);
        assertEquals(0, response.getCode());
        assertNotNull(response.getData());
        assertEquals(appId, response.getData().getId());

        // 清理测试数据
        appService.removeById(appId);
    }

    /**
     * 测试查询我的应用列表
     */
    @Test
    @Order(5)
    void testListMyApps() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 创建应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setAppName("列表测试应用");
        appAddRequest.setInitPrompt("测试");
        appAddRequest.setCodeGenType("html");
        BaseResponse<Long> addResponse = appController.addApp(appAddRequest, request);
        long appId = addResponse.getData();

        // 查询应用列表
        AppQueryRequest queryRequest = new AppQueryRequest();
        queryRequest.setPageNum(1);
        queryRequest.setPageSize(10);

        BaseResponse<Page<AppVO>> response = appController.listMyApps(queryRequest, request);

        // 验证结果
        assertNotNull(response);
        assertEquals(0, response.getCode());
        assertNotNull(response.getData());
        assertTrue(response.getData().getRecords().size() >= 1);

        // 清理测试数据
        appService.removeById(appId);
    }

    /**
     * 测试查询精选应用列表
     */
    @Test
    @Order(6)
    void testListFeaturedApps() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 创建应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setAppName("精选测试应用");
        appAddRequest.setInitPrompt("测试");
        appAddRequest.setCodeGenType("html");
        BaseResponse<Long> addResponse = appController.addApp(appAddRequest, request);
        long appId = addResponse.getData();

        // 设置为精选
        App app = appService.getById(appId);
        app.setPriority(99);
        appService.updateById(app);

        // 查询精选应用列表
        AppQueryRequest queryRequest = new AppQueryRequest();
        queryRequest.setPageNum(1);
        queryRequest.setPageSize(10);

        BaseResponse<Page<AppVO>> response = appController.listFeaturedApps(queryRequest);

        // 验证结果
        assertNotNull(response);
        assertEquals(0, response.getCode());
        assertNotNull(response.getData());
        assertTrue(response.getData().getRecords().size() >= 1);

        // 清理测试数据
        appService.removeById(appId);
    }

    /**
     * 测试管理员删除应用（通过 Service 层测试）
     */
    @Test
    @Order(7)
    void testAdminDeleteApp() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 先创建一个应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setAppName("管理员删除测试");
        appAddRequest.setInitPrompt("测试");
        appAddRequest.setCodeGenType("html");
        long appId = appService.addApp(appAddRequest, request);

        // 管理员删除应用（通过 Service 层）
        boolean result = appService.adminDeleteApp(appId);

        // 验证结果
        assertTrue(result);
    }

    /**
     * 测试管理员更新应用（通过 Service 层测试）
     */
    @Test
    @Order(8)
    void testAdminUpdateApp() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 先创建一个应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setAppName("管理员更新测试");
        appAddRequest.setInitPrompt("测试");
        appAddRequest.setCodeGenType("html");
        long appId = appService.addApp(appAddRequest, request);

        // 管理员更新应用（通过 Service 层）
        AppAdminUpdateRequest updateRequest = new AppAdminUpdateRequest();
        updateRequest.setId(appId);
        updateRequest.setAppName("管理员更新后的名称");
        updateRequest.setCover("new-cover.jpg");
        updateRequest.setPriority(99);

        boolean result = appService.adminUpdateApp(updateRequest);

        // 验证结果
        assertTrue(result);

        // 清理测试数据
        appService.removeById(appId);
    }

    /**
     * 测试管理员分页查询应用（通过 Service 层测试）
     */
    @Test
    @Order(9)
    void testAdminListApps() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 创建应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setAppName("管理员查询测试");
        appAddRequest.setInitPrompt("测试");
        appAddRequest.setCodeGenType("html");
        long appId = appService.addApp(appAddRequest, request);

        // 分页查询（通过 Service 层）
        AppQueryRequest queryRequest = new AppQueryRequest();
        queryRequest.setPageNum(1);
        queryRequest.setPageSize(10);
        queryRequest.setAppName("管理员查询测试");

        Page<AppVO> page = appService.adminListApps(queryRequest);

        // 验证结果
        assertNotNull(page);
        assertTrue(page.getRecords().size() >= 1);

        // 清理测试数据
        appService.removeById(appId);
    }

    /**
     * 测试管理员获取应用详情（通过 Service 层测试）
     */
    @Test
    @Order(10)
    void testAdminGetAppVO() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 先创建一个应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setAppName("管理员详情测试");
        appAddRequest.setInitPrompt("测试");
        appAddRequest.setCodeGenType("html");
        long appId = appService.addApp(appAddRequest, request);

        // 管理员获取应用详情（通过 Service 层）
        AppVO appVO = appService.adminGetAppVO(appId);

        // 验证结果
        assertNotNull(appVO);
        assertEquals(appId, appVO.getId());

        // 清理测试数据
        appService.removeById(appId);
    }
}
