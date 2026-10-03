package com.example.engine

import android.content.Context
import com.example.data.CanvasComponentEntity
import com.example.data.ComponentWidgetType
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class LuaCustomWidgetProperty(
    val key: String,
    val label: String,
    val type: String, // "text", "number", "boolean", "path", "file", "color", "slider", "select"
    val value: String,
    val min: Float = 0f,
    val max: Float = 100f,
    val options: List<String> = emptyList()
)

data class LuaCustomWidgetEvent(
    val key: String,   // "onClick", "onToggleOn", "onToggleOff", "onChange", "onSubmit"
    val title: String, // "On Button Click Logic", "On Change / Toggle Logic", "On Change / On Submit Logic", etc.
    val logic: String
)

data class LuaCustomWidgetSpec(
    val widgetId: String,
    val widgetName: String,
    val widgetType: String, // "button", "toggle", "path_input", "text_input", "number_input", "slider", "select", "link", "text_view"
    val rawCode: String,
    val properties: List<LuaCustomWidgetProperty>,
    val events: List<LuaCustomWidgetEvent>
) {
    fun primaryEventLogic(): String {
        return events.firstOrNull()?.logic.orEmpty()
    }

    fun secondaryEventLogic(): String {
        return events.getOrNull(1)?.logic.orEmpty()
    }

    fun displayTypeBadge(): String {
        return when (widgetType.lowercase(Locale.US)) {
            "button" -> "Button"
            "toggle", "switch" -> "Toggle Switch"
            "path_input", "path", "file", "file_input" -> "Path Input"
            "text_input", "input" -> "Text Input"
            "number_input", "number" -> "Number Input"
            "slider" -> "Slider"
            "select", "dropdown" -> "Select / Option"
            "link" -> "Link Opener"
            "text_view", "label", "text" -> "Text View"
            else -> widgetType.replaceFirstChar { it.uppercase() }
        }
    }

    fun mapToComponentWidgetType(): ComponentWidgetType {
        return when (widgetType.lowercase(Locale.US)) {
            "button" -> ComponentWidgetType.BUTTON
            "toggle", "switch" -> ComponentWidgetType.TOGGLE
            "slider" -> ComponentWidgetType.SLIDER
            "text_view", "label" -> ComponentWidgetType.TEXT
            "link" -> ComponentWidgetType.LINK
            "path_input", "path", "file", "file_input",
            "text_input", "input", "number_input", "number", "select" -> ComponentWidgetType.INPUT
            else -> ComponentWidgetType.BUTTON
        }
    }
}

object LuaCustomWidgetEngine {

    const val CUSTOM_WIDGET_PREFIX = "LUA_CUSTOM_WIDGET:"
    private const val PREFS_NAME = "lua_script_studio_prefs"
    private const val KEY_DEFAULT_BUTTON_LOGIC = "lua_default_button_logic"
    private const val KEY_CUSTOM_WIDGET_TEMPLATES = "lua_custom_widget_templates_v1"
    private const val KEY_LUA_PRE_EXECUTION_CODE = "lua_pre_execution_code"

    const val FALLBACK_DEFAULT_BUTTON_LOGIC = "gg.toast(\"Button clicked\")"

