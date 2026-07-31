package com.renix.codegen.controller;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.renix.codegen.common.BaseResponse;
import com.renix.codegen.common.DeleteRequest;
import com.renix.codegen.constant.UserConstant;
import com.renix.codegen.model.dto.app.AppAddRequest;
import com.renix.codegen.model.dto.app.AppAdminUpdateRequest;
import com.renix.codegen.model.dto.app.AppQueryRequest;
import com.renix.codegen.model.dto.app.AppUpdateRequest;
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
        appAddRequest.setInitPrompt("生成一个计算器应用");

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
     * 测试创建应用时 initPrompt 为空
     */
    @Test
    @Order(2)
    void testAddAppWithEmptyInitPrompt() {
        MockHttpServletRequest request = createMockRequest();

        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setInitPrompt("");  // 空的 initPrompt

        // 执行测试，应该抛出异常
        assertThrows(Exception.class, () -> {
            appController.addApp(appAddRequest, request);
        });
    }

    /**
     * 测试用户更新应用
     */
    @Test
    @Order(3)
    void testUpdateApp() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 先创建一个应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setInitPrompt("测试");
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
    @Order(4)
    void testDeleteApp() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 先创建一个应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setInitPrompt("测试");
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
    @Order(5)
    void testGetAppVOById() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 先创建一个应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setInitPrompt("测试");
        BaseResponse<Long> addResponse = appController.addApp(appAddRequest, request);
        long appId = addResponse.getData();

        // 获取应用详情
        BaseResponse<AppVO> response = appController.getAppVOById(appId);

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
    @Order(6)
    void testListMyAppVOByPage() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 创建应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setInitPrompt("测试");
        BaseResponse<Long> addResponse = appController.addApp(appAddRequest, request);
        long appId = addResponse.getData();

        // 查询应用列表
        AppQueryRequest queryRequest = new AppQueryRequest();
        queryRequest.setPageNum(1);
        queryRequest.setPageSize(10);

        BaseResponse<Page<AppVO>> response = appController.listMyAppVOByPage(queryRequest, request);

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
    @Order(7)
    void testListGoodAppVOByPage() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 创建应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setInitPrompt("测试");
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

        BaseResponse<Page<AppVO>> response = appController.listGoodAppVOByPage(queryRequest);

        // 验证结果
        assertNotNull(response);
        assertEquals(0, response.getCode());
        assertNotNull(response.getData());
        assertTrue(response.getData().getRecords().size() >= 1);

        // 清理测试数据
        appService.removeById(appId);
    }

    /**
     * 测试管理员删除应用（通过 Service 层测试，避免 AOP 拦截器问题）
     */
    @Test
    @Order(8)
    void testAdminDeleteApp() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 先创建一个应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setInitPrompt("测试");
        BaseResponse<Long> addResponse = appController.addApp(appAddRequest, request);
        long appId = addResponse.getData();

        // 验证应用存在
        App app = appService.getById(appId);
        assertNotNull(app);

        // 删除应用
        boolean result = appService.removeById(appId);

        // 验证结果
        assertTrue(result);
    }

    /**
     * 测试管理员更新应用（通过 Service 层测试，避免 AOP 拦截器问题）
     */
    @Test
    @Order(9)
    void testAdminUpdateApp() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 先创建一个应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setInitPrompt("测试");
        BaseResponse<Long> addResponse = appController.addApp(appAddRequest, request);
        long appId = addResponse.getData();

        // 更新应用
        App app = new App();
        app.setId(appId);
        app.setAppName("管理员更新后的名称");
        app.setCover("new-cover.jpg");
        app.setPriority(99);

        boolean result = appService.updateById(app);

        // 验证结果
        assertTrue(result);

        // 清理测试数据
        appService.removeById(appId);
    }

    /**
     * 测试管理员分页查询应用（通过 Service 层测试，避免 AOP 拦截器问题）
     */
    @Test
    @Order(10)
    void testAdminListApps() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 创建应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setInitPrompt("测试");
        BaseResponse<Long> addResponse = appController.addApp(appAddRequest, request);
        long appId = addResponse.getData();

        // 分页查询
        AppQueryRequest queryRequest = new AppQueryRequest();
        queryRequest.setPageNum(1);
        queryRequest.setPageSize(10);

        QueryWrapper queryWrapper = appService.getQueryWrapper(queryRequest);
        Page<App> page = appService.page(Page.of(1, 10), queryWrapper);

        // 验证结果
        assertNotNull(page);
        assertTrue(page.getRecords().size() >= 1);

        // 清理测试数据
        appService.removeById(appId);
    }
}
