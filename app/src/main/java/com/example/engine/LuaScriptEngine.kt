package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.CanvasComponentEntity
import com.example.data.ComponentWidgetType
import com.example.data.StudioProjectEntity
import com.example.data.isScreen1WidgetType
import java.io.File
import java.util.Locale

data class LuaExecutionResult(
    val logs: List<String>,
    val toastMessage: String? = null,
    val alertMessage: String? = null,
    val openedUrl: String? = null
)

data class LuaSaveResult(
    val fileName: String,
    val publicDisplayPath: String,
    val localFile: File,
    val sizeBytes: Long,
    val scriptContent: String
)

object LuaScriptEngine {

    fun scriptFileNameForProject(project: StudioProjectEntity): String {
        val rawName = project.name.ifBlank { project.overlayTitle.ifBlank { "script" } }
        val safeSlug = rawName.trim().lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifEmpty { "script" }
        return if (safeSlug.endsWith(".lua")) safeSlug else "$safeSlug.lua"
    }

    fun defaultLuaPathForProject(projectName: String): String {
        val safeSlug = projectName.trim().lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifEmpty { "custom_script" }
        return "/storage/emulated/0/Download/lua/${safeSlug}.lua"
    }

    fun formatLuaPanelHeaderTitle(rawTitle: String): String {
        val trimmed = rawTitle.trim().ifEmpty { "PC PANEL" }
        return if (trimmed.contains("◈")) {
            trimmed
        } else {
            "◈  ${trimmed.uppercase(Locale.US)}  ◈"
        }
    }

    fun stripLeadingEmojiForToast(label: String): String {
        val cleaned = label
            .replace(Regex("^[🟢🔴📝🔗🖼️🎚️✏️⚡🛡️🎯➕➖🙈❌\\s]+"), "")
            .trim()
        return cleaned.ifEmpty { label.trim().ifEmpty { "START" } }
    }

    fun defaultButtonLogicForWidget(label: String, type: String, linkUrl: String = ""): String {
        val cleanToastText = stripLeadingEmojiForToast(label).replace("\"", "\\\"")
        return "gg.toast(\"$cleanToastText\")"
    }

    fun resolveWidgetButtonLogic(component: CanvasComponentEntity): String {
        val raw = component.onPayloadHex.trim()
        val isLegacyPlaceholder = raw.isEmpty() ||
            raw.equals("On", ignoreCase = true) ||
            raw.equals("0x01", ignoreCase = true) ||
            raw.startsWith("/data/") ||
            raw.startsWith("/storage/") ||
            raw.startsWith("-- TextView display widget")
        return if (isLegacyPlaceholder) {
            defaultButtonLogicForWidget(component.label, component.type, component.linkUrl)
        } else {
            raw
        }
    }

    fun generateLuaScript(
        project: StudioProjectEntity,
        components: List<CanvasComponentEntity>
    ): String {
        val luaWidgets = components.filter { !isScreen1WidgetType(it.type) }
        val rawTitle = project.overlayTitle.ifBlank { project.name.ifBlank { "PC PANEL" } }
        val formattedHeader = formatLuaPanelHeaderTitle(rawTitle)

        return buildString {
            appendLine("gg.setVisible(false)")
            appendLine()
            appendLine("local running = true")
            appendLine("local hidden = false")
            appendLine()
            appendLine("local function panel()")
            appendLine("    if not running then return end")
            appendLine()
            appendLine("    local c = gg.choice({")
            luaWidgets.forEach { comp ->
                appendLine("        \"${escapeLuaString(comp.label)}\",")
            }
            appendLine("        \"➖  MINIMIZE\",")
            appendLine("        \"🙈  HIDE\",")
            appendLine("        \"❌  KILL\"")
            appendLine("    }, nil, \"${escapeLuaString(formattedHeader)}\")")
            appendLine()

            val minIdx = luaWidgets.size + 1
            val hideIdx = luaWidgets.size + 2
            val killIdx = luaWidgets.size + 3

            if (luaWidgets.isNotEmpty()) {
                luaWidgets.forEachIndexed { index, comp ->
                    val choiceIdx = index + 1
                    val keyword = if (index == 0) "if" else "elseif"
                    appendLine("    $keyword c == $choiceIdx then")
                    val logicLines = resolveWidgetButtonLogic(comp).lines()
                    logicLines.forEach { line ->
                        appendLine("        $line")
                    }
                }
                appendLine("    elseif c == $minIdx then")
            } else {
                appendLine("    if c == $minIdx then")
            }
            appendLine("        hidden = true")
            appendLine("        gg.setVisible(false)")
            appendLine("    elseif c == $hideIdx then")
            appendLine("        hidden = true")
            appendLine("        gg.setVisible(false)")
            appendLine("    elseif c == $killIdx then")
            appendLine("        running = false")
            appendLine("        os.exit()")
            appendLine("    end")
            appendLine("end")
            appendLine()
            appendLine("gg.showUiButton()")
            appendLine()
            appendLine("while running do")
            appendLine("    if gg.isClickedUiButton() then")
            appendLine("        hidden = false")
            appendLine("        panel()")
            appendLine("    end")
            appendLine()
            appendLine("    if not hidden and gg.isVisible(true) then")
            appendLine("        gg.setVisible(false)")
            appendLine("        panel()")
            appendLine("    end")
            appendLine()
            appendLine("    gg.sleep(100)")
            append("end")
        }
    }

