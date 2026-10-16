---
kind: build_system
name: Maven + Spring Boot 构建与部署体系
category: build_system
scope:
    - '**'
source_files:
    - 补后端/backend/pom.xml
    - 补后端/DEPLOYMENT.md
---

## 构建系统概览

本项目采用 Maven + Spring Boot 作为唯一的构建与打包方案，无 Docker、CI/CD、Makefile 等自动化流水线。

### 核心工具链
- 构建工具: Maven 3.9.6（项目内自带 backend/.mvn/maven/apache-maven-3.9.6 及独立 apache-maven-3.9.6 目录）
- Java 版本: JDK 17（java.version=17，maven.compiler.source/target=17）
- Spring Boot 版本: 3.2.5（通过 spring-boot-starter-parent 管理）
- 产物格式: 可执行 JAR（supervision-site-management-1.0.0.jar）

### 关键构建配置（pom.xml）
- 依赖管理：通过 spring-boot-starter-parent 统一版本，Lombok 声明为 optional 并通过 annotationProcessorPaths 注入编译期注解处理器
- 打包插件：spring-boot-maven-plugin，显式排除 Lombok 避免打入最终包
- 测试依赖：spring-boot-starter-test，scope 为 test

### 构建命令约定
```bash
# 本地开发
mvn clean package -DskipTests          # 跳过测试打包
mvn spring-boot:run                    # 直接运行
```

### 产物与发布流程
- 构建产物位于 backend/target/supervision-site-management-1.0.0.jar
- 前端为纯静态 HTML 文件，直接复制到服务器 /opt/supervision/static/
- 数据库初始化脚本位于 backend/src/main/resources/schema.sql
- 生产部署通过 nohup java -jar ... 启动，由 systemd 管理进程生命周期
- Nginx 反向代理将 /api/ 转发至后端 8080 端口，根路径 / 指向静态 HTML

### 环境变量覆盖
支持通过 SPRING_DATASOURCE_* 等环境变量覆盖 application.yml 中的配置，无需修改源码即可适配不同环境。

### 缺失的构建能力
- 无单元测试执行阶段（文档中明确 -DskipTests）
- 无代码质量检查（SonarQube、Checkstyle 等）
- 无容器化（Dockerfile / docker-compose）
- 无 CI/CD 流水线（GitHub Actions、Jenkins 等）
- 无多环境 profile（dev/test/prod 均通过同一 JAR + 外部配置区分）
- 版本号硬编码在 pom.xml 中，未使用语义化版本或 Git tag 驱动