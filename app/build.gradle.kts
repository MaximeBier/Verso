import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.roborazzi)
}

private val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.reader(Charsets.UTF_8)?.use { load(it) }
}

/** Valeur de local.properties en littéral Java pour BuildConfig ; "" si absente. */
fun localProperty(name: String): String =
    "\"" + localProperties.getProperty(name, "").trim().replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.maximebier.verso"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.maximebier.verso"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Traduction (V3) : clé et région Azure lues dans local.properties (jamais versionné). Sans clé, « Traduire » est masqué.
        buildConfigField("String", "TRANSLATOR_KEY", localProperty("translator.key"))
        buildConfigField("String", "TRANSLATOR_REGION", localProperty("translator.region"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        // Même exclusion que l'application de démonstration de Readium 3.4.0 (fichiers de licence en double).
        resources.excludes.add("META-INF/*")
    }

    lint {
        abortOnError = true
        // Exceptions justifiées dans app/lint.xml.
        lintConfig = file("lint.xml")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.fragment.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}

/**
 * Génère com.maximebier.verso.ui.theme.VersoPalette.kt depuis docs/design/tokens.json :
 * data class VersoColors (un champ par jeton, dans l'ordre du thème clair), les quatre thèmes et la palette des vignettes.
 * Échoue si un thème n'a pas exactement les mêmes jetons que le thème clair ou si une couleur est mal formée.
 */
abstract class GenerateDesignTokensTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val tokensFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        @Suppress("UNCHECKED_CAST")
        val root = groovy.json.JsonSlurper().parse(tokensFile.get().asFile, "UTF-8") as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val color = root["color"] as? Map<String, Any?> ?: throw GradleException("tokens.json : section « color » absente")
        val themes = linkedMapOf("Light" to "light", "Dark" to "dark", "Night" to "night", "Sepia" to "sepia")
        @Suppress("UNCHECKED_CAST")
        val values = themes.mapValues { (_, key) ->
            color[key] as? Map<String, Any?> ?: throw GradleException("tokens.json : thème « $key » absent")
        }
        val names = values.getValue("Light").keys.toList()
        values.forEach { (theme, tokens) ->
            val missing = names - tokens.keys
            val extra = tokens.keys - names.toSet()
            if (missing.isNotEmpty() || extra.isNotEmpty()) {
                throw GradleException("tokens.json : le thème $theme diffère du thème clair (manquants : $missing, en trop : $extra)")
            }
        }
        @Suppress("UNCHECKED_CAST")
        val covers = root["coverPalette"] as? Map<String, Any?> ?: throw GradleException("tokens.json : coverPalette absent")

        fun argb(value: Any?, where: String): String {
            val raw = value as? String ?: throw GradleException("tokens.json : $where doit être une couleur, trouvé « $value »")
            val digits = raw.removePrefix("#").uppercase()
            if (!digits.all { it in '0'..'9' || it in 'A'..'F' }) throw GradleException("tokens.json : $where mal formé (« $raw »)")
            return when (digits.length) {
                6 -> "0xFF$digits"
                8 -> "0x" + digits.substring(6, 8) + digits.substring(0, 6)
                else -> throw GradleException("tokens.json : $where doit être #RRGGBB ou #RRGGBBAA (« $raw »)")
            }
        }

        fun coverList(key: String): String {
            val list = covers[key] as? List<*> ?: throw GradleException("tokens.json : coverPalette.$key absent")
            return list.mapIndexed { index, value -> "Color(${argb(value, "coverPalette.$key[$index]")})" }.joinToString(", ")
        }

        val code = buildString {
            appendLine("// Fichier généré par la tâche Gradle generateDesignTokens depuis docs/design/tokens.json. Ne pas modifier à la main.")
            appendLine("package com.maximebier.verso.ui.theme")
            appendLine()
            appendLine("import androidx.compose.runtime.Immutable")
            appendLine("import androidx.compose.ui.graphics.Color")
            appendLine()
            appendLine("/** Rôles de couleur d'un thème Verso (un champ par jeton de tokens.json). */")
            appendLine("@Immutable")
            appendLine("data class VersoColors(")
            names.forEach { appendLine("    val $it: Color,") }
            appendLine(")")
            appendLine()
            appendLine("/** Les quatre thèmes de tokens.json (V1 : Light, Dark et Night ; V2 : Sepia) et la palette des vignettes générées. */")
            appendLine("object VersoPalette {")
            values.forEach { (theme, tokens) ->
                appendLine("    val $theme: VersoColors = VersoColors(")
                names.forEach { name -> appendLine("        $name = Color(${argb(tokens[name], "color.${themes.getValue(theme)}.$name")}),") }
                appendLine("    )")
            }
            appendLine("    val CoverLight: List<Color> = listOf(${coverList("light")})")
            appendLine("    val CoverDark: List<Color> = listOf(${coverList("dark")})")
            appendLine("}")
        }
        val dir = outputDir.get().asFile.resolve("com/maximebier/verso/ui/theme")
        dir.mkdirs()
        dir.resolve("VersoPalette.kt").writeText(code, Charsets.UTF_8)
    }
}

val generateDesignTokens = tasks.register<GenerateDesignTokensTask>("generateDesignTokens") {
    group = "verso"
    description = "Génère VersoPalette.kt depuis docs/design/tokens.json."
    tokensFile.set(layout.projectDirectory.file("../docs/design/tokens.json"))
    outputDir.set(layout.buildDirectory.dir("generated/tokens"))
}

androidComponents {
    onVariants { variant ->
        // AGP enregistre le dossier comme source Kotlin générée et fait dépendre la compilation de la tâche.
        variant.sources.kotlin?.addGeneratedSourceDirectory(generateDesignTokens, GenerateDesignTokensTask::outputDir)
    }
}