    @JvmStatic
    fun executeOverlayLuaLogic(
        label: String,
        type: String,
        rawLogic: String?,
        linkUrl: String?,
        state: Boolean,
        value: Int,
        text: String
    ): LuaExecutionResult {
        val tempComp = CanvasComponentEntity(
            projectId = 0L,
            type = type,
            label = label,
            posXDp = 0,
            posYDp = 0,
            widthDp = 100,
            heightDp = 40,
            bgColorHex = "#2563EB",
            textColorHex = "#FFFFFF",
            customImagePath = "",
            soundTrigger = "NONE",
            customSoundPath = "",
            targetFilePath = "",
            byteOffsetHex = "0x00",
            onPayloadHex = rawLogic.orEmpty(),
            offPayloadHex = "",
            sliderMax = 100,
            currentValue = text,
            linkUrl = linkUrl.orEmpty()
        )
        return executeWidgetLuaLogic(tempComp, state, value, text)
    }

    fun executeWidgetLuaLogic(
        component: CanvasComponentEntity,
        state: Boolean,
        value: Int,
        text: String
    ): LuaExecutionResult {
        val logic = resolveWidgetButtonLogic(component)
        val logs = mutableListOf<String>()
        var lastToast: String? = null
        var lastAlert: String? = null
        var openedUrl: String? = null

        val env = mutableMapOf<String, Any?>(
            "label" to component.label,
            "state" to state,
            "value" to value,
            "text" to text,
            "url" to component.linkUrl.ifBlank { "https://google.com" }
        )

        val lines = logic.lines().map { it.trim() }
        var i = 0
        var activeBranch = true
        var branchTakenInCurrentIf = false
        var insideIfBlock = false

        while (i < lines.size) {
            val rawLine = lines[i]
            i++
            if (rawLine.isEmpty() || rawLine.startsWith("--")) continue
            val line = rawLine.substringBefore("--").trim()
            if (line.isEmpty()) continue

            if (line.startsWith("if ") && line.endsWith(" then")) {
                insideIfBlock = true
                val condExpr = line.removePrefix("if ").removeSuffix(" then").trim()
                val condResult = evaluateCondition(condExpr, env)
                activeBranch = condResult
                branchTakenInCurrentIf = condResult
                continue
            }
            if (line.startsWith("elseif ") && line.endsWith(" then")) {
                if (!insideIfBlock) continue
                if (branchTakenInCurrentIf) {
                    activeBranch = false
                } else {
                    val condExpr = line.removePrefix("elseif ").removeSuffix(" then").trim()
                    val condResult = evaluateCondition(condExpr, env)
                    activeBranch = condResult
                    if (condResult) branchTakenInCurrentIf = true
                }
                continue
            }
            if (line == "else") {
                if (insideIfBlock) {
                    activeBranch = !branchTakenInCurrentIf
                    branchTakenInCurrentIf = true
                }
                continue
            }
            if (line == "end") {
                insideIfBlock = false
                activeBranch = true
                branchTakenInCurrentIf = false
                continue
            }

            if (!activeBranch) continue

            when {
                line.startsWith("local ") && line.contains("=") -> {
                    val afterLocal = line.removePrefix("local ").trim()
                    val varName = afterLocal.substringBefore("=").trim()
                    val expr = afterLocal.substringAfter("=").trim()
                    env[varName] = evaluateExpression(expr, env)
                }
                line.startsWith("print(") && line.endsWith(")") -> {
                    val inner = line.removePrefix("print(").removeSuffix(")").trim()
                    val msg = evaluateExpression(inner, env)?.toString().orEmpty()
                    logs.add(msg)
                }
                line.startsWith("gg.toast(") && line.endsWith(")") -> {
                    val inner = line.removePrefix("gg.toast(").removeSuffix(")").trim()
                    val msg = evaluateExpression(inner, env)?.toString().orEmpty()
                    lastToast = msg
                    logs.add("[Toast] $msg")
                }
                line.startsWith("toast(") && line.endsWith(")") -> {
                    val inner = line.removePrefix("toast(").removeSuffix(")").trim()
                    val msg = evaluateExpression(inner, env)?.toString().orEmpty()
                    lastToast = msg
                    logs.add("[Toast] $msg")
                }
                line.startsWith("gg.alert(") && line.endsWith(")") -> {
                    val inner = line.removePrefix("gg.alert(").removeSuffix(")").trim()
                    val msg = evaluateExpression(inner, env)?.toString().orEmpty()
                    lastAlert = msg
                    logs.add("[Alert] $msg")
                }
                line.startsWith("alert(") && line.endsWith(")") -> {
                    val inner = line.removePrefix("alert(").removeSuffix(")").trim()
                    val msg = evaluateExpression(inner, env)?.toString().orEmpty()
                    lastAlert = msg
                    logs.add("[Alert] $msg")
                }
                line.startsWith("openLink(") && line.endsWith(")") -> {
                    val inner = line.removePrefix("openLink(").removeSuffix(")").trim()
                    val targetUrl = evaluateExpression(inner, env)?.toString().orEmpty()
                    openedUrl = targetUrl
                    logs.add("[OpenLink] $targetUrl")
                }
                line.contains("=") && !line.contains("==") && !line.contains("~=") -> {
                    val varName = line.substringBefore("=").trim()
                    val expr = line.substringAfter("=").trim()
                    env[varName] = evaluateExpression(expr, env)
                }
                else -> {
                    logs.add("Executed: $line")
                }
            }
        }

        if (logs.isEmpty() && lastToast == null && lastAlert == null) {
            logs.add("[${component.label}] Executed button click logic.")
        }

        return LuaExecutionResult(
            logs = logs,
            toastMessage = lastToast,
            alertMessage = lastAlert,
            openedUrl = openedUrl
        )
    }

