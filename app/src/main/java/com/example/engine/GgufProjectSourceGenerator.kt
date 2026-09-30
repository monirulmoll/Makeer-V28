package com.example.engine

import android.content.Context
import com.example.data.CanvasComponentEntity
import com.example.data.ComponentWidgetType
import java.io.File
import java.util.Locale

/**
 * Converts a validated [StructuredAppSpecification] and AST [CanvasComponentEntity] list
 * into a complete Android Floating Window Overlay project and writes the generated source
 * tree to disk for APK compilation.
 */
object GgufProjectSourceGenerator {

    fun generateAndroidOverlayProject(
        context: Context,
        specification: StructuredAppSpecification,
        components: List<CanvasComponentEntity>,
        modelState: GgufModelState,
        existingScratchFiles: Map<String, String> = emptyMap()
    ): GeneratedBlueprintSpec {
        val scratchFiles = LinkedHashMap<String, String>()
        if (existingScratchFiles.isNotEmpty()) {
            scratchFiles.putAll(existingScratchFiles)
        }

        val manifestXml = generateManifestXml(specification)
        val buildGradleKts = generateBuildGradleKts(specification)
        val stringsXml = generateStringsXml(specification)
        val specJsonFile = specification.rawLlmJson
        val mainActivityKt = generateMainActivityKt(specification, components)
        val overlayServiceKt = generateOverlayServiceKt(specification, components)
        val logicEngineJava = generateScratchLogicEngineJava(specification, components)

        scratchFiles["AndroidManifest.xml"] = manifestXml
        scratchFiles["build.gradle.kts"] = buildGradleKts
        scratchFiles["res/values/strings.xml"] = stringsXml
        scratchFiles["assets/overlay_specification.json"] = specJsonFile
        scratchFiles["MainActivity.kt"] = mainActivityKt
        scratchFiles["AiDynamicOverlayService.kt"] = overlayServiceKt
        scratchFiles["AiScratchLogicEngine.java"] = logicEngineJava

        val projectFolderSlug = specification.appName
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('_')
            .ifEmpty { "GeneratedOverlayProject" }

        val projectRootDir = File(context.filesDir, "ai_generated_projects/$projectFolderSlug").apply {
            mkdirs()
        }

        val artifacts = mutableListOf<GeneratedFileArtifact>()
        for ((relPath, content) in scratchFiles) {
            val outFile = File(projectRootDir, relPath)
            outFile.parentFile?.mkdirs()
            outFile.writeText(content, Charsets.UTF_8)
            artifacts.add(
                GeneratedFileArtifact(
                    name = outFile.name,
                    relativePath = relPath,
                    fullPath = outFile.absolutePath,
                    role = when {
                        relPath.endsWith("AndroidManifest.xml") -> "Android Manifest & Overlay Permissions"
                        relPath.endsWith("build.gradle.kts") -> "Gradle Build Configuration"
                        relPath.endsWith("overlay_specification.json") -> "llama.cpp Structured JSON Specification"
                        relPath.endsWith("MainActivity.kt") -> "Overlay Permission & Service Launcher Activity"
                        relPath.endsWith("AiDynamicOverlayService.kt") -> "WindowManager Draggable Floating Panel Service"
                        relPath.endsWith("AiScratchLogicEngine.java") -> "Compiled Overlay Action & Calculation Engine"
                        else -> "Android Project Resource"
                    }
                )
            )
        }

        val diagnostics = listOf(
            "llama.cpp model: ${modelState.modelFileName} (${modelState.modelDescription})",
            "Prompt tokens: ${specification.inferenceTrace.inputTokenIds.size} | Generated tokens: ${specification.inferenceTrace.generatedTokenIds.size}",
            "Validated JSON specification -> ${components.size} AST overlay widgets",
            "Wrote ${artifacts.size} Android project files to ${projectRootDir.absolutePath}"
        )

        val summaryText = buildString {
            appendLine("// === REAL LLAMA.CPP GGUF OVERLAY SPECIFICATION ===")
            appendLine(specification.rawLlmJson)
            appendLine()
            appendLine("// === AiDynamicOverlayService.kt ===")
            appendLine(overlayServiceKt)
            appendLine()
            appendLine("// === AiScratchLogicEngine.java ===")
            appendLine(logicEngineJava)
        }

        val structuredBuildReport = buildString {
            appendLine("Model: ${modelState.modelFileName} (${modelState.modelArchitecture})")
            appendLine("Generated Tokens: ${specification.inferenceTrace.generatedTokenIds.size}")
            appendLine("Overlay Panel: ${specification.panel.width}x${specification.panel.height}dp (draggable=${specification.panel.draggable})")
            appendLine("Package: ${specification.packageName}")
            appendLine("Widgets: ${components.joinToString { "${it.label} [${it.type}]" }}")
        }

        return GeneratedBlueprintSpec(
            suggestedAppName = specification.appName,
            suggestedPackageName = specification.packageName,
            suggestedOverlayTitle = specification.overlayTitle,
            suggestedTargetFilePath = specification.targetFilePath,
            components = components,
            kotlinJavaSummary = summaryText,
            generatedScratchFiles = scratchFiles,
            compilerDiagnostics = diagnostics,
            autoPatchedFixes = emptyList(),
            finalErrorCount = 0,
            isFloatingOverlayApp = true,
            appCategory = "FLOATING_OVERLAY_APP",
            projectRootPath = projectRootDir.absolutePath,
            structuredBuildOutput = structuredBuildReport,
            fileArtifacts = artifacts,
            requestClass = specification.requestClass,
            decisionAnnouncement = specification.decisionAnnouncement,
            appPurpose = specification.appPurpose,
            buildPlanSummary = structuredBuildReport,
            expectedBehavior = specification.expectedBehavior
        )
    }

