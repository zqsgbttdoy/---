---
kind: external_dependency
name: Nginx 反向代理服务器
slug: nginx
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
---

生产环境使用 Nginx 1.20+ 作为反向代理服务器，负责静态 HTML 文件服务和 API 请求转发。Nginx 监听 80 端口，将 /api/ 路径的请求代理到后端 8080 端口的 Spring Boot 应用。同时配置了文件上传大小限制（client_max_body_size 100m）和超时设置。