    private fun evaluateCondition(expr: String, env: Map<String, Any?>): Boolean {
        val clean = expr.trim()
        if (clean.startsWith("not ")) {
            return !evaluateCondition(clean.removePrefix("not ").trim(), env)
        }
        if (clean.contains("==")) {
            val left = evaluateExpression(clean.substringBefore("==").trim(), env)
            val right = evaluateExpression(clean.substringAfter("==").trim(), env)
            return left?.toString() == right?.toString()
        }
        if (clean.contains("~=")) {
            val left = evaluateExpression(clean.substringBefore("~=").trim(), env)
            val right = evaluateExpression(clean.substringAfter("~=").trim(), env)
            return left?.toString() != right?.toString()
        }
        if (clean.contains(">=")) {
            val left = evaluateExpression(clean.substringBefore(">=").trim(), env)?.toString()?.toDoubleOrNull() ?: 0.0
            val right = evaluateExpression(clean.substringAfter(">=").trim(), env)?.toString()?.toDoubleOrNull() ?: 0.0
            return left >= right
        }
        if (clean.contains("<=")) {
            val left = evaluateExpression(clean.substringBefore("<=").trim(), env)?.toString()?.toDoubleOrNull() ?: 0.0
            val right = evaluateExpression(clean.substringAfter("<=").trim(), env)?.toString()?.toDoubleOrNull() ?: 0.0
            return left <= right
        }
        if (clean.contains(">")) {
            val left = evaluateExpression(clean.substringBefore(">").trim(), env)?.toString()?.toDoubleOrNull() ?: 0.0
            val right = evaluateExpression(clean.substringAfter(">").trim(), env)?.toString()?.toDoubleOrNull() ?: 0.0
            return left > right
        }
        if (clean.contains("<")) {
            val left = evaluateExpression(clean.substringBefore("<").trim(), env)?.toString()?.toDoubleOrNull() ?: 0.0
            val right = evaluateExpression(clean.substringAfter("<").trim(), env)?.toString()?.toDoubleOrNull() ?: 0.0
            return left < right
        }
        val evaluated = evaluateExpression(clean, env)
        return when (evaluated) {
            is Boolean -> evaluated
            null -> false
            else -> evaluated.toString() != "false" && evaluated.toString() != "nil" && evaluated.toString().isNotEmpty()
        }
    }

