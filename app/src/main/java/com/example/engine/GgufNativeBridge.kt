package com.example.engine

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Real Android JNI Bridge around upstream llama.cpp (`libgguf_llama_jni.so`).
 *
 * Strictly enforces:
 * - Real GGUF validation & loading through `gguf_init_from_file` + `llama_model_load_from_file`
 * - Real context creation through `llama_init_from_model`
 * - Real tokenization through `llama_tokenize`
 * - Real autoregressive decoding & sampling through `llama_decode` + `llama_sampler_sample`
 * - Streaming token output via [TokenStreamCallback]
 * - Cancellation via [stopGeneration]
 * - Zero fake GGUF generation, zero synthetic weights, zero silent fallback.
 */
object GgufNativeBridge {

    private const val PREFS_NAME = "studio_gguf_prefs"
    private const val KEY_SAVED_GGUF_PATH = "saved_gguf_path"
    private const val KEY_GENERATION_MODE = "studio_generation_mode"

    @Volatile
    private var isJniLibraryLoaded = false

    @Volatile
    private var jniLoadFailureReason: String? = null

    init {
        try {
            System.loadLibrary("gguf_llama_jni")
            isJniLibraryLoaded = true
            nativeInitBackend()
        } catch (primaryErr: Throwable) {
            val hostSo = File("/tmp/host_jni_build/libgguf_llama_jni.so")
            if (hostSo.exists() && hostSo.isFile) {
                try {
                    System.load(hostSo.absolutePath)
                    isJniLibraryLoaded = true
                    jniLoadFailureReason = null
                    nativeInitBackend()
                } catch (hostErr: Throwable) {
                    isJniLibraryLoaded = false
                    jniLoadFailureReason = "Failed to load native library libgguf_llama_jni.so: ${hostErr.message ?: hostErr.javaClass.simpleName}"
                }
            } else {
                isJniLibraryLoaded = false
                jniLoadFailureReason = "Failed to load native library libgguf_llama_jni.so: ${primaryErr.message ?: primaryErr.javaClass.simpleName}"
            }
        }
    }

    @JvmStatic
    private external fun nativeInitBackend()

    @JvmStatic
    private external fun nativeGetLastError(): String

    @JvmStatic
    private external fun nativeGetSystemInfo(): String

    @JvmStatic
    private external fun nativeLoadModel(modelPath: String): Long

    @JvmStatic
    private external fun nativeCreateContext(
        modelHandle: Long,
        nCtx: Int,
        nBatch: Int,
        nThreads: Int
    ): Long

    @JvmStatic
    private external fun nativeGetModelMetadataJson(modelHandle: Long): String

    @JvmStatic
    private external fun nativeTokenize(
        modelHandle: Long,
        prompt: String,
        addSpecial: Boolean,
        parseSpecial: Boolean
    ): IntArray?

    @JvmStatic
    private external fun nativeGetLastGeneratedTokens(): IntArray?

    @JvmStatic
    private external fun nativeGenerateStream(
        modelHandle: Long,
        contextHandle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        grammar: String?,
        streamCallback: TokenStreamCallback?
    ): String?

    @JvmStatic
    private external fun nativeStopGeneration()

    @JvmStatic
    private external fun nativeUnloadModel()

    fun isNativeRuntimeAvailable(): Boolean = isJniLibraryLoaded

    fun getNativeLoadError(): String? {
        if (!isJniLibraryLoaded) {
            return jniLoadFailureReason ?: "Native library libgguf_llama_jni.so is not loaded."
        }
        return try {
            nativeGetLastError().takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            t.message
        }
    }

    fun getSystemInfo(): String {
        if (!isJniLibraryLoaded) {
            return jniLoadFailureReason ?: "JNI unavailable"
        }
        return try {
            nativeGetSystemInfo()
        } catch (t: Throwable) {
            "Error querying llama.cpp system info: ${t.message}"
        }
    }

