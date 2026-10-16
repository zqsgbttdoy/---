package com.supervision;

import com.supervision.common.ApiResponse;
import com.supervision.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GlobalExceptionHandler 新增 handler 的单元测试。
 * 直接实例化 handler 调用方法，断言 ApiResponse 的 code 与 message，
 * 并验证响应消息中不包含 SQL/堆栈等敏感细节。
 */
@DisplayName("GlobalExceptionHandler 扩展 handler 单元测试")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(uri);
        return req;
    }

    @Test
    @DisplayName("DataIntegrityViolationException -> 400 且不暴露 SQL 细节")
    void testDataIntegrityViolation() {
        DataIntegrityViolationException e =
                new DataIntegrityViolationException("could not execute statement [Duplicate entry '13800000000' for key 'uk_phone']");
        ApiResponse<Void> resp = handler.handleDataIntegrityViolationException(e, request("/api/users"));

        assertEquals(400, resp.getCode());
        assertEquals("操作失败：数据已被引用或违反唯一约束", resp.getMessage());
        assertNull(resp.getData());
        assertFalse(resp.getMessage().contains("Duplicate entry"), "响应不应暴露 SQL 细节");
    }

    @Test
    @DisplayName("DataAccessException -> 500 数据库访问异常")
    void testDataAccessException() {
        QueryTimeoutException e = new QueryTimeoutException("query timeout after 30s");
        ApiResponse<Void> resp = handler.handleDataAccessException(e, request("/api/projects"));

        assertEquals(500, resp.getCode());
        assertEquals("数据库访问异常，请稍后重试", resp.getMessage());
        assertNull(resp.getData());
        assertFalse(resp.getMessage().contains("timeout"), "响应不应暴露底层细节");
    }

    @Test
    @DisplayName("HttpMessageNotReadableException -> 400 请求体格式错误")
    void testHttpMessageNotReadable() {
        HttpMessageNotReadableException e = new HttpMessageNotReadableException(
                "JSON parse error", new MockHttpInputMessage(new byte[0]));
        ApiResponse<Void> resp = handler.handleHttpMessageNotReadableException(e, request("/api/users"));

        assertEquals(400, resp.getCode());
        assertEquals("请求体格式错误", resp.getMessage());
        assertNull(resp.getData());
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException -> 400 参数类型错误: {参数名}")
    void testMethodArgumentTypeMismatch() {
        MethodArgumentTypeMismatchException e = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "projectId", null, new NumberFormatException("For input string: \"abc\""));
        ApiResponse<Void> resp = handler.handleMethodArgumentTypeMismatchException(e, request("/api/projects/abc"));

        assertEquals(400, resp.getCode());
        assertEquals("参数类型错误: projectId", resp.getMessage());
        assertNull(resp.getData());
    }

    @Test
    @DisplayName("MissingServletRequestParameterException -> 400 缺少必填参数: {参数名}")
    void testMissingServletRequestParameter() {
        MissingServletRequestParameterException e =
                new MissingServletRequestParameterException("userId", "Long");
        ApiResponse<Void> resp = handler.handleMissingServletRequestParameterException(e, request("/api/roles"));

        assertEquals(400, resp.getCode());
        assertEquals("缺少必填参数: userId", resp.getMessage());
        assertNull(resp.getData());
    }

    @Test
    @DisplayName("MaxUploadSizeExceededException -> 400 上传文件大小超出限制")
    void testMaxUploadSizeExceeded() {
        MaxUploadSizeExceededException e = new MaxUploadSizeExceededException(10 * 1024 * 1024L);
        ApiResponse<Void> resp = handler.handleMaxUploadSizeExceededException(e, request("/api/files/upload"));

        assertEquals(400, resp.getCode());
        assertEquals("上传文件大小超出限制", resp.getMessage());
        assertNull(resp.getData());
    }

    @Test
    @DisplayName("NoResourceFoundException -> 404 资源不存在")
    void testNoResourceFound() {
        NoResourceFoundException e = new NoResourceFoundException(HttpMethod.GET, "/api/not-exist");
        ApiResponse<Void> resp = handler.handleNoResourceFoundException(e, request("/api/not-exist"));

        assertEquals(404, resp.getCode());
        assertEquals("资源不存在", resp.getMessage());
        assertNull(resp.getData());
    }
}