    private fun evaluateExpression(expr: String, env: Map<String, Any?>): Any? {
        val trimmed = expr.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.contains("..")) {
            val parts = splitConcatParts(trimmed)
            if (parts.size > 1) {
                return parts.joinToString("") { part ->
                    evaluateExpression(part, env)?.toString().orEmpty()
                }
            }
        }
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length >= 2)
        ) {
            return trimmed.substring(1, trimmed.length - 1).replace("\\\"", "\"")
        }
        if (trimmed == "true") return true
        if (trimmed == "false") return false
        if (trimmed == "nil") return null
        trimmed.toIntOrNull()?.let { return it }
        trimmed.toDoubleOrNull()?.let { return it }
        if (trimmed.startsWith("tostring(") && trimmed.endsWith(")")) {
            val inner = trimmed.removePrefix("tostring(").removeSuffix(")").trim()
            return evaluateExpression(inner, env)?.toString() ?: "nil"
        }
        if (trimmed.startsWith("tonumber(") && trimmed.endsWith(")")) {
            val inner = trimmed.removePrefix("tonumber(").removeSuffix(")").trim()
            return evaluateExpression(inner, env)?.toString()?.toDoubleOrNull() ?: 0
        }
        if (env.containsKey(trimmed)) {
            return env[trimmed]
        }
        return trimmed
    }

    private fun splitConcatParts(expr: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var quoteChar = '"'
        var idx = 0
        while (idx < expr.length) {
            val c = expr[idx]
            if ((c == '"' || c == '\'') && (idx == 0 || expr[idx - 1] != '\\')) {
                if (!inQuotes) {
                    inQuotes = true
                    quoteChar = c
                } else if (c == quoteChar) {
                    inQuotes = false
                }
                current.append(c)
                idx++
            } else if (!inQuotes && idx + 1 < expr.length && expr[idx] == '.' && expr[idx + 1] == '.') {
                result.add(current.toString().trim())
                current.setLength(0)
                idx += 2
            } else {
                current.append(c)
                idx++
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString().trim())
        }
        return result
    }

    private fun escapeLuaString(raw: String): String {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }

    fun saveLuaScriptToDownloads(
        context: Context,
        project: StudioProjectEntity,
        components: List<CanvasComponentEntity>,
        customScriptOverride: String? = null
    ): LuaSaveResult {
        val fileName = scriptFileNameForProject(project)
        val scriptContent = customScriptOverride?.takeIf { it.isNotBlank() }
            ?: generateLuaScript(project, components)
        val scriptBytes = scriptContent.toByteArray(Charsets.UTF_8)

        val localLuaDir = File(context.filesDir, "saved_lua_scripts").apply { mkdirs() }
        val localFile = File(localLuaDir, fileName)
        localFile.writeBytes(scriptBytes)

        var publicDisplayPath = "/storage/emulated/0/Download/lua/$fileName"
        var wroteDirect = false

        try {
            val pubDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val directDirs = listOfNotNull(
                pubDownloads?.let { File(it, "lua") },
                File("/storage/emulated/0/Download/lua")
            ).distinctBy { it.absolutePath }

            for (dir in directDirs) {
                try {
                    if (!dir.exists()) dir.mkdirs()
                    if (dir.exists() && dir.canWrite()) {
                        val outFile = File(dir, fileName)
                        outFile.writeBytes(scriptBytes)
                        publicDisplayPath = outFile.absolutePath
                        wroteDirect = true
                    }
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }

        if (!wroteDirect && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                try {
                    resolver.delete(
                        collection,
                        "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                        arrayOf(fileName)
                    )
                } catch (_: Exception) {
                }
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/lua")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val itemUri = resolver.insert(collection, values)
                if (itemUri != null) {
                    resolver.openOutputStream(itemUri)?.use { out ->
                        out.write(scriptBytes)
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(itemUri, values, null, null)
                    publicDisplayPath = "/storage/emulated/0/Download/lua/$fileName"
                }
            } catch (_: Exception) {
            }
        }

        return LuaSaveResult(
            fileName = fileName,
            publicDisplayPath = publicDisplayPath,
            localFile = localFile,
            sizeBytes = scriptBytes.size.toLong(),
            scriptContent = scriptContent
        )
    }
}
