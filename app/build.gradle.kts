plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lumestar.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lumestar.tv"
        minSdk = 24
        targetSdk = 34
        versionCode = 30
        versionName = "0.7.2"
        // chave padrão do TMDB: vem do segredo TMDB_KEY do GitHub na hora de compilar (nunca fica no código)
        buildConfigField("String", "TMDB_KEY", "\"" + (System.getenv("TMDB_KEY") ?: "").filter { it.isLetterOrDigit() } + "\"")
        buildConfigField("String", "TMDB_KEY2", "\"" + (System.getenv("TMDB_KEY_2") ?: "").filter { it.isLetterOrDigit() } + "\"")
    }

    signingConfigs {
        create("lumestar") {
            // chave própria do LumeStarTV: a TV só aceita atualizações assinadas com ela
            storeFile = file("lumestar.keystore")
            storePassword = "lumestartv"
            keyAlias = "lumestar"
            keyPassword = "lumestartv"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("lumestar")
        }
        debug {
            signingConfig = signingConfigs.getByName("lumestar")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources.excludes.add("META-INF/*")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")

    // capas e logos
    implementation("io.coil-kt:coil-compose:2.6.0")
    // reprodutor (HLS, DASH, RTSP e vídeo comum)
    val media3 = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-datasource-rtmp:$media3")
    implementation("androidx.media3:media3-effect:$media3")
}
