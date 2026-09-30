/*
 * Decompiled with CFR 0.152.
 */
package com.example.engine;

public final class ConfigParameterSpec {
    public static final int HEADER_SIZE_BYTES = 64;
    public static final byte[] MAGIC_BYTES = new byte[]{70, 67, 70, 71};
    public static final int OFFSET_HW_ACCEL = 4;
    public static final int OFFSET_ZERO_COPY = 5;
    public static final int OFFSET_QUANT_INT8 = 6;
    public static final int OFFSET_TELEMETRY = 7;
    public static final int OFFSET_THREAD_COUNT = 8;
    public static final int OFFSET_FREQ_GOVERNOR = 12;
    public static final int OFFSET_VRAM_CEILING = 16;
    public static final int OFFSET_NODE_TAG = 20;
    public static final int LENGTH_NODE_TAG = 16;
    public static final int OFFSET_REGISTER_HEX = 36;
    public static final int LENGTH_REGISTER_HEX = 10;
    public static final int OFFSET_MODE_TAG = 46;
    public static final int LENGTH_MODE_TAG = 14;
    public static final int OFFSET_CRC32 = 60;
    public static final String KEY_HW_ACCEL = "hw_tensor_accel";
    public static final String KEY_ZERO_COPY = "zero_copy_dma";
    public static final String KEY_QUANT_INT8 = "quant_int8_mode";
    public static final String KEY_TELEMETRY = "kernel_telemetry";
    public static final String KEY_THREAD_COUNT = "worker_thread_count";
    public static final String KEY_FREQ_GOVERNOR = "freq_governor_pct";
    public static final String KEY_VRAM_CEILING = "vram_ceiling_mb";
    public static final String KEY_NODE_TAG = "execution_node_tag";
    public static final String KEY_REGISTER_HEX = "custom_register_hex";
    public static final String KEY_OPERATING_MODE = "operating_mode";

    private ConfigParameterSpec() {
    }

    public static class StateSnapshot {
        public boolean hwAccel = true;
        public boolean zeroCopyDma = false;
        public boolean quantInt8 = true;
        public boolean kernelTelemetry = false;
        public int workerThreads = 4;
        public int freqGovernorPct = 85;
        public int vramCeilingMb = 512;
        public String executionNodeTag = "NPU_CLUSTER_A0";
        public String customRegisterHex = "0x7F00A1";
        public String operatingMode = "ENGINE_INIT";
        public long crc32Value = 0L;
        public byte[] rawHeaderBytes = new byte[64];
        public String rawTextContent = "";
        public String targetFilePath = "";

        public StateSnapshot copy() {
            StateSnapshot c = new StateSnapshot();
            c.hwAccel = this.hwAccel;
            c.zeroCopyDma = this.zeroCopyDma;
            c.quantInt8 = this.quantInt8;
            c.kernelTelemetry = this.kernelTelemetry;
            c.workerThreads = this.workerThreads;
            c.freqGovernorPct = this.freqGovernorPct;
            c.vramCeilingMb = this.vramCeilingMb;
            c.executionNodeTag = this.executionNodeTag;
            c.customRegisterHex = this.customRegisterHex;
            c.operatingMode = this.operatingMode;
            c.crc32Value = this.crc32Value;
            c.rawHeaderBytes = this.rawHeaderBytes != null ? (byte[])this.rawHeaderBytes.clone() : new byte[64];
            c.rawTextContent = this.rawTextContent;
            c.targetFilePath = this.targetFilePath;
            return c;
        }
    }
}
