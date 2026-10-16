package com.supervision;

import com.supervision.config.ProductionSafetyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProductionSafetyValidator 生产安全启动校验单元测试")
class ProductionSafetyValidatorTest {

    private static final String DEV_DEFAULT_SECRET =
            "supervision-site-management-default-secret-key-please-change-in-production-2026";

    private ProductionSafetyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ProductionSafetyValidator();
        // 默认设置一组全部合法的配置，各用例再单独覆盖某一项为非法值
        setField("jwtSecret", "a-strong-production-secret-key-with-enough-length-2026");
        setField("datasourcePassword", "Str0ng@Passw0rd!");
        setField("corsAllowedOrigins", "https://supervision.example.com");
        setField("defaultResetPassword", "Init@2026!");
        setField("uploadSigningSecret", "test-only-independent-attachment-secret-2026");
    }

    /** 通过反射设置私有字段，模拟 @Value 注入 */
    private void setField(String name, String value) {
        try {
            Field field = ProductionSafetyValidator.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(validator, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("全部配置合法时校验通过，不抛异常")
    void testValidateAllValid() {
        assertDoesNotThrow(() -> validator.validate());
    }

    @Test
    void uploadSecretMustBeIndependentAndConfigured() {
        for (String invalid : new String[]{null, "", "short", "请替换为openssl_rand_-base64_48生成的随机密钥",
                "a-strong-production-secret-key-with-enough-length-2026", DEV_DEFAULT_SECRET}) {
            setField("uploadSigningSecret", invalid);
            var error = assertThrows(IllegalStateException.class, () -> validator.validate());
            assertTrue(error.getMessage().contains("APP_UPLOAD_SIGNING_SECRET"));
        }
    }

    @Test
    void qaAcceptanceJwtTemplatePlaceholderMustFail() throws Exception {
        String template = java.nio.file.Files.readString(java.nio.file.Path.of("../deploy/supervision.env.example"));
        String placeholder = template.lines().filter(line -> line.startsWith("APP_JWT_SECRET="))
                .findFirst().orElseThrow().substring("APP_JWT_SECRET=".length());
        setField("jwtSecret", placeholder);
        assertThrows(IllegalStateException.class, () -> validator.validate(), "公开模板占位密钥不得通过生产启动校验");
    }

    private org.springframework.boot.test.context.runner.ApplicationContextRunner prodConfigRunner() {
        // 只加载配置和校验器，不启用数据源／Web自动配置，不连接MySQL。
        return new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withInitializer(new org.springframework.boot.test.context.ConfigDataApplicationContextInitializer())
                .withUserConfiguration(ProductionSafetyValidator.class)
                .withPropertyValues("spring.profiles.active=prod", "APP_JWT_SECRET=" + java.util.UUID.randomUUID(),
                        "APP_UPLOAD_SIGNING_SECRET=" + java.util.UUID.randomUUID(),
                        "SPRING_DATASOURCE_USERNAME=qa_config_only", "SPRING_DATASOURCE_PASSWORD=Qa9" + java.util.UUID.randomUUID(),
                        "APP_DEFAULT_RESET_PASSWORD=Qa9" + java.util.UUID.randomUUID(),
                        "APP_CORS_ALLOWED_ORIGINS=https://qa22.example.invalid");
    }

    @Test
    void qaAcceptanceProdProfileOverridesDevelopmentDefaults() {
        prodConfigRunner().run(context -> {
            assertNull(context.getStartupFailure());
            var env = context.getEnvironment();
            assertTrue(java.util.Arrays.asList(env.getActiveProfiles()).contains("prod"));
            assertEquals("validate", env.getProperty("spring.jpa.hibernate.ddl-auto"));
            assertEquals("false", env.getProperty("spring.jpa.show-sql"));
            assertEquals("https://qa22.example.invalid", env.getProperty("app.cors.allowed-origins"));
            assertEquals(env.getProperty("APP_JWT_SECRET"), env.getProperty("app.jwt.secret"));
            assertEquals(env.getProperty("SPRING_DATASOURCE_PASSWORD"), env.getProperty("spring.datasource.password"));
            assertNotNull(context.getBean(ProductionSafetyValidator.class));
        });
    }

    @Test
    void qaAcceptanceProdEmptySecretFailsStartup() {
        prodConfigRunner().withPropertyValues("APP_JWT_SECRET=")
                .run(context -> assertNotNull(context.getStartupFailure(), "空JWT密钥必须阻止启动"));
    }

    // ---------------- JWT 密钥校验 ----------------

    @Test
    @DisplayName("JWT 密钥为空应抛出 IllegalStateException 并提示 APP_JWT_SECRET")
    void testJwtSecretEmpty() {
        setField("jwtSecret", "");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validate());
        assertTrue(ex.getMessage().contains("APP_JWT_SECRET"));
    }

    @Test
    @DisplayName("JWT 密钥长度不足 32 字符应抛出 IllegalStateException")
    void testJwtSecretTooShort() {
        setField("jwtSecret", "short-secret");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validate());
        assertTrue(ex.getMessage().contains("APP_JWT_SECRET"));
    }

    @Test
    @DisplayName("JWT 密钥等于开发默认值应抛出 IllegalStateException")
    void testJwtSecretIsDevDefault() {
        setField("jwtSecret", DEV_DEFAULT_SECRET);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validate());
        assertTrue(ex.getMessage().contains("APP_JWT_SECRET"));
    }

    // ---------------- 数据库密码校验 ----------------

    @Test
    @DisplayName("数据库密码为空应抛出 IllegalStateException 并提示 SPRING_DATASOURCE_PASSWORD")
    void testDatasourcePasswordEmpty() {
        setField("datasourcePassword", "");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validate());
        assertTrue(ex.getMessage().contains("SPRING_DATASOURCE_PASSWORD"));
    }

    @Test
    @DisplayName("数据库密码为 root 应抛出 IllegalStateException")
    void testDatasourcePasswordRoot() {
        setField("datasourcePassword", "root");
        assertThrows(IllegalStateException.class, () -> validator.validate());
    }

    @Test
    @DisplayName("数据库密码为 123456 应抛出 IllegalStateException")
    void testDatasourcePassword123456() {
        setField("datasourcePassword", "123456");
        assertThrows(IllegalStateException.class, () -> validator.validate());
    }

    @Test
    @DisplayName("数据库密码长度不足 8 位应抛出 IllegalStateException 并提示长度")
    void testDatasourcePasswordTooShort() {
        setField("datasourcePassword", "Ab1");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validate());
        assertTrue(ex.getMessage().contains("长度不足"));
    }

    @Test
    @DisplayName("数据库密码仅字母无数字（如 password）应抛出 IllegalStateException")
    void testDatasourcePasswordNoDigit() {
        setField("datasourcePassword", "password");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validate());
        assertTrue(ex.getMessage().contains("字母与数字"));
    }

    // ---------------- CORS 允许来源校验 ----------------

    @Test
    @DisplayName("CORS 来源为空应抛出 IllegalStateException 并提示 APP_CORS_ALLOWED_ORIGINS")
    void testCorsOriginsEmpty() {
        setField("corsAllowedOrigins", "");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validate());
        assertTrue(ex.getMessage().contains("APP_CORS_ALLOWED_ORIGINS"));
    }

    @Test
    @DisplayName("CORS 来源包含通配符 * 应抛出 IllegalStateException")
    void testCorsOriginsWildcard() {
        setField("corsAllowedOrigins", "*");
        assertThrows(IllegalStateException.class, () -> validator.validate());
    }

    @Test
    @DisplayName("CORS 来源列表中混入 * 也应抛出 IllegalStateException")
    void testCorsOriginsListContainsWildcard() {
        setField("corsAllowedOrigins", "https://a.example.com,*");
        assertThrows(IllegalStateException.class, () -> validator.validate());
    }

    // ---------------- 默认重置密码校验 ----------------

    @Test
    @DisplayName("默认重置密码为空应抛出 IllegalStateException 并提示 APP_DEFAULT_RESET_PASSWORD")
    void testDefaultResetPasswordEmpty() {
        setField("defaultResetPassword", "");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validate());
        assertTrue(ex.getMessage().contains("APP_DEFAULT_RESET_PASSWORD"));
    }

    @Test
    @DisplayName("默认重置密码为 123456 应抛出 IllegalStateException")
    void testDefaultResetPassword123456() {
        setField("defaultResetPassword", "123456");
        assertThrows(IllegalStateException.class, () -> validator.validate());
    }

    @Test
    @DisplayName("默认重置密码仅数字无字母（如 12345678）应抛出 IllegalStateException")
    void testDefaultResetPasswordNoLetter() {
        setField("defaultResetPassword", "12345678");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validate());
        assertTrue(ex.getMessage().contains("字母与数字"));
    }

    @Test
    @DisplayName("默认重置密码长度不足 8 位应抛出 IllegalStateException")
    void testDefaultResetPasswordTooShort() {
        setField("defaultResetPassword", "Ab1!");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validate());
        assertTrue(ex.getMessage().contains("长度不足"));
    }
}
