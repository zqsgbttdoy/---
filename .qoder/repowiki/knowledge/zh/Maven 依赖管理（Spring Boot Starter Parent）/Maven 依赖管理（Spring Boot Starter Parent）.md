---
kind: dependency_management
name: Maven 依赖管理（Spring Boot Starter Parent）
category: dependency_management
scope:
    - '**'
source_files:
    - 补后端/backend/pom.xml
    - 补后端/backend/.mvn/maven/apache-maven-3.9.6/conf/settings.xml
---

本项目使用 Maven + Spring Boot Starter Parent 进行依赖管理，核心约定如下：

1. **构建工具与版本**：基于 `spring-boot-starter-parent:3.2.5`，Java 版本锁定为 17；项目通过 `mvnw.cmd` 提供的 Maven Wrapper 启动，避免本地 Maven 版本差异。

2. **依赖声明方式**：所有第三方库在 `backend/pom.xml` 中集中声明。框架层依赖（如 spring-boot-starter-web、spring-boot-starter-data-jpa、spring-boot-starter-validation）不显式写版本号，由 parent POM 统一管理；业务/工具依赖（druid-spring-boot-3-starter、lombok、fastjson2、jjwt、commons-lang3、mysql-connector-j）则显式指定版本。

3. **作用域与打包策略**：运行时依赖（mysql-connector-j、jjwt-impl/jjwt-jackson）标记 `<scope>runtime</scope>`；lombok 标记 `<optional>true</optional>` 并通过 maven-compiler-plugin 的 annotationProcessorPaths 注入编译期注解处理器，同时在 spring-boot-maven-plugin 中排除 lombok 以避免打入最终 jar。

4. **仓库与镜像**：`.mvn/maven/apache-maven-3.9.6/conf/settings.xml` 仅包含默认注释配置和一个禁止 HTTP 外部仓库的 mirror（`maven-default-http-blocker`），未定义阿里云/Nexus 等镜像或私有仓库，实际下载走 Maven Central 默认源。用户级 settings.xml 应放在 `${user.home}/.m2/settings.xml`，但仓库路径仍使用默认 `~/.m2/repository`。

5. **无锁文件与 vendoring**：项目未提交 `pom.xml.lock`、`dependency-reduced-pom.xml` 或 vendor 目录，也不存在 Gradle 的 `build.gradle` / `gradle.lockfile`，因此不存在依赖锁定机制，每次构建可能拉取到上游最新兼容版本。

开发者规范建议：
- 新增依赖统一在 `pom.xml` 的 `<dependencies>` 中声明，优先使用 Spring Boot BOM 管理的版本。
- 仅将编译期工具（lombok）标记 optional，其余依赖按 runtime/test 正确划分 scope。
- 如需加速下载或接入私有仓库，应在团队统一的 `~/.m2/settings.xml` 中配置 mirrors/servers，而非修改项目内 settings.xml。