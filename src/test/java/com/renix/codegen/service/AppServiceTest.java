package com.renix.codegen.service;

import com.mybatisflex.core.paginate.Page;
import com.renix.codegen.constant.UserConstant;
import com.renix.codegen.exception.BusinessException;
import com.renix.codegen.model.dto.AppAddRequest;
import com.renix.codegen.model.dto.AppAdminUpdateRequest;
import com.renix.codegen.model.dto.AppQueryRequest;
import com.renix.codegen.model.dto.AppUpdateRequest;
import com.renix.codegen.model.entity.App;
import com.renix.codegen.model.entity.User;
import com.renix.codegen.model.enums.UserRoleEnum;
import com.renix.codegen.model.vo.AppVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 应用服务层测试
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AppServiceTest {

    @Resource
    private AppService appService;

    @Resource
    private UserService userService;

    private static Long testUserId;

    /**
     * 初始化测试用户
     */
    @BeforeAll
    static void initTestUser() {
        // 测试用户将在第一次测试中创建
    }

    /**
     * 创建测试用户（如果不存在）
     */
    private void ensureTestUser() {
        if (testUserId == null) {
            // 创建测试用户
            User testUser = new User();
            testUser.setUserAccount("testuser_app_" + System.currentTimeMillis());
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
        appAddRequest.setAppName("测试应用");
        appAddRequest.setInitPrompt("生成一个计算器应用");
        appAddRequest.setCodeGenType("html");

        // 执行测试
        long appId = appService.addApp(appAddRequest, request);

        // 验证结果
        assertTrue(appId > 0);
        App app = appService.getById(appId);
        assertNotNull(app);
        assertEquals("测试应用", app.getAppName());
        assertEquals(testUserId, app.getUserId());

        // 清理测试数据
        appService.removeById(appId);
    }

    /**
     * 测试创建应用时 initPrompt 为空
     */
    @Test
    @Order(2)
    void testAddAppWithEmptyInitPrompt() {
        MockHttpServletRequest request = createMockRequest();

        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setAppName("测试应用");
        appAddRequest.setInitPrompt("");  // 空的 initPrompt

        // 执行测试，应该抛出异常
        assertThrows(BusinessException.class, () -> {
            appService.addApp(appAddRequest, request);
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
        appAddRequest.setAppName("原始名称");
        appAddRequest.setInitPrompt("测试");
        appAddRequest.setCodeGenType("html");
        long appId = appService.addApp(appAddRequest, request);

        // 更新应用
        AppUpdateRequest updateRequest = new AppUpdateRequest();
        updateRequest.setId(appId);
        updateRequest.setAppName("新名称");

        boolean result = appService.updateApp(updateRequest, request);
        assertTrue(result);

        // 验证更新结果
        App updatedApp = appService.getById(appId);
        assertEquals("新名称", updatedApp.getAppName());

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
        appAddRequest.setAppName("待删除应用");
        appAddRequest.setInitPrompt("测试");
        appAddRequest.setCodeGenType("html");
        long appId = appService.addApp(appAddRequest, request);

        // 删除应用
        boolean result = appService.deleteApp(appId, request);
        assertTrue(result);

        // 验证删除结果
        App deletedApp = appService.getById(appId);
        assertNull(deletedApp);
    }

    /**
     * 测试获取应用详情
     */
    @Test
    @Order(5)
    void testGetAppVO() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 先创建一个应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setAppName("详情测试应用");
        appAddRequest.setInitPrompt("测试");
        appAddRequest.setCodeGenType("html");
        long appId = appService.addApp(appAddRequest, request);

        // 获取应用详情
        AppVO appVO = appService.getAppVO(appId);

        // 验证结果
        assertNotNull(appVO);
        assertEquals(appId, appVO.getId());
        assertEquals("详情测试应用", appVO.getAppName());

        // 清理测试数据
        appService.removeById(appId);
    }

    /**
     * 测试分页查询我的应用
     */
    @Test
    @Order(6)
    void testListMyApps() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 创建多个应用
        for (int i = 0; i < 3; i++) {
            AppAddRequest appAddRequest = new AppAddRequest();
            appAddRequest.setAppName("测试应用" + i);
            appAddRequest.setInitPrompt("测试");
            appAddRequest.setCodeGenType("html");
            appService.addApp(appAddRequest, request);
        }

        // 分页查询
        AppQueryRequest queryRequest = new AppQueryRequest();
        queryRequest.setPageNum(1);
        queryRequest.setPageSize(10);

        Page<AppVO> page = appService.listMyApps(queryRequest, request);

        // 验证结果
        assertNotNull(page);
        assertTrue(page.getRecords().size() >= 3);

        // 清理测试数据
        for (AppVO appVO : page.getRecords()) {
            appService.removeById(appVO.getId());
        }
    }

    /**
     * 测试分页查询精选应用
     */
    @Test
    @Order(7)
    void testListFeaturedApps() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 创建一个精选应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setAppName("精选应用");
        appAddRequest.setInitPrompt("测试");
        appAddRequest.setCodeGenType("html");
        long appId = appService.addApp(appAddRequest, request);

        // 设置为精选
        App app = appService.getById(appId);
        app.setPriority(99);
        appService.updateById(app);

        // 分页查询
        AppQueryRequest queryRequest = new AppQueryRequest();
        queryRequest.setPageNum(1);
        queryRequest.setPageSize(10);

        Page<AppVO> page = appService.listFeaturedApps(queryRequest);

        // 验证结果
        assertNotNull(page);
        assertTrue(page.getRecords().size() >= 1);

        // 清理测试数据
        appService.removeById(appId);
    }

    /**
     * 测试管理员删除应用
     */
    @Test
    @Order(8)
    void testAdminDeleteApp() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 先创建一个应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setAppName("管理员删除测试");
        appAddRequest.setInitPrompt("测试");
        appAddRequest.setCodeGenType("html");
        long appId = appService.addApp(appAddRequest, request);

        // 管理员删除应用
        boolean result = appService.adminDeleteApp(appId);
        assertTrue(result);

        // 验证删除结果
        App deletedApp = appService.getById(appId);
        assertNull(deletedApp);
    }

    /**
     * 测试管理员更新应用
     */
    @Test
    @Order(9)
    void testAdminUpdateApp() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 先创建一个应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setAppName("管理员更新测试");
        appAddRequest.setInitPrompt("测试");
        appAddRequest.setCodeGenType("html");
        long appId = appService.addApp(appAddRequest, request);

        // 管理员更新应用
        AppAdminUpdateRequest updateRequest = new AppAdminUpdateRequest();
        updateRequest.setId(appId);
        updateRequest.setAppName("管理员更新后的名称");
        updateRequest.setCover("new-cover.jpg");
        updateRequest.setPriority(99);

        boolean result = appService.adminUpdateApp(updateRequest);
        assertTrue(result);

        // 验证更新结果
        App updatedApp = appService.getById(appId);
        assertEquals("管理员更新后的名称", updatedApp.getAppName());
        assertEquals("new-cover.jpg", updatedApp.getCover());
        assertEquals(99, updatedApp.getPriority());

        // 清理测试数据
        appService.removeById(appId);
    }

    /**
     * 测试管理员分页查询应用
     */
    @Test
    @Order(10)
    void testAdminListApps() {
        // 准备测试数据
        MockHttpServletRequest request = createMockRequest();

        // 创建应用
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setAppName("管理员查询测试");
        appAddRequest.setInitPrompt("测试");
        appAddRequest.setCodeGenType("html");
        long appId = appService.addApp(appAddRequest, request);

        // 分页查询
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
     * 清理测试数据
     */
    @AfterAll
    static void cleanup() {
        // 测试用户会在事务回滚时自动清理
    }
}
