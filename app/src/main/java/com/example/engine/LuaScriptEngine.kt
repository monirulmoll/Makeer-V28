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
    val openedUrl: String? = null,
    val exited: Boolean = false
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
        val trimmed = rawTitle.trim()
            .replace("◈", "")
            .trim()
            .ifEmpty { "PC PANEL" }
        return trimmed
    }

    fun stripLeadingEmojiForToast(label: String): String {
        val cleaned = label
            .replace(Regex("^[🟢🔴📝🔗🖼️🎚️✏️⚡🛡️🎯➕➖🙈❌\\s]+"), "")
            .trim()
        return cleaned.ifEmpty { label.trim().ifEmpty { "START" } }
    }

    fun normalizeGameGuardianLuaCode(rawCode: String): String {
        if (rawCode.isBlank()) return rawCode
        val ggApiRegex = Regex(
            "(?<![a-zA-Z0-9_.])(toast|alert|choice|multiChoice|prompt|sleep|setVisible|isVisible|showUiButton|hideUiButton|isClickedUiButton|searchNumber|getResults|editAll|clearResults|setRanges)\\s*\\("
        )
        return rawCode.lines().joinToString("\n") { line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("--")) {
                line
            } else {
                ggApiRegex.replace(line) { matchResult ->
                    "gg.${matchResult.groupValues[1]}("
                }
            }
        }
    }

    private fun extractIfStateBranch(rawCode: String, wantOnBranch: Boolean): String? {
        val lines = rawCode.lines()
        val firstNonEmpty = lines.indexOfFirst { it.trim().isNotEmpty() }
        if (firstNonEmpty < 0) return null
        val header = lines[firstNonEmpty].trim()
        if (!header.startsWith("if state") || !header.endsWith("then")) return null
        val elseIdx = lines.indexOfFirst { it.trim() == "else" }
        val endIdx = lines.indexOfLast { it.trim() == "end" }
        if (elseIdx <= firstNonEmpty || endIdx <= elseIdx) return null
        val slice = if (wantOnBranch) {
            lines.subList(firstNonEmpty + 1, elseIdx)
        } else {
            lines.subList(elseIdx + 1, endIdx)
        }
        return slice.joinToString("\n") { it.trim() }.trim().takeIf { it.isNotEmpty() }
    }

    fun defaultButtonLogicForWidget(
        label: String,
        type: String,
        linkUrl: String = "",
        customDefaultButtonLogic: String? = null
    ): String {
        val cleanToastText = stripLeadingEmojiForToast(label).replace("\"", "\\\"")
        if (type == ComponentWidgetType.TOGGLE.name) {
            return "gg.toast(\"$cleanToastText: ON\")"
        }
        if (!customDefaultButtonLogic.isNullOrBlank()) {
            return normalizeGameGuardianLuaCode(customDefaultButtonLogic.trim())
        }
        if (type == ComponentWidgetType.BUTTON.name) {
            return LuaCustomWidgetEngine.FALLBACK_DEFAULT_BUTTON_LOGIC
        }
        return "gg.toast(\"$cleanToastText: ON\")"
    }

    fun defaultOffLogicForWidget(label: String, type: String): String {
        val cleanToastText = stripLeadingEmojiForToast(label).replace("\"", "\\\"")
        return "gg.toast(\"$cleanToastText: OFF\")"
    }

    fun resolveWidgetButtonLogic(component: CanvasComponentEntity): String {
        val customSpec = LuaCustomWidgetEngine.parseCustomWidgetSpec(component)
        if (customSpec != null) {
            val evLogic = customSpec.primaryEventLogic().trim()
            if (evLogic.isNotEmpty()) {
                return normalizeGameGuardianLuaCode(evLogic)
            }
        }
        val raw = component.onPayloadHex.trim()
        val isLegacyPlaceholder = raw.isEmpty() ||
            raw.equals("On", ignoreCase = true) ||
            raw.equals("0x01", ignoreCase = true) ||
            raw.startsWith("/data/") ||
            raw.startsWith("/storage/") ||
            raw.startsWith("-- TextView display widget")
        if (isLegacyPlaceholder) {
            return defaultButtonLogicForWidget(component.label, component.type, component.linkUrl)
        }
        val extractedOn = extractIfStateBranch(raw, wantOnBranch = true)
        return normalizeGameGuardianLuaCode(extractedOn ?: raw)
    }

    fun resolveWidgetOffLogic(component: CanvasComponentEntity): String {
        val customSpec = LuaCustomWidgetEngine.parseCustomWidgetSpec(component)
        if (customSpec != null && customSpec.widgetType.equals("toggle", ignoreCase = true)) {
            val evOffLogic = customSpec.secondaryEventLogic().trim()
            if (evOffLogic.isNotEmpty()) {
                return normalizeGameGuardianLuaCode(evOffLogic)
            }
        }
        val rawOff = component.offPayloadHex.trim()
        val isLegacyOff = rawOff.isEmpty() ||
            rawOff.equals("Off", ignoreCase = true) ||
            rawOff.equals("0x00", ignoreCase = true) ||
            rawOff.startsWith("/data/") ||
            rawOff.startsWith("/storage/")
        if (!isLegacyOff) {
            val extractedOffFromOff = extractIfStateBranch(rawOff, wantOnBranch = false)
            return normalizeGameGuardianLuaCode(extractedOffFromOff ?: rawOff)
        }
        val extractedOffFromOn = extractIfStateBranch(component.onPayloadHex.trim(), wantOnBranch = false)
        if (extractedOffFromOn != null) {
            return normalizeGameGuardianLuaCode(extractedOffFromOn)
        }
        return defaultOffLogicForWidget(component.label, component.type)
    }

    fun generateLuaScript(
        project: StudioProjectEntity,
        components: List<CanvasComponentEntity>,
        preExecutionCode: String? = null
    ): String {
        val luaWidgets = components.filter { !isScreen1WidgetType(it.type) }
        val rawTitle = project.overlayTitle.ifBlank { project.name.ifBlank { "PC PANEL" } }
        val formattedHeader = formatLuaPanelHeaderTitle(rawTitle)
        val exitIdx = luaWidgets.size + 1

        val generated = buildString {
            val cleanPreCode = preExecutionCode?.trim().orEmpty()
            if (cleanPreCode.isNotEmpty()) {
                appendLine("-- =======================================================")
                appendLine("-- Lua Script Pre-Execution Code (Runs First Before Script)")
                appendLine("-- =======================================================")
                appendLine(normalizeGameGuardianLuaCode(cleanPreCode))
                appendLine()
            }
            appendLine("gg.setVisible(false)")
            appendLine("gg.showUiButton()")
            appendLine()
            appendLine("local running = true")
            if (luaWidgets.isNotEmpty()) {
                appendLine()
                luaWidgets.forEachIndexed { index, comp ->
                    val customSpec = LuaCustomWidgetEngine.parseCustomWidgetSpec(comp)
                    val initOn = comp.currentValue == "1" || comp.currentValue.equals("true", ignoreCase = true)
                    if (customSpec != null && customSpec.properties.isNotEmpty()) {
                        val propEntries = mutableListOf("state = ${if (initOn) "true" else "false"}")
                        propEntries.add("type = \"${escapeLuaString(customSpec.widgetType)}\"")
                        customSpec.properties.forEach { prop ->
                            val safeKey = prop.key.replace(Regex("[^A-Za-z0-9_]"), "_").ifEmpty { "value" }
                            val luaLiteral = when (prop.type) {
                                "boolean" -> if (prop.value.equals("true", ignoreCase = true) || prop.value == "1") "true" else "false"
                                "number", "slider" -> prop.value.toDoubleOrNull()?.toString() ?: "0"
                                else -> "\"${escapeLuaString(prop.value)}\""
                            }
                            propEntries.add("$safeKey = $luaLiteral")
                        }
                        appendLine("local switch_${index + 1} = { ${propEntries.joinToString(", ")} }")
                    } else {
                        appendLine("local switch_${index + 1} = { state = ${if (initOn) "true" else "false"} }")
                    }
                }
            }
            appendLine()
            appendLine("local function panel()")
            appendLine("    while running do")
            appendLine("        local c = gg.choice({")
            luaWidgets.forEachIndexed { index, comp ->
                val customSpec = LuaCustomWidgetEngine.parseCustomWidgetSpec(comp)
                val escapedLabel = escapeLuaString(comp.label)
                if (comp.type == ComponentWidgetType.TEXT.name || customSpec?.widgetType == "text_view") {
                    appendLine("            \"$escapedLabel\",")
                } else if (customSpec != null && customSpec.widgetType in setOf("path_input", "text_input", "number_input", "slider", "select")) {
                    val primaryProp = customSpec.properties.firstOrNull()
                    val propKey = primaryProp?.key?.replace(Regex("[^A-Za-z0-9_]"), "_") ?: "value"
                    appendLine("            \"$escapedLabel [\" .. tostring(switch_${index + 1}.$propKey) .. \"]\",")
                } else if (customSpec != null && customSpec.widgetType in setOf("button", "link")) {
                    appendLine("            \"[ ▶ ]  $escapedLabel\",")
                } else {
                    appendLine("            (switch_${index + 1}.state and \"[ 🟢 ON ]  \" or \"[ ⚪ OFF ] \") .. \"$escapedLabel\",")
                }
            }
            appendLine("            \"Exit\"")
            appendLine("        }, nil, \"${escapeLuaString(formattedHeader)}\")")
            appendLine()
            appendLine("        if c == nil then")
            appendLine("            break")
            luaWidgets.forEachIndexed { index, comp ->
                val choiceIdx = index + 1
                val customSpec = LuaCustomWidgetEngine.parseCustomWidgetSpec(comp)
                appendLine("        elseif c == $choiceIdx then")
                if (customSpec != null) {
                    // Bind custom widget properties as local variables for event logic
                    customSpec.properties.forEach { prop ->
                        val safeKey = prop.key.replace(Regex("[^A-Za-z0-9_]"), "_").ifEmpty { "value" }
                        appendLine("            local $safeKey = switch_$choiceIdx.$safeKey")
                    }
                }
                if (comp.type == ComponentWidgetType.TEXT.name || customSpec?.widgetType == "text_view") {
                    val logicLines = resolveWidgetButtonLogic(comp).lines()
                    logicLines.forEach { line ->
                        appendLine("            $line")
                    }
                } else if (customSpec != null && customSpec.widgetType in setOf("path_input", "text_input", "number_input", "slider")) {
                    val primaryProp = customSpec.properties.firstOrNull()
                    val propKey = primaryProp?.key?.replace(Regex("[^A-Za-z0-9_]"), "_") ?: "value"
                    val promptTitle = escapeLuaString(primaryProp?.label ?: comp.label)
                    val promptKind = when (customSpec.widgetType) {
                        "path_input" -> "file"
                        "number_input", "slider" -> "number"
                        else -> "text"
                    }
                    appendLine("            local input = gg.prompt({\"$promptTitle\"}, {switch_$choiceIdx.$propKey}, {\"$promptKind\"})")
                    appendLine("            if input ~= nil and input[1] ~= nil then")
                    appendLine("                switch_$choiceIdx.$propKey = input[1]")
                    appendLine("                $propKey = input[1]")
                    appendLine("                local value = input[1]")
                    val logicLines = resolveWidgetButtonLogic(comp).lines()
                    logicLines.forEach { line ->
                        appendLine("                $line")
                    }
                    appendLine("            end")
                } else if (customSpec != null && customSpec.widgetType in setOf("button", "link", "select")) {
                    val logicLines = resolveWidgetButtonLogic(comp).lines()
                    logicLines.forEach { line ->
                        appendLine("            $line")
                    }
                } else {
                    appendLine("            switch_$choiceIdx.state = not switch_$choiceIdx.state")
                    appendLine("            local state = switch_$choiceIdx.state")
                    val onLines = resolveWidgetButtonLogic(comp).lines()
                    val offLines = resolveWidgetOffLogic(comp).lines()
                    appendLine("            if state then")
                    onLines.forEach { line ->
                        appendLine("                $line")
                    }
                    appendLine("            else")
                    offLines.forEach { line ->
                        appendLine("                $line")
                    }
                    appendLine("            end")
                }
            }
            appendLine("        elseif c == $exitIdx then")
            appendLine("            running = false")
            appendLine("            gg.hideUiButton()")
            appendLine("            os.exit()")
            appendLine("            break")
            appendLine("        end")
            appendLine("    end")
            appendLine("end")
            appendLine()
            appendLine("panel()")
            appendLine()
            appendLine("while running do")
            appendLine("    if gg.isClickedUiButton() then")
            appendLine("        gg.setVisible(false)")
            appendLine("        panel()")
            appendLine("    elseif gg.isVisible(true) then")
            appendLine("        gg.setVisible(false)")
            appendLine("        panel()")
            appendLine("    end")
            appendLine()
            appendLine("    gg.sleep(100)")
            append("end")
        }
        return normalizeGameGuardianLuaCode(generated)
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
        return executeOverlayLuaLogic(label, type, rawLogic, null, linkUrl, state, value, text)
    }

    @JvmStatic
    fun executeOverlayLuaLogic(
        label: String,
        type: String,
        rawLogic: String?,
        rawOffLogic: String?,
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
            offPayloadHex = rawOffLogic.orEmpty(),
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
        val customSpec = LuaCustomWidgetEngine.parseCustomWidgetSpec(component)
        val isToggleWidget = if (customSpec != null) {
            customSpec.widgetType.equals("toggle", ignoreCase = true) || customSpec.widgetType.equals("switch", ignoreCase = true)
        } else {
            component.type == ComponentWidgetType.TOGGLE.name || component.type == ComponentWidgetType.BUTTON.name
        }
        val logic = if (isToggleWidget && !state) {
            resolveWidgetOffLogic(component)
        } else {
            resolveWidgetButtonLogic(component)
        }
        val logs = mutableListOf<String>()
        var lastToast: String? = null
        var lastAlert: String? = null
        var openedUrl: String? = null
        var exited = false

        val env = mutableMapOf<String, Any?>(
            "label" to component.label,
            "state" to state,
            "value" to value,
            "text" to text,
            "path" to component.targetFilePath.ifBlank { text },
            "url" to component.linkUrl.ifBlank { "https://google.com" }
        )
        customSpec?.properties?.forEach { prop ->
            val typedVal: Any = when (prop.type) {
                "boolean" -> prop.value.equals("true", ignoreCase = true) || prop.value == "1"
                "number", "slider" -> prop.value.toIntOrNull() ?: prop.value.toDoubleOrNull() ?: 0
                else -> prop.value
            }
            env[prop.key] = typedVal
        }

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
                line.startsWith("os.exit(") -> {
                    exited = true
                    logs.add("[Exit] Script terminated via os.exit()")
                    break
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
            openedUrl = openedUrl,
            exited = exited
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
        customScriptOverride: String? = null,
        preExecutionCode: String? = null
    ): LuaSaveResult {
        val fileName = scriptFileNameForProject(project)
        val preCode = preExecutionCode ?: LuaCustomWidgetEngine.getLuaPreExecutionCode(context)
        val rawScriptContent = customScriptOverride?.takeIf { it.isNotBlank() }
            ?: generateLuaScript(project, components, preCode)
        val scriptContent = normalizeGameGuardianLuaCode(rawScriptContent)
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
