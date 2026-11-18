import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Credenciais da keystore de release (fora do git: keystore.properties).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "co.golink.tester"
    compileSdk = 36

    defaultConfig {
        applicationId = "co.golink.tester"
        minSdk = 26
        targetSdk = 36
        versionCode = 192
        versionName = "4.0.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.getProperty("storeFile") != null) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Sem keystore.properties (CI) sai um APK sem assinatura: serve para validar o R8.
            signingConfig = if (keystoreProps.getProperty("storeFile") != null) signingConfigs.getByName("release") else null
            // App Links: só golink.co (verificável); repetir o host é inofensivo.
            manifestPlaceholders["appLinkAltHost"] = "golink.co"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLinkAltHost"] = "tester.golink.co"
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // O Tesseract (OCR dos talões) traz ~7 MB de .so por ABI: comprimidos na APK.
        jniLibs {
            useLegacyPackaging = true
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // Hilt 2.58 traz um kotlin-metadata-jvm que só lê metadados até 2.3; o Kotlin 2.3.x escreve 2.4.
    // (O Hilt 2.59+ já o resolve, mas exige o AGP 9.) Remover ao passar para o AGP 9 + Hilt novo.
    ksp("org.jetbrains.kotlin:kotlin-metadata-jvm:${libs.versions.kotlin.get()}")
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.coil.video)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.zxing.core)
    implementation(libs.tesseract4android)
    // Recibos: câmara em direto a ler o QR das faturas (análise por frame com ZXing).
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    // Pass: sugestões de preenchimento no teclado (InlineSuggestionUi, Android 11+).
    implementation(libs.androidx.autofill)
    // Passkeys (WebAuthn) via Credential Manager; play-services para Android ≤ 13.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.webkit)

    // E2E: libsodium nativo (crypto_box_seal, Argon2id, secretbox) — interopera
    // com o libsodium do PHP/JS. AES-256-GCM do codec usa o javax.crypto.
    // = "…@aar": só o AAR, sem dependências transitivas (o jar do JNA chocava com o AAR).
    implementation(variantOf(libs.lazysodium.android) { artifactType("aar") }) { isTransitive = false }
    implementation(variantOf(libs.jna) { artifactType("aar") }) { isTransitive = false }

    // Notes nativas: QuickJS corre o assets/notes-core.js (Yjs + y-prosemirror, gerado no repo web
    // por ops/notes-core) — o mesmo formato do editor da web, sem WebView.
    implementation(libs.quickjs.wrapper.android)

    testImplementation(libs.junit)
}

// OkHttp 5: a variante Android lê a lista de sufixos públicos (topPrivateDomain) dos assets,
// via androidx.startup — nos testes na JVM não há Context; usar a variante JVM só nos testes.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("com.squareup.okhttp3:okhttp-android"))
            .using(module("com.squareup.okhttp3:okhttp-jvm:${libs.versions.okhttp.get()}"))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
