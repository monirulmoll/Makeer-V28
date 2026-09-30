package com.example.engine

import com.example.data.ComponentWidgetType

fun interface TokenStreamCallback {
    fun onToken(piece: String, tokenId: Int)
}

data class NeuralInferenceTrace(
    val usedNativeJni: Boolean,
    val ggufVersion: Int,
    val tensorCount: Long,
    val kvCount: Long,
    val vocabSize: Int,
    val inputTokenIds: List<Int>,
    val generatedTokenIds: List<Int>,
    val decodedTokenStream: String,
    val topLogitConfidence: Float,
    val embeddingDimension: Int,
    val attentionHeads: Int,
    val layersEvaluated: Int
)

data class OverlayPanelSpec(
    val width: Int = 280,
    val height: Int = 360,
    val x: Int = 48,
    val y: Int = 140,
    val draggable: Boolean = true
)

data class OverlayThemeSpec(
    val backgroundColor: String = "#0F172A",
    val panelColor: String = "#1E293B",
    val accentColor: String = "#38BDF8",
    val textColor: String = "#F8FAFC"
)

data class OverlayServiceSpec(
    val className: String = "AiDynamicOverlayService",
    val windowType: String = "TYPE_APPLICATION_OVERLAY",
    val flags: String = "FLAG_NOT_FOCUSABLE"
)

data class OverlayActionSpec(
    val id: String,
    val type: String,
    val expression: String
)

data class StructuredWidgetSpec(
    val widgetType: ComponentWidgetType,
    val label: String,
    val fieldSlug: String,
    val byteOffsetHex: String,
    val offPayload: String,
    val onPayload: String,
    val initialValue: String,
    val sliderMax: Int,
    val bgColorHex: String,
    val textColorHex: String,
    val soundTrigger: String,
    val customLogicExpression: String
)

data class StructuredAppSpecification(
    val appType: String = "floating_overlay",
    val appName: String,
    val packageName: String,
    val overlayTitle: String,
    val targetFilePath: String,
    val isFloatingOverlay: Boolean = true,
    val appCategory: String = "FLOATING_OVERLAY_APP",
    val requestClass: AiRequestClassification = AiRequestClassification.CLASS_A_SPECIFIC_APP,
    val decisionAnnouncement: String,
    val appPurpose: String,
    val expectedBehavior: String,
    val panel: OverlayPanelSpec = OverlayPanelSpec(),
    val theme: OverlayThemeSpec = OverlayThemeSpec(),
    val service: OverlayServiceSpec = OverlayServiceSpec(),
    val permissions: List<String> = listOf(
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.FOREGROUND_SERVICE"
    ),
    val actions: List<OverlayActionSpec> = emptyList(),
    val widgets: List<StructuredWidgetSpec>,
    val rawLlmJson: String = "",
    val inferenceTrace: NeuralInferenceTrace
)
