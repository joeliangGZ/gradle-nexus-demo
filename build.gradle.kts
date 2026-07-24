// plugins 块：声明本项目需要使用的 Gradle 插件
plugins {
    // Spring Boot 插件：提供 bootJar、bootRun 等任务，支持构建可执行 Fat Jar 与本地运行
    id("org.springframework.boot") version "3.3.5"
    // Spring 依赖管理插件：导入 Spring Boot BOM，统一管理依赖版本，避免版本冲突
    id("io.spring.dependency-management") version "1.1.6"
    // maven-publish 插件：提供将构件发布到 Maven 仓库（如 Nexus）的能力
    `maven-publish`
    // Kotlin JVM 插件：支持编译 Kotlin 源码为 JVM 字节码
    kotlin("jvm") version "1.9.25"
    // Kotlin Spring 插件：为 Spring 相关注解的类自动 open，避免 Kotlin final 类与 Spring 代理的冲突
    kotlin("plugin.spring") version "1.9.25"
}

// 项目的 group（组织/包命名空间），从 gradle.properties 中的 project.group 属性读取
group = property("project.group").toString()
// 项目的版本号，从 gradle.properties 中的 project.version 属性读取
version = property("project.version").toString()

// java 扩展块：配置 Java 编译相关的全局选项
java {
    // 设置源码兼容级别为 Java 17（Spring Boot 3.x 最低要求 JDK 17）
    sourceCompatibility = JavaVersion.VERSION_17
}

// repositories 块：声明依赖解析时使用的仓库
repositories {
    // 使用 Maven Central 中央仓库下载依赖
    mavenCentral()
}

// dependencies 块：声明项目所需的依赖
dependencies {
    // Spring Boot Web 启动器：引入 Spring MVC、内嵌 Tomcat 等 Web 开发所需的全部依赖
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Jackson Kotlin 模块：支持 Kotlin 数据类的 JSON 序列化与反序列化
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    // Kotlin 反射库：Spring 在运行时通过反射处理 Kotlin 类时需要
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // Spring Boot 测试启动器：引入 JUnit 5、Mockito、Spring Test 等测试依赖
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// 注册一个自定义 Jar 任务 sourcesJar：用于打包源码 jar（classifier 为 sources）
tasks.register("sourcesJar", Jar::class) {
    // 设置归档分类器，生成的 jar 文件名会带 -sources 后缀
    archiveClassifier.set("sources")
    // 打包主源码集下的所有源码文件
    from(sourceSets.main.get().allSource)
}

// 自定义函数：读取 Nexus 相关配置
// 优先从项目属性（gradle.properties）中读取，没有则回退到环境变量，再没有返回空字符串
fun getNexusProperty(project: org.gradle.api.Project, key: String, envKey: String): String {
    // 从 project.properties 中按 key 取值并转为字符串
    return project.properties[key]?.toString()
        // 若属性不存在，则从系统环境变量 envKey 中读取
        ?: System.getenv(envKey)
        // 若都没有，返回空字符串
        ?: ""
}

// publishing 扩展块：配置 maven-publish 插件的发布行为
publishing {
    // Nexus 仓库基础地址，优先读 gradle.properties 中的 nexusUrl，否则读环境变量 NEXUS_URL
    val nexusUrl = getNexusProperty(project, "nexusUrl", "NEXUS_URL")
    // Nexus 登录用户名，优先读 gradle.properties 中的 nexusUsername，否则读环境变量 NEXUS_USERNAME
    val nexusUsername = getNexusProperty(project, "nexusUsername", "NEXUS_USERNAME")
    // Nexus 登录密码，优先读 gradle.properties 中的 nexusPassword，否则读环境变量 NEXUS_PASSWORD
    val nexusPassword = getNexusProperty(project, "nexusPassword", "NEXUS_PASSWORD")
    // 判断当前版本是否为快照版本（版本号中包含 SNAPSHOT 即为快照）
    val isSnapshot = version.toString().contains("SNAPSHOT", ignoreCase = true)

    // repositories 块：配置发布目标仓库列表
    repositories {
        // Nexus Release 仓库：发布正式版本
        maven {
            // 仓库名称，用于在任务名中标识：publishMavenPublicationToNexusReleaseRepository
            name = "nexusRelease"
            // Release 仓库地址：约定路径为 /maven-releases
            url = uri("${nexusUrl}/maven-releases")
            // 访问 Nexus 所需的凭据
            credentials {
                // 用户名
                username = nexusUsername
                // 密码
                password = nexusPassword
            }
        }
        // Nexus Snapshot 仓库：发布快照版本
        maven {
            // 仓库名称，对应任务名中会出现 NexusSnapshot
            name = "nexusSnapshot"
            // Snapshot 仓库地址：约定路径为 /maven-snapshots
            url = uri("${nexusUrl}/maven-snapshots")
            // 访问 Nexus 所需的凭据
            credentials {
                // 用户名
                username = nexusUsername
                // 密码
                password = nexusPassword
            }
        }
    }

    // afterEvaluate：在 Gradle 配置阶段结束后执行，此时版本等属性已确定
    afterEvaluate {
        // 对所有 PublishToMavenRepository 类型的任务进行配置
        tasks.withType(PublishToMavenRepository::class.java).configureEach {
            // 使用 onlyIf 控制任务是否执行：
            // - 快照版本时，只允许发布到 nexusSnapshot
            // - 非快照版本时，只允许发布到 nexusRelease
            onlyIf {
                (repository.name == "nexusSnapshot" && isSnapshot) ||
                    (repository.name == "nexusRelease" && !isSnapshot)
            }
        }
    }

    // publications 块：定义要发布的构件内容
    publications {
        // 创建一个名为 "maven" 的 Maven 类型发布物
        create<MavenPublication>("maven") {
            // 发布 java 组件：包含主 jar 以及编译、运行时依赖信息（由 java 插件提供）
            from(components["java"])

            // 额外发布 Spring Boot 的 Fat Jar（可执行 jar），classifier 设为 boot 以区分普通 jar
            artifact(tasks.bootJar.get()) {
                // 分类器：生成的构件文件名会带 -boot 后缀
                classifier = "boot"
            }

            // 额外发布源码 jar（sourcesJar 任务的产出物）
            artifact(tasks["sourcesJar"])

            // pom 块：配置生成的 pom.xml 元数据
            pom {
                // 项目名称，使用 Gradle 项目名
                name.set(project.name)
                // 项目描述
                description.set("A demo project for Nexus publishing")
                // 项目主页 URL（示例占位）
                url.set("https://example.com/${project.name}")

                // 许可证信息
                licenses {
                    license {
                        // 许可证名称：MIT
                        name.set("MIT License")
                        // 许可证文本地址
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                // 开发者信息（示例占位）
                developers {
                    developer {
                        // 开发者 ID
                        id.set("developer")
                        // 开发者姓名
                        name.set("Developer Name")
                        // 开发者邮箱
                        email.set("developer@example.com")
                    }
                }

                // 源码管理 SCM 信息（示例占位，使用 GitHub 格式）
                scm {
                    // 匿名只读连接
                    connection.set("scm:git:git://github.com/example/${project.name}.git")
                    // 开发者可写连接（SSH）
                    developerConnection.set("scm:git:ssh://github.com:example/${project.name}.git")
                    // 浏览器访问地址
                    url.set("https://github.com/example/${project.name}")
                }
            }
        }
    }
}
