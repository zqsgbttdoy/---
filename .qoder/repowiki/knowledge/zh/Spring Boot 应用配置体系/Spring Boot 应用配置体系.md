---
kind: configuration_system
name: Spring Boot 应用配置体系
category: configuration_system
scope:
    - '**'
source_files:
    - backend/src/main/resources/application.yml
    - backend/src/main/java/com/supervision/config/CorsConfig.java
    - backend/src/main/java/com/supervision/config/PasswordConfig.java
    - backend/src/main/java/com/supervision/config/WebMvcConfig.java
    - backend/src/main/resources/schema.sql
---

该仓库为基于 Spring Boot 3.2 + JPA + MySQL 的监理现场管理系统后端，其配置系统采用 Spring Boot 标准约定：

1. **配置文件位置与格式**：核心应用配置位于 `backend/src/main/resources/application.yml`，使用 YAML 格式集中管理数据源、JPA、服务器端口等运行时参数。项目根目录还包含 `DEPLOYMENT.md` 部署文档，可能包含环境相关的配置说明。

2. **Java 配置类**：在 `com.supervision.config` 包下通过 `@Configuration` 类进行程序化配置，当前可见的配置类包括：
   - `CorsConfig`：跨域请求配置
   - `PasswordConfig`：密码加密/校验相关配置
   - `WebMvcConfig`：Web MVC 拦截器、视图解析器等 Web 层配置

3. **数据库配置**：通过 `application.yml` 中的 `spring.datasource.*` 和 `spring.jpa.*` 属性配置 MySQL 连接及 JPA 行为；`schema.sql` 用于初始化数据库表结构。

4. **启动入口**：`SupervisionApplication.java` 作为 Spring Boot 主启动类，默认加载 `application.yml` 并扫描 `com.supervision` 包下的组件。

5. **构建期配置**：`pom.xml` 中定义 Spring Boot 版本、依赖及打包方式，属于构建期而非运行期配置。

**开发者应遵循的规则**：
- 所有可变的运行时配置（数据库连接、第三方服务地址、开关等）统一放入 `application.yml`，禁止硬编码
- 环境差异通过 Spring Profile（如 `application-dev.yml`、`application-prod.yml`）或外部环境变量覆盖实现
- 敏感信息（密码、密钥）不应写入配置文件，应通过环境变量或外部配置中心注入
- 新增全局配置时应在 `config` 包下创建对应的 `@Configuration` 类，保持与现有 `CorsConfig`、`WebMvcConfig` 风格一致