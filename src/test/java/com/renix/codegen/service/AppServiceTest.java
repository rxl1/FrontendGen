package com.renix.codegen.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.renix.codegen.model.dto.app.AppQueryRequest;
import com.renix.codegen.model.entity.App;
import com.renix.codegen.model.entity.User;
import com.renix.codegen.model.enums.UserRoleEnum;
import com.renix.codegen.model.vo.AppVO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

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
     * 测试获取应用封装类
     */
    @Test
    @Order(1)
    void testGetAppVO() {
        // 准备测试数据
        ensureTestUser();

        // 创建应用
        App app = new App();
        app.setAppName("测试应用");
        app.setInitPrompt("测试");
        app.setCodeGenType("html");
        app.setUserId(testUserId);
        app.setPriority(0);
        appService.save(app);

        // 测试获取应用封装类
        AppVO appVO = appService.getAppVO(app);

        // 验证结果
        assertNotNull(appVO);
        assertEquals(app.getId(), appVO.getId());
        assertEquals("测试应用", appVO.getAppName());
        assertNotNull(appVO.getUser()); // 应该包含用户信息

        // 清理测试数据
        appService.removeById(app.getId());
    }

    /**
     * 测试获取应用封装类（null 输入）
     */
    @Test
    @Order(2)
    void testGetAppVONull() {
        AppVO appVO = appService.getAppVO(null);
        assertNull(appVO);
    }

    /**
     * 测试获取应用封装类列表
     */
    @Test
    @Order(3)
    void testGetAppVOList() {
        // 准备测试数据
        ensureTestUser();

        // 创建多个应用
        List<App> appList = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            App app = new App();
            app.setAppName("测试应用" + i);
            app.setInitPrompt("测试" + i);
            app.setCodeGenType("html");
            app.setUserId(testUserId);
            app.setPriority(0);
            appService.save(app);
            appList.add(app);
        }

        // 测试获取应用封装类列表
        List<AppVO> appVOList = appService.getAppVOList(appList);

        // 验证结果
        assertNotNull(appVOList);
        assertEquals(3, appVOList.size());
        for (AppVO appVO : appVOList) {
            assertNotNull(appVO.getUser()); // 应该包含用户信息
        }

        // 清理测试数据
        for (App app : appList) {
            appService.removeById(app.getId());
        }
    }

    /**
     * 测试获取应用封装类列表（空列表）
     */
    @Test
    @Order(4)
    void testGetAppVOListEmpty() {
        List<AppVO> appVOList = appService.getAppVOList(new ArrayList<>());
        assertNotNull(appVOList);
        assertTrue(appVOList.isEmpty());
    }

    /**
     * 测试构造查询条件
     */
    @Test
    @Order(5)
    void testGetQueryWrapper() {
        // 准备测试数据
        AppQueryRequest queryRequest = new AppQueryRequest();
        queryRequest.setId(1L);
        queryRequest.setAppName("测试");
        queryRequest.setUserId(1L);
        queryRequest.setPriority(99);

        // 测试构造查询条件
        QueryWrapper queryWrapper = appService.getQueryWrapper(queryRequest);

        // 验证结果
        assertNotNull(queryWrapper);
    }

    /**
     * 测试构造查询条件（null 输入）
     */
    @Test
    @Order(6)
    void testGetQueryWrapperNull() {
        assertThrows(Exception.class, () -> {
            appService.getQueryWrapper(null);
        });
    }

    /**
     * 测试分页查询
     */
    @Test
    @Order(7)
    void testPageQuery() {
        // 准备测试数据
        ensureTestUser();

        // 创建应用
        App app = new App();
        app.setAppName("分页测试应用");
        app.setInitPrompt("测试");
        app.setCodeGenType("html");
        app.setUserId(testUserId);
        app.setPriority(0);
        appService.save(app);

        // 分页查询
        AppQueryRequest queryRequest = new AppQueryRequest();
        queryRequest.setPageNum(1);
        queryRequest.setPageSize(10);
        queryRequest.setUserId(testUserId);

        QueryWrapper queryWrapper = appService.getQueryWrapper(queryRequest);
        Page<App> page = appService.page(Page.of(1, 10), queryWrapper);

        // 验证结果
        assertNotNull(page);
        assertTrue(page.getRecords().size() >= 1);

        // 清理测试数据
        appService.removeById(app.getId());
    }

    /**
     * 测试根据 id 查询应用
     */
    @Test
    @Order(8)
    void testGetById() {
        // 准备测试数据
        ensureTestUser();

        // 创建应用
        App app = new App();
        app.setAppName("查询测试应用");
        app.setInitPrompt("测试");
        app.setCodeGenType("html");
        app.setUserId(testUserId);
        app.setPriority(0);
        appService.save(app);

        // 根据 id 查询应用
        App queriedApp = appService.getById(app.getId());

        // 验证结果
        assertNotNull(queriedApp);
        assertEquals(app.getId(), queriedApp.getId());
        assertEquals("查询测试应用", queriedApp.getAppName());

        // 清理测试数据
        appService.removeById(app.getId());
    }

    /**
     * 测试保存应用
     */
    @Test
    @Order(9)
    void testSave() {
        // 准备测试数据
        ensureTestUser();

        // 创建应用
        App app = new App();
        app.setAppName("保存测试应用");
        app.setInitPrompt("测试");
        app.setCodeGenType("html");
        app.setUserId(testUserId);
        app.setPriority(0);

        // 保存应用
        boolean result = appService.save(app);

        // 验证结果
        assertTrue(result);
        assertTrue(app.getId() > 0);

        // 清理测试数据
        appService.removeById(app.getId());
    }

    /**
     * 测试删除应用
     */
    @Test
    @Order(10)
    void testRemoveById() {
        // 准备测试数据
        ensureTestUser();

        // 创建应用
        App app = new App();
        app.setAppName("删除测试应用");
        app.setInitPrompt("测试");
        app.setCodeGenType("html");
        app.setUserId(testUserId);
        app.setPriority(0);
        appService.save(app);

        // 删除应用
        boolean result = appService.removeById(app.getId());

        // 验证结果
        assertTrue(result);
        App deletedApp = appService.getById(app.getId());
        assertNull(deletedApp);
    }
}
