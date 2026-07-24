// 设置根项目名称，对应 Gradle 项目的根工程名
rootProject.name = "gradle-nexus-demo"

// pluginManagement：集中管理插件的版本与解析仓库，避免在每个子项目中重复声明版本号
pluginManagement {
    // 插件解析时使用的仓库列表
    repositories {
        // 使用 Gradle 官方插件仓库（Gradle Plugin Portal）
        gradlePluginPortal()
    }
    // 在 plugins 块中统一声明插件及其版本，子项目应用时可以省略版本号
    plugins {
        // Spring Boot 插件，版本 3.3.5（对应 Spring Boot 3.3.x 系列，需 JDK 17+）
        id("org.springframework.boot") version "3.3.5"
        // Spring 官方依赖管理插件，用于导入 Spring Boot BOM 并统一依赖版本
        id("io.spring.dependency-management") version "1.1.6"
    }
}