    fun saveSelectedGgufPath(context: Context, path: String, mode: StudioGenerationMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVED_GGUF_PATH, path)
            .putString(KEY_GENERATION_MODE, mode.name)
            .apply()
    }

    fun getSavedGgufPath(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SAVED_GGUF_PATH, "") ?: ""
    }

    fun getSavedGenerationMode(context: Context): StudioGenerationMode {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GENERATION_MODE, StudioGenerationMode.OFFLINE_MANUAL.name)
        return try {
            StudioGenerationMode.valueOf(raw ?: StudioGenerationMode.OFFLINE_MANUAL.name)
        } catch (_: Exception) {
            StudioGenerationMode.OFFLINE_MANUAL
        }
    }

    /**
     * Loads the previously selected real GGUF model if one exists on disk.
     * Does NOT generate or substitute any sample/fake GGUF model.
     */
    fun restoreSavedModelState(
        context: Context,
        preferredMode: StudioGenerationMode = getSavedGenerationMode(context)
    ): GgufModelState {
        val savedPath = getSavedGgufPath(context).trim()
        if (savedPath.isEmpty()) {
            return GgufModelState(
                mode = preferredMode,
                isValidGgufLoaded = false,
                importErrorMessage = null,
                statusMessage = "No GGUF model selected."
            )
        }
        val state = loadModel(savedPath, preferredMode = preferredMode)
        if (!state.isValidGgufLoaded) {
            return state.copy(
                mode = preferredMode,
                statusMessage = state.importErrorMessage ?: "No GGUF model selected."
            )
        }
        return state
    }

    fun validateGgufFilePath(
        filePath: String,
        preferredMode: StudioGenerationMode = StudioGenerationMode.AI_GGUF_MODE
    ): GgufModelState {
        return loadModel(filePath, preferredMode = preferredMode)
    }

    /**
     * Loads a real `.gguf` file via `llama.cpp` and creates an active `llama_context`.
     * Returns a technical error in [GgufModelState.importErrorMessage] if loading fails.
     */
    @Synchronized
    fun loadModel(
        filePath: String,
        nCtx: Int = 2048,
        nBatch: Int = 512,
        nThreads: Int = 0,
        preferredMode: StudioGenerationMode = StudioGenerationMode.AI_GGUF_MODE
    ): GgufModelState {
        val trimmed = filePath.trim()
        if (trimmed.isEmpty()) {
            return GgufModelState(
                mode = preferredMode,
                isValidGgufLoaded = false,
                importErrorMessage = null,
                statusMessage = "No GGUF model selected."
            )
        }

        val file = File(trimmed)
        if (!file.exists() || !file.isFile) {
            return GgufModelState(
                mode = preferredMode,
                isValidGgufLoaded = false,
                modelFilePath = trimmed,
                importErrorMessage = "GGUF file not found at path: $trimmed",
                statusMessage = "Error: GGUF file not found."
            )
        }

        if (!file.name.lowercase().endsWith(".gguf")) {
            return GgufModelState(
                mode = preferredMode,
                isValidGgufLoaded = false,
                modelFileName = file.name,
                modelFilePath = file.absolutePath,
                importErrorMessage = "Invalid file extension (${file.name}). Please select a real .gguf model file.",
                statusMessage = "Error: Selected file is not a .gguf model."
            )
        }

        if (!isJniLibraryLoaded) {
            val err = jniLoadFailureReason ?: "Native library libgguf_llama_jni.so is not loaded."
            return GgufModelState(
                mode = preferredMode,
                isValidGgufLoaded = false,
                modelFileName = file.name,
                modelFilePath = file.absolutePath,
                importErrorMessage = err,
                statusMessage = "JNI Error: $err"
            )
        }

        return try {
            val modelHandle = nativeLoadModel(file.absolutePath)
            if (modelHandle == 0L) {
                val nativeErr = nativeGetLastError().ifBlank {
                    "llama_model_load_from_file() returned null for ${file.name}."
                }
                return GgufModelState(
                    mode = preferredMode,
                    isValidGgufLoaded = false,
                    modelFileName = file.name,
                    modelFilePath = file.absolutePath,
                    importErrorMessage = nativeErr,
                    statusMessage = "Model load failed: $nativeErr"
                )
            }

            val ctxHandle = nativeCreateContext(modelHandle, nCtx, nBatch, nThreads)
            if (ctxHandle == 0L) {
                val nativeErr = nativeGetLastError().ifBlank {
                    "llama_init_from_model() failed to allocate llama_context."
                }
                nativeUnloadModel()
                return GgufModelState(
                    mode = preferredMode,
                    isValidGgufLoaded = false,
                    modelFileName = file.name,
                    modelFilePath = file.absolutePath,
                    importErrorMessage = nativeErr,
                    statusMessage = "Context creation failed: $nativeErr"
                )
            }

            val metaJsonStr = nativeGetModelMetadataJson(modelHandle)
            val meta = if (metaJsonStr.isNotBlank()) JSONObject(metaJsonStr) else JSONObject()

            val arch = meta.optString("architecture", "llama").ifBlank { "llama" }
            val desc = meta.optString("description", "").ifBlank { arch }
            val tensorCount = meta.optLong("tensorCount", 0L)
            val kvCount = meta.optLong("kvCount", 0L)
            val vocabSize = meta.optInt("vocabSize", 0)
            val embdDim = meta.optInt("embeddingDim", 0)
            val layers = meta.optInt("numLayers", 0)
            val heads = meta.optInt("attentionHeads", 0)
            val ctxLen = meta.optInt("contextLength", nCtx)
            val params = meta.optLong("parameterCount", 0L)

            GgufModelState(
                mode = preferredMode,
                isValidGgufLoaded = true,
                modelHandle = modelHandle,
                contextHandle = ctxHandle,
                modelFileName = file.name,
                modelFilePath = file.absolutePath,
                modelArchitecture = arch,
                modelDescription = desc,
                quantizationTag = desc,
                modelSizeBytes = file.length(),
                tensorCount = tensorCount,
                kvCount = kvCount,
                vocabSize = vocabSize,
                embeddingDim = embdDim,
                numLayers = layers,
                attentionHeads = heads,
                contextLength = ctxLen,
                parameterCount = params,
                importErrorMessage = null,
                statusMessage = "Model loaded (${file.name} • $desc • $layers layers • $vocabSize vocab)"
            )
        } catch (t: Throwable) {
            GgufModelState(
                mode = preferredMode,
                isValidGgufLoaded = false,
                modelFileName = file.name,
                modelFilePath = file.absolutePath,
                importErrorMessage = "JNI exception while loading GGUF model: ${t.message}",
                statusMessage = "JNI exception: ${t.message}"
            )
        }
    }

    fun createContext(
        modelHandle: Long,
        nCtx: Int = 2048,
        nBatch: Int = 512,
        nThreads: Int = 0
    ): Long {
        if (!isJniLibraryLoaded) {
            throw IllegalStateException(jniLoadFailureReason ?: "Native llama.cpp JNI library is not loaded.")
        }
        val handle = nativeCreateContext(modelHandle, nCtx, nBatch, nThreads)
        if (handle == 0L) {
            throw IllegalStateException(nativeGetLastError().ifBlank { "llama_init_from_model() failed." })
        }
        return handle
    }

    fun tokenize(
        modelHandle: Long,
        prompt: String,
        addSpecial: Boolean = true,
        parseSpecial: Boolean = true
    ): IntArray {
        if (!isJniLibraryLoaded) {
            throw IllegalStateException(jniLoadFailureReason ?: "Native llama.cpp JNI library is not loaded.")
        }
        return nativeTokenize(modelHandle, prompt, addSpecial, parseSpecial)
            ?: throw IllegalStateException(nativeGetLastError().ifBlank { "llama_tokenize() failed." })
    }

    fun generate(
        modelHandle: Long,
        contextHandle: Long,
        prompt: String,
        maxTokens: Int = 384,
        temperature: Float = 0.35f,
        topP: Float = 0.90f,
        topK: Int = 40,
        grammar: String? = null,
        streamCallback: TokenStreamCallback? = null
    ): String {
        if (!isJniLibraryLoaded) {
            throw IllegalStateException(jniLoadFailureReason ?: "Native llama.cpp JNI library is not loaded.")
        }
        val output = nativeGenerateStream(
            modelHandle,
            contextHandle,
            prompt,
            maxTokens,
            temperature,
            topP,
            topK,
            grammar,
            streamCallback
        )
        if (output == null) {
            val err = nativeGetLastError().ifBlank { "llama.cpp token generation failed." }
            throw IllegalStateException(err)
        }
        return output
    }

    fun getLastGeneratedTokenIds(): List<Int> {
        if (!isJniLibraryLoaded) return emptyList()
        return try {
            nativeGetLastGeneratedTokens()?.toList() ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun stopGeneration() {
        if (isJniLibraryLoaded) {
            try {
                nativeStopGeneration()
            } catch (_: Throwable) {
            }
        }
    }

    @Synchronized
    fun unloadModel() {
        if (isJniLibraryLoaded) {
            try {
                nativeUnloadModel()
            } catch (_: Throwable) {
            }
        }
    }

    fun importGgufFromUri(context: Context, uri: Uri): GgufModelState {
        return try {
            val displayName = resolveDisplayName(context, uri) ?: "imported_model.gguf"
            if (!displayName.lowercase().endsWith(".gguf")) {
                return GgufModelState(
                    mode = StudioGenerationMode.OFFLINE_MANUAL,
                    isValidGgufLoaded = false,
                    modelFileName = displayName,
                    importErrorMessage = "Selected file '$displayName' is not a .gguf model file.",
                    statusMessage = "Error: Selected file is not a .gguf model."
                )
            }

            val modelsDir = File(context.filesDir, "gguf_models").apply { mkdirs() }
            val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val destFile = File(modelsDir, safeName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            } ?: return GgufModelState(
                mode = StudioGenerationMode.OFFLINE_MANUAL,
                isValidGgufLoaded = false,
                importErrorMessage = "Unable to open selected file stream from Android storage.",
                statusMessage = "Error: Could not read selected GGUF file."
            )

            val loadedState = loadModel(destFile.absolutePath, preferredMode = StudioGenerationMode.AI_GGUF_MODE)
            if (loadedState.isValidGgufLoaded) {
                saveSelectedGgufPath(context, destFile.absolutePath, StudioGenerationMode.AI_GGUF_MODE)
            } else {
                destFile.delete()
            }
            loadedState
        } catch (e: Exception) {
            GgufModelState(
                mode = StudioGenerationMode.OFFLINE_MANUAL,
                isValidGgufLoaded = false,
                importErrorMessage = "GGUF import failed: ${e.message}",
                statusMessage = "Import error: ${e.message}"
            )
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else uri.lastPathSegment
            } else {
                uri.lastPathSegment
            }
        } catch (_: Exception) {
            uri.lastPathSegment
        } finally {
            cursor?.close()
        }
    }

    fun evaluateUserPrompt(
        prompt: String,
        existingProjectName: String? = null,
        modelState: GgufModelState? = null,
        streamCallback: TokenStreamCallback? = null
    ): AiPromptEvaluation {
        return GgufAstOverlayEngine.evaluatePromptWithLoadedModel(
            prompt = prompt,
            existingProjectName = existingProjectName,
            modelState = modelState,
            streamCallback = streamCallback
        )
    }

    fun generateBlueprintFromPrompt(
        context: Context,
        prompt: String,
        projectId: Long,
        defaultTargetFilePath: String,
        modelState: GgufModelState,
        existingProjectName: String? = null,
        existingPackageName: String? = null,
        existingOverlayTitle: String? = null,
        existingComponents: List<com.example.data.CanvasComponentEntity> = emptyList(),
        existingScratchFiles: Map<String, String> = emptyMap(),
        streamCallback: TokenStreamCallback? = null
    ): GeneratedBlueprintSpec {
        return GgufAstOverlayEngine.executeRealLlamaPipeline(
            context = context,
            prompt = prompt,
            projectId = projectId,
            defaultTargetFilePath = defaultTargetFilePath,
            modelState = modelState,
            existingProjectName = existingProjectName,
            existingPackageName = existingPackageName,
            existingOverlayTitle = existingOverlayTitle,
            existingComponents = existingComponents,
            existingScratchFiles = existingScratchFiles,
            streamCallback = streamCallback
        )
    }
}
