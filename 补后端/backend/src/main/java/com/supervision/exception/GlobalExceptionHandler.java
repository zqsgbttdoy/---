package com.supervision.exception;

import com.supervision.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e, HttpServletRequest request,
                                                     jakarta.servlet.http.HttpServletResponse response) {
        response.setStatus(e.getCode() >= 400 && e.getCode() <= 599 ? e.getCode() : 400);
        log.warn("业务异常: {} - {}", request.getRequestURI(), e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({org.springframework.orm.ObjectOptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleVersionConflict(Exception e) {
        return ApiResponse.error(409, "记录已被其他端修改，请保留草稿并重新打开核对");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ApiResponse.error(400, message);
    }

    /**
     * 数据完整性违规异常（唯一约束冲突、外键引用等）。
     * 响应中不暴露 SQL 细节，仅返回友好提示；细节仅记录到日志。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleDataIntegrityViolationException(DataIntegrityViolationException e, HttpServletRequest request) {
        log.warn("数据完整性异常: {} - {}", request.getRequestURI(), e.getMessage());
        return ApiResponse.error(400, "操作失败：数据已被引用或违反唯一约束");
    }

    /**
     * 数据库访问异常（连接失败、SQL 语法错误等 DataIntegrityViolationException 之外的数据层异常）。
     * 记录完整堆栈，响应不含 SQL 信息。
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleDataAccessException(DataAccessException e, HttpServletRequest request) {
        log.error("数据库访问异常: {} - {}", request.getRequestURI(), e.getMessage(), e);
        return ApiResponse.error(500, "数据库访问异常，请稍后重试");
    }

    /**
     * 请求体解析失败（JSON 格式错误、类型不匹配等）。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("请求体格式错误: {} - {}", request.getRequestURI(), e.getMessage());
        return ApiResponse.error(400, "请求体格式错误");
    }

    /**
     * 请求参数类型不匹配（如路径变量/查询参数无法转换为目标类型）。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        log.warn("参数类型错误: {} - 参数[{}]: {}", request.getRequestURI(), e.getName(), e.getMessage());
        return ApiResponse.error(400, "参数类型错误: " + e.getName());
    }

    /**
     * 缺少必填的请求参数。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e, HttpServletRequest request) {
        log.warn("缺少必填参数: {} - 参数[{}]", request.getRequestURI(), e.getParameterName());
        return ApiResponse.error(400, "缺少必填参数: " + e.getParameterName());
    }

    /**
     * 上传文件大小超出配置限制。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("上传文件大小超出限制: {} - {}", request.getRequestURI(), e.getMessage());
        return ApiResponse.error(400, "上传文件大小超出限制");
    }

    /**
     * 请求的静态资源或接口路径不存在（Spring 6.1+ 静态资源 404）。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoResourceFoundException(NoResourceFoundException e, HttpServletRequest request) {
        log.warn("资源不存在: {} - {}", request.getRequestURI(), e.getResourcePath());
        return ApiResponse.error(404, "资源不存在");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: {} - {}", request.getRequestURI(), e.getMessage(), e);
        return ApiResponse.error("系统内部错误，请联系管理员");
    }
}
