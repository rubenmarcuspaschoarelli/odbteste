plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish") // Added for JitPack
}

android {
    namespace = "com.cardr.obdiqandroidsdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "SDK_KEY", "\"1feddf76-3b99-4c4b-869a-74046daa3e30\"")
        buildConfigField("String", "SDK_VERSION", "\"1.0.0\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
//    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    api("com.github.RRCummins:RepairClubAndroidSDK:1.3.12")
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "Cardr-com" // Replace with your GitHub username
            artifactId = "OBDIQAndroidSdk" // Library name
            version = "1.0.4"// Ensure this matches your Git tag

            afterEvaluate {
                from(components["release"]) // Use the existing release component, do not manually add the AAR
            }
        }
    }
}

