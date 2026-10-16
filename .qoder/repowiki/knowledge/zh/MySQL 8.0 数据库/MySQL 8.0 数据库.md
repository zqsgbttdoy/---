---
kind: external_dependency
name: MySQL 8.0 数据库
slug: mysql
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
---

项目使用 MySQL 8.0 作为关系型数据库，通过 mysql-connector-j 驱动连接。数据库名为 supervision_db，字符集 utf8mb4，排序规则 utf8mb4_unicode_ci。JPA/Hibernate 使用 update 模式自动维护表结构，生产环境建议改用 Flyway/Liquibase 进行版本控制。