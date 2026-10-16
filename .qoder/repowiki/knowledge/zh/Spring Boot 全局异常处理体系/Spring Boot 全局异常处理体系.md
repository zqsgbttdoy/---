---
kind: error_handling
name: Spring Boot 全局异常处理体系
category: error_handling
scope:
    - '**'
source_files:
    - 补后端/backend/src/main/java/com/supervision/exception/BusinessException.java
    - 补后端/backend/src/main/java/com/supervision/exception/GlobalExceptionHandler.java
    - 补后端/backend/src/main/java/com/supervision/common/ApiResponse.java
---

## 错误处理架构概述

该项目采用 Spring Boot 标准的异常处理模式，通过自定义业务异常类、全局异常处理器和统一响应格式实现完整的错误处理机制。

## 核心组件

### 1. 业务异常定义
- **BusinessException**: 自定义运行时异常，继承自 `RuntimeException`
- 支持两种构造方式：默认400状态码或自定义错误码
- 提供 `getCode()` 方法获取具体错误码

### 2. 全局异常处理器
- **GlobalExceptionHandler**: 使用 `@RestControllerAdvice` 注解的全局异常拦截器
- 处理三类主要异常：
  - `BusinessException`: 业务逻辑异常，返回400状态码
  - `MethodArgumentNotValidException`: 参数验证异常，自动提取字段错误信息
  - `Exception`: 未捕获的系统异常，返回500状态码

### 3. 统一响应格式
- **ApiResponse**: 泛型响应包装类，包含 code、message、data 三个字段
- 提供静态工厂方法：`success()`、`error()` 等
- 所有API接口统一返回此格式

## 错误处理流程

1. **Controller层**: 使用 `@Valid` 注解进行参数验证
2. **Service层**: 业务逻辑异常通过 `throw new BusinessException()` 抛出
3. **全局拦截**: `GlobalExceptionHandler` 捕获并转换异常为统一响应
4. **日志记录**: 业务异常使用 `log.warn()`，系统异常使用 `log.error()`

## 使用模式

- 业务校验失败：`throw new BusinessException("错误消息")`
- 认证授权失败：`throw new BusinessException(401, "未登录")`
- 参数验证：在DTO字段上使用JSR-303注解，配合 `@Valid` 使用
- 数据库异常：当前未专门处理，会被通用异常处理器捕获

## 设计特点

- 简洁明了的错误码体系（400业务错误、401未授权、500系统错误）
- 统一的错误响应格式便于前端处理
- 分层清晰的异常传播机制
- 完善的日志记录便于问题排查