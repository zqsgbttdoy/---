---
kind: logging_system
name: 基于 Spring Boot 默认日志框架的轻量级日志系统
category: logging_system
scope:
    - '**'
source_files:
    - 补后端/backend/src/main/resources/application.yml
    - 补后端/backend/src/main/java/com/supervision/exception/GlobalExceptionHandler.java
    - 补后端/backend/pom.xml
---

## 日志系统概述

该项目采用 Spring Boot 3.2.5 默认的日志框架（SLF4J + Logback），实现了最基础的日志记录功能，但缺乏完整的结构化日志体系。

## 技术栈与依赖
- **日志门面**: SLF4J API（通过 Lombok 的 @Slf4j 注解使用）
- **日志实现**: Logback（Spring Boot 默认集成）
- **配置方式**: application.yml 中的 logging.level 配置
- **注解支持**: Lombok 的 @Slf4j 注解生成 logger 实例

## 核心配置
在 `application.yml` 中仅包含基础日志级别配置：
- `com.supervision: debug` - 应用包日志级别设为 debug
- `org.hibernate.SQL: debug` - SQL 语句调试输出
- 未配置自定义 logback.xml 配置文件
- 未设置日志文件输出路径、格式、轮转策略等

## 使用模式
目前仅在异常处理层使用了日志记录：
- `GlobalExceptionHandler.java` 中使用 `@Slf4j` 注解
- 业务异常使用 `log.warn()` 记录
- 系统异常使用 `log.error()` 记录并附带完整堆栈
- 其他业务代码中未发现日志记录调用

## 架构特点
1. **极简设计**: 没有专门的日志工具类或日志切面
2. **集中异常日志**: 所有异常统一在 GlobalExceptionHandler 中记录
3. **无结构化字段**: 日志消息为简单字符串拼接，缺少结构化字段
4. **无日志分级**: 未按模块或业务域划分日志级别
5. **无异步处理**: 同步写入控制台日志

## 存在的问题
- 缺少业务操作日志记录
- 无请求/响应日志拦截器
- 未配置日志文件输出和轮转
- 缺少关键业务上下文信息（用户ID、请求ID等）
- 未遵循结构化日志最佳实践

## 改进建议
1. 引入 MDC 实现请求链路追踪
2. 添加 AOP 切面记录关键业务操作日志
3. 配置日志文件输出和按天轮转
4. 建立统一的日志格式规范
5. 实现敏感信息脱敏处理