package com.supervision;

import com.supervision.common.ApiResponse;
import com.supervision.common.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PageResult 和 ApiResponse 单元测试")
class PageResultTest {

    @Test
    @DisplayName("PageResult构造函数 - 正常参数")
    void testPageResultConstructor() {
        List<String> data = Arrays.asList("a", "b", "c");
        PageResult<String> result = new PageResult<>(data, 100L, 2, 10);

        assertEquals(3, result.getList().size());
        assertEquals(100L, result.getTotal());
        assertEquals(2, result.getPageNum());
        assertEquals(10, result.getPageSize());
    }

    @Test
    @DisplayName("PageResult构造函数 - 空列表")
    void testPageResultWithEmptyList() {
        PageResult<String> result = new PageResult<>(Collections.emptyList(), 0L, 1, 10);

        assertTrue(result.getList().isEmpty());
        assertEquals(0L, result.getTotal());
    }

    @Test
    @DisplayName("PageResult构造函数 - total为0")
    void testPageResultWithZeroTotal() {
        List<Integer> data = Collections.emptyList();
        PageResult<Integer> result = new PageResult<>(data, 0L, 1, 20);

        assertEquals(0L, result.getTotal());
        assertEquals(1, result.getPageNum());
        assertEquals(20, result.getPageSize());
    }

    @Test
    @DisplayName("PageResult构造函数 - 单条数据")
    void testPageResultWithSingleItem() {
        List<String> data = Collections.singletonList("only");
        PageResult<String> result = new PageResult<>(data, 1L, 1, 10);

        assertEquals(1, result.getList().size());
        assertEquals("only", result.getList().get(0));
        assertEquals(1L, result.getTotal());
    }

    @Test
    @DisplayName("PageResult - Lombok Getter/Setter")
    void testPageResultSetters() {
        PageResult<String> result = new PageResult<>(Collections.emptyList(), 0L, 1, 10);
        result.setTotal(50L);
        result.setPageNum(3);
        result.setPageSize(25);

        assertEquals(50L, result.getTotal());
        assertEquals(3, result.getPageNum());
        assertEquals(25, result.getPageSize());
    }

    @Test
    @DisplayName("ApiResponse.success - 带data参数")
    void testApiResponseSuccess() {
        ApiResponse<String> response = ApiResponse.success("testData");

        assertEquals(200, response.getCode());
        assertEquals("操作成功", response.getMessage());
        assertEquals("testData", response.getData());
    }

    @Test
    @DisplayName("ApiResponse.success - 带message和data参数")
    void testApiResponseSuccessWithMessage() {
        ApiResponse<Integer> response = ApiResponse.success("创建成功", 42);

        assertEquals(200, response.getCode());
        assertEquals("创建成功", response.getMessage());
        assertEquals(42, response.getData());
    }

    @Test
    @DisplayName("ApiResponse.error - 仅message参数")
    void testApiResponseError() {
        ApiResponse<Void> response = ApiResponse.error("系统错误");

        assertEquals(500, response.getCode());
        assertEquals("系统错误", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    @DisplayName("ApiResponse.error - 带code和message参数")
    void testApiResponseErrorWithCode() {
        ApiResponse<Void> response = ApiResponse.error(404, "资源不存在");

        assertEquals(404, response.getCode());
        assertEquals("资源不存在", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    @DisplayName("ApiResponse.success - data为null")
    void testApiResponseSuccessWithNullData() {
        ApiResponse<String> response = ApiResponse.success(null);

        assertEquals(200, response.getCode());
        assertEquals("操作成功", response.getMessage());
        assertNull(response.getData());
    }
}
