package com.heecomou.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI heecomouOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("HeecoMou API")
                        .description("HeecoMou 智能语音输入法后端 API 文档\n\n"
                                + "### 认证方式\n"
                                + "除 /auth/** 和 /health 外，所有接口需要 Bearer Token 认证。\n\n"
                                + "1. 先调用 `POST /api/v1/auth/register` 注册\n"
                                + "2. 再调用 `POST /api/v1/auth/login` 获取 Token\n"
                                + "3. 点击右上角 **Authorize** 按钮，输入 `Bearer <accessToken>`\n\n"
                                + "### 通用响应格式\n"
                                + "所有接口统一返回 `ApiResponse<T>` 格式:\n"
                                + "```json\n"
                                + "{\"code\":200,\"message\":\"success\",\"data\":{...}}\n"
                                + "```")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("HeecoMou Team")
                                .email("dev@heecomou.com")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"))
                .components(new Components()
                        .addSecuritySchemes("Bearer", new SecurityScheme()
                                .name("Bearer")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("输入从登录接口获取的 accessToken")));
    }
}
