package com.heecomou.desktop

import com.heecomou.desktop.audio.AudioCaptureManager
import com.heecomou.desktop.network.VocabVO
import com.heecomou.desktop.vocab.LocalVocabStore
import org.junit.jupiter.api.*
import java.io.File
import kotlin.test.assertTrue

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DesktopPerformanceBenchmarkTest {

    private val WARMUP_ITERATIONS = 3
    private val BENCH_ITERATIONS = 10

    // ============================================================
    // 1. AudioCaptureManager Buffer Benchmarks
    // ============================================================
    @Test
    @Order(1)
    @DisplayName("[Perf] AudioCapture buffer allocation time")
    fun audioCaptureBufferAllocation() {
        val durations = mutableListOf<Long>()

        repeat(BENCH_ITERATIONS) {
            val start = System.nanoTime()
            val manager = AudioCaptureManager()
            val bufferSize = manager.bufferSize
            manager.release()
            val end = System.nanoTime()
            durations.add(end - start)
            assertTrue(bufferSize > 0, "Buffer size should be positive")
        }

        val avgUs = durations.average() / 1000.0
        println("[Perf] AudioCaptureManager alloc: avg=${"%.1f".format(avgUs)}µs")
        assertTrue(avgUs < 100_000.0,
            "AudioCaptureManager creation should complete under 100µs, got ${"%.1f".format(avgUs)}µs")
    }

    @Test
    @Order(2)
    @DisplayName("[Perf] AudioCapture buffer size comparison (16kHz vs 44.1kHz)")
    fun audioCaptureBufferSizeComparison() {
        val manager16k = AudioCaptureManager(sampleRate = 16000f)
        val manager44k = AudioCaptureManager(sampleRate = 44100f)

        val buffer16k = manager16k.bufferSize
        val buffer44k = manager44k.bufferSize

        println("[Perf] Buffer 16kHz: $buffer16k bytes")
        println("[Perf] Buffer 44.1kHz: $buffer44k bytes")
        println("[Perf] Ratio: ${"%.2f".format(buffer44k.toDouble() / buffer16k.toDouble())}x")

        assertTrue(buffer44k > buffer16k, "44.1kHz buffer should be larger than 16kHz")
        assertTrue(buffer16k in 1024..8192, "16kHz buffer should be in reasonable range")

        manager16k.release()
        manager44k.release()
    }

    // ============================================================
    // 2. LocalVocabStore Benchmarks
    // ============================================================
    @Test
    @Order(3)
    @DisplayName("[Perf] LocalVocabStore batch insert (100 words)")
    fun vocabBatchInsert100() {
        val testDb = "perf-vocab-${System.currentTimeMillis()}.db"
        val store = LocalVocabStore(testDb)

        try {
            val items = (1..100).map { i ->
                VocabVO(
                    id = i.toLong(),
                    userId = 1L,
                    word = "词${i}",
                    pinyin = "ci ${i}",
                    category = "性能测试",
                    frequency = i % 10,
                    version = i.toLong()
                )
            }

            val durations = mutableListOf<Long>()
            repeat(BENCH_ITERATIONS) {
                val start = System.nanoTime()
                store.upsertBatch(items)
                val end = System.nanoTime()
                durations.add(end - start)
            }

            val avgMs = durations.average() / 1_000_000.0
            val opsPerSec = (100.0 / (durations.average() / 1_000_000_000.0))

            println("[Perf] Batch insert 100 words: avg=${"%.2f".format(avgMs)}ms, ${"%.0f".format(opsPerSec)} ops/sec")
            assertTrue(avgMs < 500.0,
                "Batch insert 100 words should complete under 500ms, got ${"%.2f".format(avgMs)}ms")
        } finally {
            store.close()
            File(testDb).delete()
        }
    }

    @Test
    @Order(4)
    @DisplayName("[Perf] LocalVocabStore search (1000 words dataset)")
    fun vocabSearchPerformance() {
        val testDb = "perf-search-${System.currentTimeMillis()}.db"
        val store = LocalVocabStore(testDb)

        try {
            val items = (1..1000).map { i ->
                VocabVO(
                    id = i.toLong(),
                    userId = 1L,
                    word = if (i % 2 == 0) "高频词${i}" else "低频词${i}",
                    pinyin = "pin yin ${i}",
                    category = "性能测试",
                    frequency = if (i % 2 == 0) 100 else 1,
                    version = i.toLong()
                )
            }
            store.upsertBatch(items + items) // upsert same data twice

            assertEquals(1000, store.countWords())

            val durations = mutableListOf<Long>()
            repeat(BENCH_ITERATIONS) {
                val start = System.nanoTime()
                val results = store.search("高频", limit = 20)
                val end = System.nanoTime()
                durations.add(end - start)
                assertTrue(results.isNotEmpty(), "Search should return results")
            }

            val avgMs = durations.average() / 1_000_000.0
            println("[Perf] Search in 1000-word DB: avg=${"%.2f".format(avgMs)}ms")
            assertTrue(avgMs < 100.0,
                "Search in 1000 words should complete under 100ms, got ${"%.2f".format(avgMs)}ms")
        } finally {
            store.close()
            File(testDb).delete()
        }
    }

    @Test
    @Order(5)
    @DisplayName("[Perf] LocalVocabStore getWord latency")
    fun vocabGetWordLatency() {
        val testDb = "perf-getword-${System.currentTimeMillis()}.db"
        val store = LocalVocabStore(testDb)

        try {
            val item = VocabVO(1L, 1L, "基准词", "ji zhun ci", "测试", 10, 1L, null, null)
            store.upsertBatch(listOf(item))

            val durations = mutableListOf<Long>()
            repeat(BENCH_ITERATIONS * 10) {
                val start = System.nanoTime()
                val freq = store.getWord("基准词")
                val end = System.nanoTime()
                durations.add(end - start)
                assertEquals(10, freq)
            }

            val avgUs = durations.average() / 1000.0
            println("[Perf] getWord lookup: avg=${"%.1f".format(avgUs)}µs")
            assertTrue(avgUs < 10_000.0,
                "Word lookup should complete under 10µs, got ${"%.1f".format(avgUs)}µs")
        } finally {
            store.close()
            File(testDb).delete()
        }
    }

    @Test
    @Order(6)
    @DisplayName("[Perf] LocalVocabStore bumpFrequency throughput")
    fun vocabBumpFrequencyThroughput() {
        val testDb = "perf-bump-${System.currentTimeMillis()}.db"
        val store = LocalVocabStore(testDb)

        try {
            val item = VocabVO(1L, 1L, "热词", "re ci", "测试", 0, 1L, null, null)
            store.upsertBatch(listOf(item))

            val duration = measureNanos {
                repeat(100) { store.bumpFrequency("热词") }

            }

            val finalFreq = store.getWord("热词")
            assertEquals(100, finalFreq)

            val avgUs = duration / 100.0 / 1000.0
            println("[Perf] bumpFrequency 100x: total=${duration/1_000_000.0}ms, avg=${"%.1f".format(avgUs)}µs/op")
            assertTrue(avgUs < 500.0,
                "bumpFrequency per operation should be under 500µs, got ${"%.1f".format(avgUs)}µs")
        } finally {
            store.close()
            File(testDb).delete()
        }
    }

    // ============================================================
    // 3. Model Serialization Benchmarks
    // ============================================================
    @Test
    @Order(7)
    @DisplayName("[Perf] VocabVO serialization throughput")
    fun vocabVOSerializationThroughput() {
        val gson = com.google.gson.Gson()
        val vocab = VocabVO(1L, 1L, "性能测试词", "xing neng ce shi ci", "术语", 10, 100L, "2024-01-01", "2024-01-02")

        // Warmup
        repeat(WARMUP_ITERATIONS) {
            gson.toJson(vocab)
            gson.fromJson(gson.toJson(vocab), VocabVO::class.java)
        }

        // Serialization
        val serDuration = measureNanos {
            repeat(1000) { gson.toJson(vocab) }
        }
        val serAvgUs = serDuration / 1000.0 / 1000.0
        println("[Perf] VocabVO serialize 1000x: avg=${"%.1f".format(serAvgUs)}µs/op")

        // Deserialization
        val json = gson.toJson(vocab)
        val deserDuration = measureNanos {
            repeat(1000) { gson.fromJson(json, VocabVO::class.java) }
        }
        val deserAvgUs = deserDuration / 1000.0 / 1000.0
        println("[Perf] VocabVO deserialize 1000x: avg=${"%.1f".format(deserAvgUs)}µs/op")

        assertTrue(serAvgUs < 100.0,
            "Serialization should be under 100µs, got ${"%.1f".format(serAvgUs)}µs")
        assertTrue(deserAvgUs < 100.0,
            "Deserialization should be under 100µs, got ${"%.1f".format(deserAvgUs)}µs")
    }

    // ============================================================
    // 4. AsrRouter Decision Latency
    // ============================================================
    @Test
    @Order(8)
    @DisplayName("[Perf] AsrRouter decision latency")
    fun asrRouterDecisionLatency() {
        val router = com.heecomou.desktop.asr.AsrRouter()
        val preferences = com.heecomou.desktop.asr.AsrPreferences()

        val duration = measureNanos {
            repeat(1000) {
                router.decide(preferences, isNetworkAvailable = true, networkRttMs = 50, noiseLevel = 1)
            }
        }

        val avgUs = duration / 1000.0 / 1000.0
        println("[Perf] AsrRouter.decide 1000x: avg=${"%.1f".format(avgUs)}µs/op")
        assertTrue(avgUs < 50.0,
            "Router decision should be under 50µs, got ${"%.1f".format(avgUs)}µs")
    }

    // ============================================================
    // 5. TextOutputManager Latency
    // ============================================================
    @Test
    @Order(9)
    @DisplayName("[Perf] TextOutput writeToClipboard latency")
    fun textOutputClipboardLatency() {
        val output = com.heecomou.desktop.ui.TextOutputManager()

        try {
            val duration = measureNanos {
                repeat(100) {
                    output.writeToClipboard("性能测试 第${it}次")
                }
            }

            val avgUs = duration / 100.0 / 1000.0
            println("[Perf] writeToClipboard 100x: avg=${"%.1f".format(avgUs)}µs/op")
            assertTrue(avgUs < 10_000.0,
                "Clipboard write should be under 10µs, got ${"%.1f".format(avgUs)}µs")
        } finally {
            output.release()
        }
    }

    // ============================================================
    // Utility
    // ============================================================
    private inline fun measureNanos(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }
}
