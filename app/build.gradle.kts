import java.net.URI
import java.net.HttpURLConnection
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val debianRuntimeRoot = layout.buildDirectory.dir("generated/debianRuntime")

android {
    namespace = "com.yukisoffd.lyracode"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.yukisoffd.lyracode"
        minSdk = 26
        targetSdk = 36
        versionCode = 68
        versionName = "3.6.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "LYRA_UPDATE_MANIFEST_URL",
            "\"${providers.gradleProperty("lyra.updateManifestUrl").orNull.orEmpty()}\"",
        )
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
    packaging {
        resources {
            merges += "META-INF/LICENSE.md"
            merges += "META-INF/NOTICE.md"
        }
        jniLibs.useLegacyPackaging = true
    }
    sourceSets {
        getByName("main") {
            assets.directories.add(rootProject.file("deepseek_v3_tokenizer/deepseek_v3_tokenizer").absolutePath)
            kotlin.directories.add("src/debianRuntime/java")
            res.directories.add("src/debianRuntime/res")
            jniLibs.directories.add(debianRuntimeRoot.get().dir("jniLibs").asFile.absolutePath)
        }
    }
}

val prepareDebianRuntime by tasks.registering {
    group = "build setup"
    description = "Downloads and verifies the small arm64 PRoot engine bundled in every APK."

    val outputRoot = debianRuntimeRoot.get().asFile
    val proot = outputRoot.resolve("jniLibs/arm64-v8a/libproot_exec.so")
    val loader = outputRoot.resolve("jniLibs/arm64-v8a/libproot_loader.so")
    outputs.files(proot, loader)

    doLast {
            fun sha256(file: File): String {
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                return digest.digest().joinToString("") { "%02x".format(it) }
            }

            fun downloadVerified(url: String, destination: File, expectedSha256: String, cacheName: String) {
                if (destination.isFile && sha256(destination) == expectedSha256) return
                val cacheDir = File(gradle.gradleUserHomeDir, "caches/lyracode-debian-runtime")
                cacheDir.mkdirs()
                val cached = File(cacheDir, cacheName)
                val temporary = File(cacheDir, "$cacheName.part")
                var lastError: Throwable? = null
                for (attempt in 0 until if (cached.isFile && sha256(cached) == expectedSha256) 0 else 3) {
                    temporary.delete()
                    try {
                        val connection = URI(url).toURL().openConnection() as HttpURLConnection
                        connection.connectTimeout = 30_000
                        connection.readTimeout = 120_000
                        connection.instanceFollowRedirects = true
                        connection.setRequestProperty("User-Agent", "LyraCode-Gradle")
                        try {
                            connection.inputStream.buffered().use { input ->
                                temporary.outputStream().buffered().use(input::copyTo)
                            }
                        } finally {
                            connection.disconnect()
                        }
                        lastError = null
                        break
                    } catch (error: Throwable) {
                        lastError = error
                        if (attempt < 2) Thread.sleep(1_000L * (attempt + 1))
                    }
                }
                lastError?.let { throw it }
                if (temporary.isFile) {
                    val actual = sha256(temporary)
                    check(actual == expectedSha256) {
                        "SHA-256 mismatch for $url: expected $expectedSha256, got $actual"
                    }
                    Files.move(temporary.toPath(), cached.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                check(cached.isFile && sha256(cached) == expectedSha256) { "Verified runtime cache is missing: $cached" }
                destination.parentFile.mkdirs()
                val outputTemporary = File(destination.parentFile, "${destination.name}.part")
                cached.copyTo(outputTemporary, overwrite = true)
                Files.move(
                    outputTemporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }

            val rikkaCommit = "693c2ce53fe28d4eb03517edffd7824f9f99f682"
            downloadVerified(
                "https://raw.githubusercontent.com/rikkahub/rikkahub/$rikkaCommit/workspace/src/main/jniLibs/arm64-v8a/libproot_exec.so",
                proot,
                "d4ffbd19e20614c908be774af5dcd9da306094482f556713db037563c353219c",
                "libproot_exec-arm64-v8a.so",
            )
            downloadVerified(
                "https://raw.githubusercontent.com/rikkahub/rikkahub/$rikkaCommit/workspace/src/main/jniLibs/arm64-v8a/libproot_loader.so",
                loader,
                "44ef39c1e1a18c09f6e4c4b5d6f8bba82d30596598bd155ec162d05c5122ff04",
                "libproot_loader-arm64-v8a.so",
            )
    }
}
tasks.named("preBuild").configure { dependsOn(prepareDebianRuntime) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.jsch)
    implementation(project(":jlatexmath"))
    implementation(libs.jetbrains.markdown)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.sora.editor.bom))
    implementation(libs.sora.editor)
    implementation(libs.sora.language.textmate)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.android.mail)
    implementation(libs.android.activation)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