    private fun generateManifestXml(spec: StructuredAppSpecification): String {
        val permsXml = spec.permissions.joinToString("\n    ") { perm ->
            "<uses-permission android:name=\"$perm\" />"
        }
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="${spec.packageName}">

                $permsXml

                <application
                    android:allowBackup="true"
                    android:label="${spec.appName}"
                    android:supportsRtl="true">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true"
                        android:label="${spec.appName}">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>

                    <service
                        android:name=".service.${spec.service.className}"
                        android:enabled="true"
                        android:exported="false" />
                </application>
            </manifest>
        """.trimIndent()
    }

    private fun generateBuildGradleKts(spec: StructuredAppSpecification): String {
        return """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
            }

            android {
                namespace = "${spec.packageName}"
                compileSdk = 35

                defaultConfig {
                    applicationId = "${spec.packageName}"
                    minSdk = 24
                    targetSdk = 35
                    versionCode = 1
                    versionName = "1.0.0"
                }
            }
        """.trimIndent()
    }

    private fun generateStringsXml(spec: StructuredAppSpecification): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">${spec.appName}</string>
                <string name="overlay_title">${spec.overlayTitle}</string>
            </resources>
        """.trimIndent()
    }

    private fun generateMainActivityKt(
        spec: StructuredAppSpecification,
        components: List<CanvasComponentEntity>
    ): String {
        return """
            package ${spec.packageName}

            import android.app.Activity
            import android.content.Intent
            import android.net.Uri
            import android.os.Bundle
            import android.provider.Settings
            import android.widget.Button
            import android.widget.LinearLayout
            import android.widget.TextView
            import ${spec.packageName}.service.${spec.service.className}

            class MainActivity : Activity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    val root = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(48, 48, 48, 48)
                    }
                    val titleView = TextView(this).apply {
                        text = "${spec.overlayTitle} (${components.size} widgets)"
                        textSize = 20f
                    }
                    val launchBtn = Button(this).apply {
                        text = "Start Floating Overlay Window"
                        setOnClickListener {
                            if (!Settings.canDrawOverlays(this@MainActivity)) {
                                startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${'$'}packageName")
                                    )
                                )
                            } else {
                                startService(Intent(this@MainActivity, ${spec.service.className}::class.java))
                            }
                        }
                    }
                    root.addView(titleView)
                    root.addView(launchBtn)
                    setContentView(root)
                }
            }
        """.trimIndent()
    }

    private fun generateOverlayServiceKt(
        spec: StructuredAppSpecification,
        components: List<CanvasComponentEntity>
    ): String {
        val widgetsBuilderCode = components.joinToString("\n                    ") { comp ->
            val safeLabel = comp.label.replace("\"", "\\\"")
            when (comp.type) {
                ComponentWidgetType.INPUT.name ->
                    "val input_${comp.id} = EditText(this@apply.context).apply { hint = \"$safeLabel\"; setText(\"${comp.currentValue.replace("\"", "\\\"")}\") }; addView(input_${comp.id})"
                ComponentWidgetType.TOGGLE.name ->
                    "val toggle_${comp.id} = Switch(this@apply.context).apply { text = \"$safeLabel\"; isChecked = ${comp.currentValue.equals("true", true)} }; addView(toggle_${comp.id})"
                ComponentWidgetType.SLIDER.name ->
                    "val slider_${comp.id} = SeekBar(this@apply.context).apply { max = ${comp.sliderMax}; progress = ${comp.currentValue.toIntOrNull() ?: 50} }; addView(slider_${comp.id})"
                ComponentWidgetType.TEXT.name ->
                    "val text_${comp.id} = TextView(this@apply.context).apply { text = \"$safeLabel: ${comp.currentValue.replace("\"", "\\\"")}\" }; addView(text_${comp.id})"
                else ->
                    "val btn_${comp.id} = Button(this@apply.context).apply { text = \"$safeLabel\"; setOnClickListener { AiScratchLogicEngine.onActionTriggered(\"$safeLabel\") } }; addView(btn_${comp.id})"
            }
        }

        return """
            package ${spec.packageName}.service

            import android.app.Service
            import android.content.Intent
            import android.graphics.Color
            import android.graphics.PixelFormat
            import android.os.IBinder
            import android.view.Gravity
            import android.view.MotionEvent
            import android.view.WindowManager
            import android.widget.Button
            import android.widget.EditText
            import android.widget.LinearLayout
            import android.widget.SeekBar
            import android.widget.Switch
            import android.widget.TextView
            import ${spec.packageName}.engine.AiScratchLogicEngine

            class ${spec.service.className} : Service() {
                private lateinit var windowManager: WindowManager
                private var overlayRoot: LinearLayout? = null

                override fun onCreate() {
                    super.onCreate()
                    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                    val params = WindowManager.LayoutParams(
                        ${spec.panel.width},
                        ${spec.panel.height},
                        WindowManager.LayoutParams.${spec.service.windowType},
                        WindowManager.LayoutParams.${spec.service.flags},
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                        x = ${spec.panel.x}
                        y = ${spec.panel.y}
                    }

                    val container = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setBackgroundColor(Color.parseColor("${spec.theme.panelColor}"))
                        setPadding(24, 24, 24, 24)
                        val header = TextView(context).apply {
                            text = "${spec.overlayTitle}"
                            setTextColor(Color.parseColor("${spec.theme.textColor}"))
                            textSize = 16f
                        }
                        addView(header)
                        $widgetsBuilderCode
                    }

                    if (${spec.panel.draggable}) {
                        var initialX = 0
                        var initialY = 0
                        var touchX = 0f
                        var touchY = 0f
                        container.setOnTouchListener { _, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    initialX = params.x
                                    initialY = params.y
                                    touchX = event.rawX
                                    touchY = event.rawY
                                    true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    params.x = initialX + (event.rawX - touchX).toInt()
                                    params.y = initialY + (event.rawY - touchY).toInt()
                                    windowManager.updateViewLayout(container, params)
                                    true
                                }
                                else -> false
                            }
                        }
                    }

                    overlayRoot = container
                    windowManager.addView(container, params)
                }

                override fun onDestroy() {
                    overlayRoot?.let { windowManager.removeView(it) }
                    overlayRoot = null
                    super.onDestroy()
                }

                override fun onBind(intent: Intent?): IBinder? = null
            }
        """.trimIndent()
    }

    private fun generateScratchLogicEngineJava(
        spec: StructuredAppSpecification,
        components: List<CanvasComponentEntity>
    ): String {
        val fieldsCode = spec.widgets.joinToString("\n    ") { w ->
            "public static String ${w.fieldSlug} = \"${w.initialValue.replace("\"", "\\\"")}\";"
        }
        return """
            package ${spec.packageName}.engine;

            import java.util.LinkedHashMap;
            import java.util.Map;

            public final class AiScratchLogicEngine {
                public static final String APP_NAME = "${spec.appName.replace("\"", "\\\"")}";
                public static final String PACKAGE_NAME = "${spec.packageName}";
                public static final int WIDGET_COUNT = ${components.size};
                $fieldsCode

                private static final Map<String, String> STATE = new LinkedHashMap<>();

                static {
                    STATE.put("status", "Ready");
                    STATE.put("panelSize", "${spec.panel.width}x${spec.panel.height}");
                }

                public static synchronized String onActionTriggered(String actionLabel) {
                    if (actionLabel == null) return "";
                    String lower = actionLabel.toLowerCase();
                    if (lower.contains("clear") || lower.contains("reset")) {
                        STATE.put("result", "0");
                        return "0";
                    }
                    STATE.put("lastAction", actionLabel);
                    return STATE.getOrDefault("result", "OK: " + actionLabel);
                }

                public static synchronized double evaluateArithmetic(String expression) {
                    if (expression == null || expression.trim().isEmpty()) return 0.0;
                    String clean = expression.replaceAll("[^0-9.+\\-*/]", "");
                    if (clean.isEmpty()) return 0.0;
                    String[] addParts = clean.split("\\+");
                    double sum = 0.0;
                    for (String part : addParts) {
                        if (part.isEmpty()) continue;
                        if (part.contains("*")) {
                            String[] mul = part.split("\\*");
                            double prod = 1.0;
                            for (String m : mul) {
                                if (!m.isEmpty()) prod *= Double.parseDouble(m);
                            }
                            sum += prod;
                        } else {
                            sum += Double.parseDouble(part);
                        }
                    }
                    STATE.put("result", String.valueOf(sum));
                    return sum;
                }
            }
        """.trimIndent()
    }
}
