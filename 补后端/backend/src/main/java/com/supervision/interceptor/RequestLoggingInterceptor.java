package com.supervision.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 请求日志拦截器。
 * <p>
 * preHandle 阶段生成短 traceId（UUID 前 8 位）写入 MDC，供日志 pattern 中的 %X{traceId} 输出，
 * 实现单次请求内日志的链路串联；同时记录请求起始时间。
 * afterCompletion 阶段记录请求方法、URI、响应状态码与耗时（超过 1000ms 以 WARN 级别告警），
 * 并在 finally 中清理 MDC，防止线程池复用导致 traceId 串号。
 */
@Slf4j
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    /** MDC 中链路追踪 ID 的键名，与 logback-spring.xml 的 %X{traceId} 对应 */
    private static final String TRACE_ID = "traceId";

    /** request attribute：请求起始时间（毫秒） */
    private static final String START_TIME_ATTR = "REQUEST_LOG_START_TIME";

    /** 慢请求阈值（毫秒），超过则以 WARN 级别记录 */
    private static final long SLOW_REQUEST_THRESHOLD_MS = 1000L;

    /**
     * 请求进入：生成 traceId 写入 MDC，记录起始时间与请求概要（DEBUG 级）。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(TRACE_ID, traceId);
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        log.debug("请求开始: {} {}", request.getMethod(), request.getRequestURI());
        return true;
    }

    /**
     * 请求完成：计算耗时并记录 method/URI/status/耗时，慢请求以 WARN 记录；最后清理 MDC。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            Object startTime = request.getAttribute(START_TIME_ATTR);
            long costMs = startTime instanceof Long ? System.currentTimeMillis() - (Long) startTime : -1;
            if (costMs > SLOW_REQUEST_THRESHOLD_MS) {
                log.warn("慢请求: {} {} status={} 耗时={}ms",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), costMs);
            } else {
                log.info("请求完成: {} {} status={} 耗时={}ms",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), costMs);
            }
        } finally {
            MDC.remove(TRACE_ID);
        }
    }
}