    fun getLuaPreExecutionCode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LUA_PRE_EXECUTION_CODE, null)
        return if (raw.isNullOrBlank()) "" else LuaScriptEngine.normalizeGameGuardianLuaCode(raw.trim())
    }

    fun setLuaPreExecutionCode(context: Context, code: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val trimmed = code.trim()
        val normalized = if (trimmed.isEmpty()) "" else LuaScriptEngine.normalizeGameGuardianLuaCode(trimmed)
        prefs.edit().putString(KEY_LUA_PRE_EXECUTION_CODE, normalized).apply()
    }

    fun getDefaultButtonLogic(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DEFAULT_BUTTON_LOGIC, null)
        return if (raw.isNullOrBlank()) {
            FALLBACK_DEFAULT_BUTTON_LOGIC
        } else {
            LuaScriptEngine.normalizeGameGuardianLuaCode(raw.trim())
        }
    }

    fun hasUserConfiguredDefaultButtonLogic(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getString(KEY_DEFAULT_BUTTON_LOGIC, null).isNullOrBlank()
    }

    fun setDefaultButtonLogic(context: Context, logic: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val normalized = LuaScriptEngine.normalizeGameGuardianLuaCode(logic.trim())
            .ifBlank { FALLBACK_DEFAULT_BUTTON_LOGIC }
        prefs.edit().putString(KEY_DEFAULT_BUTTON_LOGIC, normalized).apply()
    }

    fun isCustomWidget(component: CanvasComponentEntity): Boolean {
        return component.byteOffsetHex.startsWith(CUSTOM_WIDGET_PREFIX)
    }

    fun parseCustomWidgetSpec(component: CanvasComponentEntity): LuaCustomWidgetSpec? {
        if (!isCustomWidget(component)) return null
        val jsonStr = component.byteOffsetHex.removePrefix(CUSTOM_WIDGET_PREFIX).trim()
        if (jsonStr.isEmpty()) return null
        return try {
            val obj = JSONObject(jsonStr)
            val spec = specFromJson(obj)
            // Keep label and primary/secondary event logic synced with latest component columns
            val syncedEvents = spec.events.mapIndexed { idx, ev ->
                when (idx) {
                    0 -> ev.copy(logic = component.onPayloadHex.ifBlank { ev.logic })
                    1 -> ev.copy(logic = component.offPayloadHex.ifBlank { ev.logic })
                    else -> ev
                }
            }
            spec.copy(
                widgetName = component.label.ifBlank { spec.widgetName },
                events = syncedEvents
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun encodeCustomWidgetSpec(spec: LuaCustomWidgetSpec): String {
        return CUSTOM_WIDGET_PREFIX + specToJson(spec).toString()
    }

    fun applySpecToComponent(
        component: CanvasComponentEntity,
        spec: LuaCustomWidgetSpec
    ): CanvasComponentEntity {
        val primaryLogic = LuaScriptEngine.normalizeGameGuardianLuaCode(
            spec.primaryEventLogic().ifBlank {
                LuaScriptEngine.defaultButtonLogicForWidget(spec.widgetName, component.type, component.linkUrl)
            }
        )
        val secondaryLogic = LuaScriptEngine.normalizeGameGuardianLuaCode(
            spec.secondaryEventLogic().ifBlank {
                LuaScriptEngine.defaultOffLogicForWidget(spec.widgetName, component.type)
            }
        )
        val pathProp = spec.properties.firstOrNull { it.type == "path" || it.type == "file" }
        val urlProp = spec.properties.firstOrNull { it.key.equals("url", ignoreCase = true) || it.key.equals("link", ignoreCase = true) }
        val colorProp = spec.properties.firstOrNull { it.type == "color" }
        val sliderProp = spec.properties.firstOrNull { it.type == "slider" }
        val boolProp = spec.properties.firstOrNull { it.type == "boolean" }
        val primaryVal = when (spec.widgetType.lowercase(Locale.US)) {
            "toggle", "switch" -> {
                if (boolProp != null) {
                    if (boolProp.value.equals("true", ignoreCase = true) || boolProp.value == "1") "1" else "0"
                } else {
                    component.currentValue
                }
            }
            "path_input", "path", "file" -> pathProp?.value ?: component.currentValue
            "slider", "number_input", "number" -> {
                (sliderProp ?: spec.properties.firstOrNull { it.type == "number" })?.value ?: component.currentValue
            }
            "text_input", "input", "select" -> {
                spec.properties.firstOrNull()?.value ?: component.currentValue
            }
            else -> component.currentValue
        }

        val normalizedSpec = spec.copy(
            events = spec.events.mapIndexed { index, ev ->
                when (index) {
                    0 -> ev.copy(logic = primaryLogic)
                    1 -> ev.copy(logic = secondaryLogic)
                    else -> ev.copy(logic = LuaScriptEngine.normalizeGameGuardianLuaCode(ev.logic))
                }
            }
        )

        return component.copy(
            label = normalizedSpec.widgetName.ifBlank { component.label },
            type = normalizedSpec.mapToComponentWidgetType().name,
            byteOffsetHex = encodeCustomWidgetSpec(normalizedSpec),
            onPayloadHex = primaryLogic,
            offPayloadHex = secondaryLogic,
            targetFilePath = pathProp?.value?.takeIf { it.isNotBlank() } ?: component.targetFilePath,
            linkUrl = urlProp?.value?.takeIf { it.isNotBlank() } ?: component.linkUrl,
            bgColorHex = colorProp?.value?.takeIf { it.startsWith("#") } ?: component.bgColorHex,
            sliderMax = sliderProp?.max?.toInt()?.coerceAtLeast(1) ?: component.sliderMax,
            currentValue = primaryVal
        )
    }

    fun getSavedCustomWidgetTemplates(context: Context): List<LuaCustomWidgetSpec> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CUSTOM_WIDGET_TEMPLATES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<LuaCustomWidgetSpec>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                list.add(specFromJson(item))
            }
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun saveCustomWidgetTemplate(context: Context, spec: LuaCustomWidgetSpec) {
        val current = getSavedCustomWidgetTemplates(context).toMutableList()
        val existingIdx = current.indexOfFirst {
            it.widgetId == spec.widgetId || it.widgetName.equals(spec.widgetName, ignoreCase = true)
        }
        if (existingIdx >= 0) {
            current[existingIdx] = spec
        } else {
            current.add(spec)
        }
        val arr = JSONArray()
        current.forEach { arr.put(specToJson(it)) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_WIDGET_TEMPLATES, arr.toString())
            .apply()
    }

    fun deleteCustomWidgetTemplate(context: Context, widgetId: String) {
        val current = getSavedCustomWidgetTemplates(context)
            .filterNot { it.widgetId == widgetId }
        val arr = JSONArray()
        current.forEach { arr.put(specToJson(it)) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_WIDGET_TEMPLATES, arr.toString())
            .apply()
    }

    /**
     * Analyzes user-written Lua/widget definition code to automatically detect:
     * 1. Widget Type ("button", "toggle", "path_input", "text_input", "number_input", "slider", "select", "link", "text_view")
     * 2. Editable Properties (text, number, boolean, path, file, color, slider, select)
     * 3. Events & Initial Event Logic (preserving user code or populating Default Button Logic template if unspecified)
     */
    fun analyzeWidgetCode(
        rawCode: String,
        defaultButtonLogic: String = FALLBACK_DEFAULT_BUTTON_LOGIC
    ): LuaCustomWidgetSpec {
        val trimmedCode = rawCode.trim()
        val lines = trimmedCode.lines()

        var explicitType: String? = null
        var explicitName: String? = null
        var explicitLabel: String? = null
        var minVal: Float? = null
        var maxVal: Float? = null
        val parsedOptions = mutableListOf<String>()
        val rawAssignments = mutableListOf<Pair<String, String>>()
        val executableLines = mutableListOf<String>()
        val functionBlocks = mutableMapOf<String, MutableList<String>>()
        var currentFuncName: String? = null

        val keyValRegex = Regex("""^(?:local\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.+)$""")
        val funcStartRegex = Regex("""^(?:local\s+)?function\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(.*\)""")
        val anonFuncRegex = Regex("""^(?:local\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*function\s*\(.*\)""")
        val commentAnnotationRegex = Regex("""^--\s*@([A-Za-z_]+)\s+(.+)$""")

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val annoMatch = commentAnnotationRegex.find(line)
            if (annoMatch != null) {
                val annoKey = annoMatch.groupValues[1].lowercase(Locale.US)
                val annoVal = unquote(annoMatch.groupValues[2].trim())
                when (annoKey) {
                    "type", "widget" -> explicitType = normalizeTypeKeyword(annoVal)
                    "name", "title" -> explicitName = annoVal
                    "label" -> explicitLabel = annoVal
                }
                continue
            }

            if (line.startsWith("--")) continue

            val cleanLine = line.substringBefore("--").trim().removeSuffix(",")
            if (cleanLine.isEmpty()) continue

            val fStart = funcStartRegex.find(cleanLine) ?: anonFuncRegex.find(cleanLine)
            if (fStart != null) {
                currentFuncName = fStart.groupValues[1]
                functionBlocks.putIfAbsent(currentFuncName!!, mutableListOf())
                continue
            }

            if (currentFuncName != null) {
                if (cleanLine == "end") {
                    currentFuncName = null
                } else {
                    functionBlocks[currentFuncName]?.add(cleanLine)
                }
                continue
            }

            // Check for options table like options = {"A", "B", "C"}
            if (cleanLine.contains("{") && cleanLine.contains("}")) {
                val kv = keyValRegex.find(cleanLine)
                if (kv != null) {
                    val k = kv.groupValues[1].trim()
                    val v = kv.groupValues[2].trim()
                    if (k.lowercase(Locale.US) in setOf("options", "choices", "items", "list")) {
                        val inside = v.substringAfter("{").substringBeforeLast("}")
                        inside.split(",").map { unquote(it.trim()) }.filter { it.isNotEmpty() }.forEach {
                            parsedOptions.add(it)
                        }
                        continue
                    }
                }
            }

            val kvMatch = keyValRegex.find(cleanLine)
            if (kvMatch != null) {
                val key = kvMatch.groupValues[1].trim()
                val valueExpr = kvMatch.groupValues[2].trim()
                val keyLower = key.lowercase(Locale.US)
                val unquotedVal = unquote(valueExpr)

                when (keyLower) {
                    "type", "widget_type", "widgettype", "kind" -> {
                        explicitType = normalizeTypeKeyword(unquotedVal)
                    }
                    "name", "widget_name", "widgetname", "title" -> {
                        explicitName = unquotedVal
                    }
                    "label" -> {
                        explicitLabel = unquotedVal
                    }
                    "min", "min_value", "minvalue" -> {
                        minVal = unquotedVal.toFloatOrNull()
                    }
                    "max", "max_value", "maxvalue", "slidermax" -> {
                        maxVal = unquotedVal.toFloatOrNull()
                    }
                    else -> {
                        rawAssignments.add(key to valueExpr)
                    }
                }
            } else {
                if (cleanLine != "{" && cleanLine != "}") {
                    executableLines.add(cleanLine)
                }
            }
        }

        // Also inspect gg.prompt(...) calls for automatic type & property detection
        val promptRegex = Regex("""gg\.prompt\s*\(\s*\{\s*["']([^"']+)["']\s*\}(?:\s*,\s*\{\s*([^}]*)\s*\})?(?:\s*,\s*\{\s*["']([^"']+)["']\s*\})?""")
        val promptMatch = promptRegex.find(trimmedCode)
        if (promptMatch != null) {
            val promptLabel = promptMatch.groupValues[1].trim()
            val promptDefault = unquote(promptMatch.groupValues.getOrNull(2)?.trim().orEmpty())
            val promptKind = promptMatch.groupValues.getOrNull(3)?.trim()?.lowercase(Locale.US).orEmpty()
            if (explicitLabel == null && promptLabel.isNotEmpty()) {
                explicitLabel = promptLabel
            }
            when (promptKind) {
                "file", "path" -> {
                    if (explicitType == null) explicitType = "path_input"
                    if (rawAssignments.none { it.first.equals("path", ignoreCase = true) || it.first.equals("file", ignoreCase = true) }) {
                        rawAssignments.add("path" to "\"${promptDefault.ifEmpty { "/storage/emulated/0/" }}\"")
                    }
                }
                "number" -> {
                    if (explicitType == null) explicitType = if (promptLabel.contains("[") && promptLabel.contains(";")) "slider" else "number_input"
                    if (rawAssignments.none { it.first.equals("value", ignoreCase = true) }) {
                        rawAssignments.add("value" to (promptDefault.ifEmpty { "50" }))
                    }
                }
                "checkbox" -> {
                    if (explicitType == null) explicitType = "toggle"
                }
                else -> {
                    if (explicitType == null) explicitType = "text_input"
                    if (rawAssignments.none { it.first.equals("value", ignoreCase = true) || it.first.equals("text", ignoreCase = true) }) {
                        rawAssignments.add("value" to "\"$promptDefault\"")
                    }
                }
            }
        }

        // Determine widgetType from explicitType or code semantics
        val detectedType = explicitType ?: inferWidgetTypeFromSemantics(
            trimmedCode = trimmedCode,
            rawAssignments = rawAssignments,
            parsedOptions = parsedOptions,
            minVal = minVal,
            maxVal = maxVal,
            functionBlocks = functionBlocks,
            executableLines = executableLines
        )

        // Build properties list from rawAssignments
        val properties = mutableListOf<LuaCustomWidgetProperty>()
        val hasLabelAndValuePair = explicitLabel != null &&
            rawAssignments.any { it.first.equals("value", ignoreCase = true) || it.first.equals("text", ignoreCase = true) }

        for ((rawKey, rawValExpr) in rawAssignments) {
            val keyLower = rawKey.lowercase(Locale.US)
            val unquoted = unquote(rawValExpr)

            // If this is a toggle widget's main state variable, keep it as boolean property or state
            val propType = inferPropertyType(
                key = keyLower,
                rawValExpr = rawValExpr,
                unquotedVal = unquoted,
                minVal = minVal,
                maxVal = maxVal,
                hasOptions = parsedOptions.isNotEmpty()
            )

            val propLabel = if (hasLabelAndValuePair && (keyLower == "value" || keyLower == "text")) {
                explicitLabel!!
            } else {
                formatPropertyLabel(rawKey)
            }

            properties.add(
                LuaCustomWidgetProperty(
                    key = rawKey,
                    label = propLabel,
                    type = propType,
                    value = unquoted,
                    min = minVal ?: 0f,
                    max = maxVal ?: 100f,
                    options = if (propType == "select") parsedOptions.ifEmpty { listOf("Option 1", "Option 2") } else emptyList()
                )
            )
        }

        // If options were declared without a separate mode/option variable, add a select property
        if (parsedOptions.isNotEmpty() && properties.none { it.type == "select" }) {
            properties.add(
                LuaCustomWidgetProperty(
                    key = "selected_option",
                    label = explicitLabel ?: "Option",
                    type = "select",
                    value = parsedOptions.first(),
                    options = parsedOptions
                )
            )
        }

        // Ensure primary property exists for input-oriented widget types
        when (detectedType) {
            "path_input" -> {
                if (properties.none { it.type == "path" || it.type == "file" }) {
                    properties.add(
                        0,
                        LuaCustomWidgetProperty(
                            key = "path",
                            label = explicitLabel ?: "Path",
                            type = "path",
                            value = "/storage/emulated/0/"
                        )
                    )
                }
            }
            "text_input" -> {
                if (properties.none { it.type == "text" }) {
                    properties.add(
                        0,
                        LuaCustomWidgetProperty(
                            key = "value",
                            label = explicitLabel ?: "Text",
                            type = "text",
                            value = "Enter text here"
                        )
                    )
                }
            }
            "number_input" -> {
                if (properties.none { it.type == "number" || it.type == "slider" }) {
                    properties.add(
                        0,
                        LuaCustomWidgetProperty(
                            key = "value",
                            label = explicitLabel ?: "Number",
                            type = "number",
                            value = "0",
                            min = minVal ?: 0f,
                            max = maxVal ?: 100f
                        )
                    )
                }
            }
            "slider" -> {
                if (properties.none { it.type == "slider" || it.type == "number" }) {
                    properties.add(
                        0,
                        LuaCustomWidgetProperty(
                            key = "value",
                            label = explicitLabel ?: "Value",
                            type = "slider",
                            value = "50",
                            min = minVal ?: 0f,
                            max = maxVal ?: 100f
                        )
                    )
                }
            }
            "select" -> {
                if (properties.none { it.type == "select" }) {
                    val opts = parsedOptions.ifEmpty { listOf("Option 1", "Option 2", "Option 3") }
                    properties.add(
                        0,
                        LuaCustomWidgetProperty(
                            key = "option",
                            label = explicitLabel ?: "Select Option",
                            type = "select",
                            value = opts.first(),
                            options = opts
                        )
                    )
                }
            }
            "link" -> {
                if (properties.none { it.key.equals("url", ignoreCase = true) || it.key.equals("link", ignoreCase = true) }) {
                    properties.add(
                        0,
                        LuaCustomWidgetProperty(
                            key = "url",
                            label = "Link URL",
                            type = "text",
                            value = "https://google.com"
                        )
                    )
                }
            }
        }

        // Determine widgetName
        val resolvedName = when {
            !explicitName.isNullOrBlank() -> explicitName!!
            !explicitLabel.isNullOrBlank() && !hasLabelAndValuePair -> explicitLabel!!
            !explicitLabel.isNullOrBlank() && hasLabelAndValuePair -> explicitLabel!!
            else -> defaultWidgetNameForType(detectedType)
        }

        // Detect events & logic from functionBlocks and executableLines
        val events = buildDetectedEvents(
            detectedType = detectedType,
            widgetName = resolvedName,
            properties = properties,
            functionBlocks = functionBlocks,
            executableLines = executableLines,
            defaultButtonLogic = defaultButtonLogic
        )

        val slug = resolvedName.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifEmpty { "custom_widget" }
        val widgetId = "${slug}_${System.currentTimeMillis() % 100000}"

        return LuaCustomWidgetSpec(
            widgetId = widgetId,
            widgetName = resolvedName,
            widgetType = detectedType,
            rawCode = trimmedCode,
            properties = properties,
            events = events
        )
    }

    private fun buildDetectedEvents(
        detectedType: String,
        widgetName: String,
        properties: List<LuaCustomWidgetProperty>,
        functionBlocks: Map<String, List<String>>,
        executableLines: List<String>,
        defaultButtonLogic: String
    ): List<LuaCustomWidgetEvent> {
        val safeDefaultClick = LuaScriptEngine.normalizeGameGuardianLuaCode(
            defaultButtonLogic.ifBlank { FALLBACK_DEFAULT_BUTTON_LOGIC }
        )
        val topLevelLogic = executableLines.joinToString("\n").trim()

        if (detectedType == "text_view") {
            return emptyList()
        }

        if (detectedType == "toggle") {
            val onFunc = functionBlocks.entries.firstOrNull {
                it.key.lowercase(Locale.US) in setOf("on", "onon", "ontoggleon", "onenable", "onchange", "ontoggle")
            }?.value?.joinToString("\n")?.trim().orEmpty()
            val offFunc = functionBlocks.entries.firstOrNull {
                it.key.lowercase(Locale.US) in setOf("off", "onoff", "ontoggleoff", "ondisable")
            }?.value?.joinToString("\n")?.trim().orEmpty()

            val resolvedOn = when {
                onFunc.isNotBlank() -> LuaScriptEngine.normalizeGameGuardianLuaCode(onFunc)
                topLevelLogic.isNotBlank() -> LuaScriptEngine.normalizeGameGuardianLuaCode(topLevelLogic)
                else -> "gg.toast(\"${escapeQuotes(widgetName)}: ON\")"
            }
            val resolvedOff = when {
                offFunc.isNotBlank() -> LuaScriptEngine.normalizeGameGuardianLuaCode(offFunc)
                else -> "gg.toast(\"${escapeQuotes(widgetName)}: OFF\")"
            }
            return listOf(
                LuaCustomWidgetEvent(
                    key = "onToggleOn",
                    title = "On Change / Toggle ON Logic",
                    logic = resolvedOn
                ),
                LuaCustomWidgetEvent(
                    key = "onToggleOff",
                    title = "On Change / Toggle OFF Logic",
                    logic = resolvedOff
                )
            )
        }

        val eventTitle = when (detectedType) {
            "button" -> "On Button Click Logic"
            "path_input" -> "On Path Change / Submit Logic"
            "text_input", "number_input" -> "On Change / On Submit Logic"
            "slider" -> "On Value Change Logic"
            "select" -> "On Option Select Logic"
            "link" -> "On Click Logic"
            else -> "On Event Logic"
        }

        val eventKey = when (detectedType) {
            "button", "link" -> "onClick"
            "path_input", "text_input", "number_input" -> "onSubmit"
            "slider", "select" -> "onChange"
            else -> "onClick"
        }

        val primaryFuncBody = functionBlocks.values.firstOrNull()?.joinToString("\n")?.trim().orEmpty()
        val userProvidedLogic = primaryFuncBody.ifBlank { topLevelLogic }

        val resolvedPrimaryLogic = if (userProvidedLogic.isNotBlank()) {
            LuaScriptEngine.normalizeGameGuardianLuaCode(userProvidedLogic)
        } else {
            when (detectedType) {
                "path_input" -> {
                    val propKey = properties.firstOrNull { it.type == "path" || it.type == "file" }?.key ?: "path"
                    "gg.toast(\"${escapeQuotes(widgetName)}: \" .. tostring($propKey))"
                }
                "text_input", "number_input", "slider", "select" -> {
                    val propKey = properties.firstOrNull()?.key ?: "value"
                    "gg.toast(\"${escapeQuotes(widgetName)}: \" .. tostring($propKey))"
                }
                else -> safeDefaultClick
            }
        }

        val result = mutableListOf(
            LuaCustomWidgetEvent(
                key = eventKey,
                title = eventTitle,
                logic = resolvedPrimaryLogic
            )
        )

        // Include any additional named functions declared in the code as secondary events
        if (functionBlocks.size > 1) {
            functionBlocks.entries.drop(1).forEach { (fnName, fnLines) ->
                result.add(
                    LuaCustomWidgetEvent(
                        key = fnName,
                        title = "Event: $fnName()",
                        logic = LuaScriptEngine.normalizeGameGuardianLuaCode(fnLines.joinToString("\n"))
                    )
                )
            }
        }

        return result
    }

    private fun normalizeTypeKeyword(raw: String): String {
        return when (raw.trim().lowercase(Locale.US)) {
            "button", "btn", "action" -> "button"
            "toggle", "switch", "checkbox", "bool", "boolean" -> "toggle"
            "path", "path_input", "pathinput", "file", "file_input", "fileinput", "folder" -> "path_input"
            "text_input", "textinput", "input", "edittext", "prompt", "string" -> "text_input"
            "number", "number_input", "numberinput", "int", "float" -> "number_input"
            "slider", "seekbar", "range" -> "slider"
            "select", "dropdown", "option", "options", "choice" -> "select"
            "link", "url", "web" -> "link"
            "text", "text_view", "textview", "label", "header" -> "text_view"
            else -> "button"
        }
    }

    private fun inferWidgetTypeFromSemantics(
        trimmedCode: String,
        rawAssignments: List<Pair<String, String>>,
        parsedOptions: List<String>,
        minVal: Float?,
        maxVal: Float?,
        functionBlocks: Map<String, List<String>>,
        executableLines: List<String>
    ): String {
        val codeLower = trimmedCode.lowercase(Locale.US)
        val keysLower = rawAssignments.map { it.first.lowercase(Locale.US) }.toSet()

        // 1. Path / File input detection
        val hasPathKey = keysLower.any {
            it in setOf("path", "file", "filepath", "targetpath", "target_path", "dir", "folder") ||
                it.endsWith("_path") || it.endsWith("path")
        }
        val hasPathValue = rawAssignments.any { (_, v) ->
            val u = unquote(v)
            u.startsWith("/storage/") || u.startsWith("/sdcard/") || u.startsWith("/data/")
        }
        if (hasPathKey || hasPathValue || codeLower.contains("path_input") || codeLower.contains("file_input")) {
            return "path_input"
        }

        // 2. Select / Options detection
        if (parsedOptions.isNotEmpty() || codeLower.contains("dropdown") || keysLower.contains("options")) {
            return "select"
        }

        // 3. Slider detection
        if ((minVal != null && maxVal != null) || keysLower.contains("slider") || codeLower.contains("seekbar")) {
            return "slider"
        }

        // 4. Link detection
        if (keysLower.contains("url") || keysLower.contains("link") || codeLower.contains("openlink(")) {
            return "link"
        }

        // 5. Toggle / Switch detection
        val hasToggleKeywords = keysLower.contains("state") ||
            functionBlocks.keys.any { it.lowercase(Locale.US).contains("toggle") } ||
            (codeLower.contains("on") && codeLower.contains("off") && codeLower.contains("state"))
        if (hasToggleKeywords && rawAssignments.any { (_, v) ->
                val u = unquote(v).lowercase(Locale.US)
                u == "true" || u == "false"
            }
        ) {
            return "toggle"
        }

        // 6. Text / Number Input detection
        val textInputKeys = setOf("value", "text", "message", "placeholder", "input", "hint", "prompt")
        if (keysLower.any { it in textInputKeys }) {
            val valPair = rawAssignments.firstOrNull { it.first.lowercase(Locale.US) in textInputKeys }
            if (valPair != null) {
                val rawV = valPair.second.trim()
                val unq = unquote(rawV)
                if (!rawV.startsWith("\"") && !rawV.startsWith("'") && unq.toDoubleOrNull() != null) {
                    return "number_input"
                }
                return "text_input"
            }
        }

        // 7. Static Text View detection
        if (functionBlocks.isEmpty() && executableLines.isEmpty() && rawAssignments.isEmpty() &&
            (codeLower.contains("textview") || codeLower.contains("text_view") || codeLower.contains("label"))
        ) {
            return "text_view"
        }

        return "button"
    }

    private fun inferPropertyType(
        key: String,
        rawValExpr: String,
        unquotedVal: String,
        minVal: Float?,
        maxVal: Float?,
        hasOptions: Boolean
    ): String {
        val k = key.lowercase(Locale.US)
        if (k in setOf("path", "filepath", "targetpath", "target_path", "dir", "folder") || k.endsWith("path")) {
            return "path"
        }
        if (k in setOf("file", "filename", "source_file")) {
            return "file"
        }
        if (unquotedVal.startsWith("/storage/") || unquotedVal.startsWith("/sdcard/") || unquotedVal.startsWith("/data/")) {
            return "path"
        }
        if (k.contains("color") || k.contains("hex") || unquotedVal.matches(Regex("^#[0-9A-Fa-f]{6,8}$"))) {
            return "color"
        }
        if (unquotedVal.equals("true", ignoreCase = true) || unquotedVal.equals("false", ignoreCase = true)) {
            return "boolean"
        }
        if (hasOptions && k in setOf("mode", "option", "selected", "choice", "value")) {
            return "select"
        }
        val isQuoted = (rawValExpr.startsWith("\"") && rawValExpr.endsWith("\"")) ||
            (rawValExpr.startsWith("'") && rawValExpr.endsWith("'"))
        if (!isQuoted && unquotedVal.toDoubleOrNull() != null) {
            return if (minVal != null || maxVal != null || k.contains("slider") || k.contains("range")) {
                "slider"
            } else {
                "number"
            }
        }
        return "text"
    }

    private fun formatPropertyLabel(rawKey: String): String {
        return rawKey
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .split("_", " ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.lowercase(Locale.US).replaceFirstChar { it.uppercase() }
            }
            .ifBlank { "Property" }
    }

    private fun defaultWidgetNameForType(type: String): String {
        return when (type.lowercase(Locale.US)) {
            "button" -> "Custom Button"
            "toggle" -> "Custom Toggle"
            "path_input" -> "File Path Box"
            "text_input" -> "Text Input Box"
            "number_input" -> "Number Input"
            "slider" -> "Custom Slider"
            "select" -> "Option Selector"
            "link" -> "Custom Link"
            "text_view" -> "Custom Text View"
            else -> "Custom Widget"
        }
    }

    private fun unquote(raw: String): String {
        val t = raw.trim()
        if ((t.startsWith("\"") && t.endsWith("\"") && t.length >= 2) ||
            (t.startsWith("'") && t.endsWith("'") && t.length >= 2)
        ) {
            return t.substring(1, t.length - 1)
        }
        return t
    }

    private fun escapeQuotes(raw: String): String {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun specToJson(spec: LuaCustomWidgetSpec): JSONObject {
        val obj = JSONObject()
        obj.put("widgetId", spec.widgetId)
        obj.put("widgetName", spec.widgetName)
        obj.put("widgetType", spec.widgetType)
        obj.put("rawCode", spec.rawCode)

        val propsArr = JSONArray()
        for (p in spec.properties) {
            val pObj = JSONObject()
            pObj.put("key", p.key)
            pObj.put("label", p.label)
            pObj.put("type", p.type)
            pObj.put("value", p.value)
            pObj.put("min", p.min.toDouble())
            pObj.put("max", p.max.toDouble())
            val optsArr = JSONArray()
            p.options.forEach { optsArr.put(it) }
            pObj.put("options", optsArr)
            propsArr.put(pObj)
        }
        obj.put("properties", propsArr)

        val eventsArr = JSONArray()
        for (ev in spec.events) {
            val eObj = JSONObject()
            eObj.put("key", ev.key)
            eObj.put("title", ev.title)
            eObj.put("logic", ev.logic)
            eventsArr.put(eObj)
        }
        obj.put("events", eventsArr)
        return obj
    }

    private fun specFromJson(obj: JSONObject): LuaCustomWidgetSpec {
        val widgetId = obj.optString("widgetId", "custom_widget")
        val widgetName = obj.optString("widgetName", "Custom Widget")
        val widgetType = obj.optString("widgetType", "button")
        val rawCode = obj.optString("rawCode", "")

        val properties = mutableListOf<LuaCustomWidgetProperty>()
        val propsArr = obj.optJSONArray("properties")
        if (propsArr != null) {
            for (i in 0 until propsArr.length()) {
                val pObj = propsArr.optJSONObject(i) ?: continue
                val opts = mutableListOf<String>()
                val optsArr = pObj.optJSONArray("options")
                if (optsArr != null) {
                    for (j in 0 until optsArr.length()) {
                        opts.add(optsArr.optString(j))
                    }
                }
                properties.add(
                    LuaCustomWidgetProperty(
                        key = pObj.optString("key", "value"),
                        label = pObj.optString("label", "Value"),
                        type = pObj.optString("type", "text"),
                        value = pObj.optString("value", ""),
                        min = pObj.optDouble("min", 0.0).toFloat(),
                        max = pObj.optDouble("max", 100.0).toFloat(),
                        options = opts
                    )
                )
            }
        }

        val events = mutableListOf<LuaCustomWidgetEvent>()
        val eventsArr = obj.optJSONArray("events")
        if (eventsArr != null) {
            for (i in 0 until eventsArr.length()) {
                val eObj = eventsArr.optJSONObject(i) ?: continue
                events.add(
                    LuaCustomWidgetEvent(
                        key = eObj.optString("key", "onClick"),
                        title = eObj.optString("title", "On Button Click Logic"),
                        logic = LuaScriptEngine.normalizeGameGuardianLuaCode(eObj.optString("logic", ""))
                    )
                )
            }
        }

        return LuaCustomWidgetSpec(
            widgetId = widgetId,
            widgetName = widgetName,
            widgetType = widgetType,
            rawCode = rawCode,
            properties = properties,
            events = events
        )
    }
}
