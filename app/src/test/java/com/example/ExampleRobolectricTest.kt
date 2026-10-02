package com.example

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.CanvasComponentEntity
import com.example.engine.ConfigParameterSpec
import com.example.engine.LocalConfigStateWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context and verify default toggle size`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Studio Error", appName)

        val defaultToggle = CanvasComponentEntity(
            projectId = 1L,
            type = "TOGGLE",
            label = "Switch #1"
        )
        assertEquals(165, defaultToggle.widthDp)
        assertEquals(36, defaultToggle.heightDp)
    }

    @Test
    fun `verify custom component offset writer writes exact byte offset`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val writer = LocalConfigStateWriter.getInstance()
        val latch = CountDownLatch(1)
        val targetFile = File(context.filesDir, "custom_test_state.bin")
        if (targetFile.exists()) targetFile.delete()

        val listener = object : LocalConfigStateWriter.OnStateWriteListener {
            override fun onWriteSuccess(
                parameterKey: String,
                byteOffset: Int,
                previousValue: String,
                newValue: String,
                durationMicros: Long,
                updatedSnapshot: ConfigParameterSpec.StateSnapshot
            ) {
                if (parameterKey == "CustomButton1") {
                    latch.countDown()
                }
            }

            override fun onWriteError(parameterKey: String, errorMessage: String) {
                latch.countDown()
            }
        }
        writer.addListener(listener)
        writer.writeCustomComponentOffsetAsync(
            context.filesDir,
            targetFile.absolutePath,
            "0x10",
            "0x7F",
            "CustomButton1"
        )

        for (i in 0 until 50) {
            shadowOf(Looper.getMainLooper()).idle()
            if (latch.await(50, TimeUnit.MILLISECONDS)) break
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(latch.count == 0L)
        writer.removeListener(listener)

        RandomAccessFile(targetFile, "r").use { raf ->
            raf.seek(0x10)
            val byteVal = raf.readByte().toInt() and 0xFF
            assertEquals(0x7F, byteVal)
        }
    }

    @Test
    fun `verify blueprint generation and signed apk compilation engine`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val customBgImg = File(context.filesDir, "test_floating_bg.png").apply {
            val bmp = android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.parseColor("#0F172A"))
            java.io.FileOutputStream(this).use { out ->
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        val project = com.example.data.StudioProjectEntity(
            id = 1L,
            name = "My Floating Utility",
            overlayTitle = "Floating Mod Panel",
            canvasWidthDp = 216,
            canvasHeightDp = 290,
            canvasBgColorHex = "#CC0F172A",
            defaultTargetFilePath = File(context.filesDir, "overlay_state.bin").absolutePath,
            autoFixSize = true,
            canvasBgImagePath = customBgImg.absolutePath
        )
        val components = listOf(
            CanvasComponentEntity(
                id = 1L,
                projectId = 1L,
                type = "TOGGLE",
                label = "esp hack",
                posXDp = 10,
                posYDp = 10,
                widthDp = 196,
                heightDp = 44,
                byteOffsetHex = "0x04",
                onPayloadHex = "0x01",
                offPayloadHex = "0x00",
                targetFilePath = File(context.filesDir, "overlay_state.bin").absolutePath
            ),
            CanvasComponentEntity(
                id = 2L,
                projectId = 1L,
                type = "BUTTON",
                label = "teleport hack",
                posXDp = 10,
                posYDp = 62,
                widthDp = 176,
                heightDp = 44,
                byteOffsetHex = "0x08",
                onPayloadHex = "0xFF",
                offPayloadHex = "0x00",
                targetFilePath = File(context.filesDir, "overlay_state.bin").absolutePath
            )
        )

        val blueprintFiles = com.example.blueprint.ApkCompilationEngine.generateProjectBlueprintFiles(project, components)
        assertTrue(blueprintFiles.containsKey("AndroidManifest.xml"))
        assertTrue(blueprintFiles.containsKey("build.gradle"))
        assertTrue(blueprintFiles.containsKey("src/main/java/com/floating/modmenu/MainActivity.java"))
        assertTrue(blueprintFiles.containsKey("src/main/java/com/floating/modmenu/FloatingModMenuService.java"))
        assertTrue(blueprintFiles.containsKey("src/main/java/com/floating/modmenu/BinaryOffsetPatcher.java"))
        assertTrue(blueprintFiles.containsKey("src/main/res/layout/activity_main.xml"))
        assertTrue(blueprintFiles.containsKey("src/main/res/layout/inspector_dock.xml"))
        assertTrue(blueprintFiles.containsKey("src/main/res/layout/floating_view.xml"))

        val outApk = File(context.cacheDir, "test_compiled_signed.apk")
        if (outApk.exists()) outApk.delete()

        val result = com.example.blueprint.ApkCompilationEngine.compileAndSignProjectApk(
            context,
            project,
            components,
            outApk
        )
        assertTrue(result.signedApkFile.exists())
        assertTrue("Compiled APK must be > 1 MB, was ${result.apkSizeBytes}", result.apkSizeBytes > 1_000_000L)

        val verifier = com.android.apksig.ApkVerifier.Builder(result.signedApkFile)
            .setMinCheckedPlatformVersion(24)
            .build()
        val verifyResult = verifier.verify()
        assertTrue("APK signature verification failed: ${verifyResult.errors}", verifyResult.isVerified)
        assertTrue(verifyResult.isVerifiedUsingV2Scheme || verifyResult.isVerifiedUsingV3Scheme)

        java.util.zip.ZipFile(result.signedApkFile).use { zip ->
            val arsc = zip.getEntry("resources.arsc")
            val dex = zip.getEntry("classes.dex")
            val config = zip.getEntry("assets/overlay_config.json")
            val canvasBgEntry = zip.getEntry("assets/canvas_bg.png")
            val genService = zip.getEntry("assets/generated_project/src/main/java/com/floating/modmenu/FloatingModMenuService.java")
            val genPatcher = zip.getEntry("assets/generated_project/src/main/java/com/floating/modmenu/BinaryOffsetPatcher.java")
            assertTrue("resources.arsc must exist and be STORED (0)", arsc != null && arsc.method == java.util.zip.ZipEntry.STORED)
            assertTrue("classes.dex must exist in compiled APK", dex != null)
            assertTrue("assets/overlay_config.json must be injected in signed APK", config != null)
            assertTrue("assets/canvas_bg.png must be bundled in signed APK", canvasBgEntry != null)
            assertTrue("Generated FloatingModMenuService.java must be packaged in signed APK", genService != null)
            assertTrue("Generated BinaryOffsetPatcher.java must be packaged in signed APK", genPatcher != null)
        }

        // Verify XML layouts and Java blueprint views inflate cleanly
        val launcherView = com.example.blueprint.ProjectLauncherJavaView(context)
        launcherView.submitProjects(listOf(project))
        val canvasView = com.example.blueprint.EmptyCanvasWorkspaceView(context)
        canvasView.bindWorkspaceState(project, components, 1L)
        val inspectorView = com.example.blueprint.BottomPropertyInspectorView(context)
        var lastAutoSavedComponent: CanvasComponentEntity? = null
        inspectorView.setOnInspectorPropertyChangeListener(object : com.example.blueprint.BottomPropertyInspectorView.OnInspectorPropertyChangeListener {
            override fun onUpdateComponent(updatedComponent: CanvasComponentEntity) {
                lastAutoSavedComponent = updatedComponent
            }
            override fun onToggleAutoFixSize() {}
            override fun onDuplicateComponent(component: CanvasComponentEntity) {}
            override fun onDeleteComponent(component: CanvasComponentEntity) {}
            override fun onTestTriggerWrite(component: CanvasComponentEntity) {}
            override fun onCloseInspector() {}
        })
        inspectorView.bindComponent(components[0])

        // Verify real-time auto-save on text field change without any manual Save button click
        val etOnPayload = inspectorView.findViewById<android.widget.EditText>(R.id.et_on_payload_hex)
        val etOffPayload = inspectorView.findViewById<android.widget.EditText>(R.id.et_off_payload_hex)
        etOffPayload.setText("Off")
        etOnPayload.setText("On")
        assertEquals("Off", lastAutoSavedComponent?.offPayloadHex)
        assertEquals("On", lastAutoSavedComponent?.onPayloadHex)

        // Verify Python file text replacement (Off -> On and On -> Off)
        val pyFile = File(context.filesDir, "py.py")
        pyFile.writeText("status = Off\n")
        val writer = LocalConfigStateWriter.getInstance()
        val okOn = writer.applyWidgetPatchSync(
            context.filesDir,
            "widget_1",
            "TOGGLE",
            pyFile.absolutePath,
            "0x04",
            "Off",
            "On",
            "On",
            true,
            "Toggle #1"
        )
        assertTrue(okOn)
        assertTrue(pyFile.readText().contains("On"))

        val okOff = writer.applyWidgetPatchSync(
            context.filesDir,
            "widget_1",
            "TOGGLE",
            pyFile.absolutePath,
            "0x04",
            "Off",
            "On",
            "Off",
            false,
            "Toggle #1"
        )
        assertTrue(okOff)
        assertTrue(pyFile.readText().contains("Off"))

        // Verify Selected File Replace (single option ON) and Merge (multiple options ON) keeping same target file name
        val targetReplaceFile = File(context.filesDir, "game_target_config.cfg")
        targetReplaceFile.writeText("ORIGINAL_DEFAULT_DATA=0\n")

        val selectedFileA = File(context.filesDir, "aimbot_patch.cfg").apply {
            writeText("AIMBOT_ENABLED=1\nFOV=120")
        }
        val selectedFileB = File(context.filesDir, "norecoil_patch.cfg").apply {
            writeText("NORECOIL_ACTIVE=1\nSPREAD=0.0")
        }

        val spec1 = com.example.service.DynamicOverlayRegistry.OverlayItemSpec().apply {
            id = 101L
            type = "TOGGLE"
            label = selectedFileA.name
            customImagePath = selectedFileA.absolutePath
            targetFilePath = targetReplaceFile.absolutePath
            currentValue = "1"
        }
        val spec2 = com.example.service.DynamicOverlayRegistry.OverlayItemSpec().apply {
            id = 102L
            type = "SLIDER"
            label = selectedFileB.name
            customImagePath = selectedFileB.absolutePath
            targetFilePath = targetReplaceFile.absolutePath
            currentValue = "0"
        }
        com.example.service.DynamicOverlayRegistry.updateActiveOverlay(
            "Test Panel",
            "",
            216,
            290,
            "#FFFFFF",
            true,
            listOf(spec1, spec2)
        )

        // 1. Single option ON -> replaces target file with selectedFileA keeping target file name
        val okSingleReplace = writer.applyWidgetPatchSync(
            context.filesDir,
            "widget_101",
            "TOGGLE",
            targetReplaceFile.absolutePath,
            "0x04",
            "Off",
            "On",
            "1",
            true,
            selectedFileA.name,
            selectedFileA.absolutePath
        )
        assertTrue(okSingleReplace)
        assertEquals("game_target_config.cfg", targetReplaceFile.name)
        assertEquals("AIMBOT_ENABLED=1\nFOV=120", targetReplaceFile.readText())

        // 2. Multiple options ON (Toggle #101 + Slider #102 both active) -> original file removed & both selected files merged into targetReplaceFile
        val okMultiMerge = writer.applyWidgetPatchSync(
            context.filesDir,
            "widget_102",
            "SLIDER",
            targetReplaceFile.absolutePath,
            "0x08",
            "Off",
            "On",
            "75",
            true,
            selectedFileB.name,
            selectedFileB.absolutePath
        )
        assertTrue(okMultiMerge)
        assertEquals("game_target_config.cfg", targetReplaceFile.name)
        val mergedText = targetReplaceFile.readText()
        assertTrue("Original content must be removed", !mergedText.contains("ORIGINAL_DEFAULT_DATA"))
        assertTrue("First selected file must be in merged file", mergedText.contains("AIMBOT_ENABLED=1"))
        assertTrue("Second selected file must be in merged file", mergedText.contains("NORECOIL_ACTIVE=1"))

        // 3. Turn both options OFF -> restores original target file
        writer.applyWidgetPatchSync(
            context.filesDir,
            "widget_101",
            "TOGGLE",
            targetReplaceFile.absolutePath,
            "0x04",
            "Off",
            "On",
            "0",
            false,
            selectedFileA.name,
            selectedFileA.absolutePath
        )
        writer.applyWidgetPatchSync(
            context.filesDir,
            "widget_102",
            "SLIDER",
            targetReplaceFile.absolutePath,
            "0x08",
            "Off",
            "On",
            "0",
            false,
            selectedFileB.name,
            selectedFileB.absolutePath
        )
        assertEquals("ORIGINAL_DEFAULT_DATA=0\n", targetReplaceFile.readText())
    }

    @Test
    fun `verify LuaScriptEngine generates gg choice menu and executes button logic`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val luaProject = com.example.data.StudioProjectEntity(
            id = 2L,
            name = "PC Panel Script",
            packageName = "lua.script.pcpanel",
            overlayTitle = "PC PANEL"
        )
        val startComp = CanvasComponentEntity(
            id = 10L,
            projectId = 2L,
            type = "BUTTON",
            label = "🟢  START",
            onPayloadHex = com.example.engine.LuaScriptEngine.defaultButtonLogicForWidget("🟢  START", "BUTTON")
        )
        val stopComp = CanvasComponentEntity(
            id = 11L,
            projectId = 2L,
            type = "TOGGLE",
            label = "🔴  STOP",
            onPayloadHex = com.example.engine.LuaScriptEngine.defaultButtonLogicForWidget("🔴  STOP", "TOGGLE")
        )

        val script = com.example.engine.LuaScriptEngine.generateLuaScript(luaProject, listOf(startComp, stopComp))
        assertTrue(script.contains("local c = gg.choice({"))
        assertTrue(script.contains("\"🟢  START\","))
        assertTrue(script.contains("\"🔴  STOP\","))
        assertTrue(script.contains("\"➖  MINIMIZE\","))
        assertTrue(script.contains("\"🙈  HIDE\","))
        assertTrue(script.contains("\"❌  KILL\""))
        assertTrue(script.contains("gg.toast(\"START\")"))
        assertTrue(script.contains("gg.toast(\"STOP\")"))
        assertTrue(script.contains("gg.showUiButton()"))

        val execResult = com.example.engine.LuaScriptEngine.executeWidgetLuaLogic(startComp, true, 0, "")
        assertEquals("START", execResult.toastMessage)

        val saveResult = com.example.engine.LuaScriptEngine.saveLuaScriptToDownloads(
            context,
            luaProject,
            listOf(startComp, stopComp)
        )
        assertTrue(saveResult.localFile.exists())
        assertEquals("pc_panel_script.lua", saveResult.fileName)
    }
}
