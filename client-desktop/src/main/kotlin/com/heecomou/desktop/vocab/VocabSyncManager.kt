package com.heecomou.desktop.vocab

import com.heecomou.desktop.network.VocabApiService
import java.util.logging.Level
import java.util.logging.Logger

data class SyncResult(
    val syncedCount: Int,
    val totalCount: Int,
    val error: String? = null
)

class VocabSyncManager(
    private val apiService: VocabApiService,
    private val localStore: LocalVocabStore
) {
    companion object {
        private val LOGGER = Logger.getLogger(VocabSyncManager::class.java.name)
    }

    fun syncIfNeeded(): SyncResult {
        return try {
            var currentVersion = localStore.getMaxVersion()
            var syncedCount = 0

            while (true) {
                val response = apiService.sync(currentVersion)
                if (response == null) {
                    return SyncResult(syncedCount, localStore.countWords(),
                        "网络连接失败，无法访问服务器")
                }
                if (response.code != 200) {
                    return SyncResult(syncedCount, localStore.countWords(),
                        "同步失败: [${response.code}] ${response.message}")
                }
                val data = response.data
                if (data != null) {
                    if (data.items.isNotEmpty()) {
                        localStore.upsertBatch(data.items)
                        syncedCount += data.items.size
                    }
                    currentVersion = maxOf(currentVersion, data.maxVersion)
                    if (!data.hasMore) break
                } else {
                    break
                }
            }

            val total = localStore.countWords()
            LOGGER.info("Sync completed: $syncedCount new, $total total")
            SyncResult(syncedCount, total)
        } catch (e: Exception) {
            LOGGER.log(Level.WARNING, "Sync exception: ${e.message}", e)
            SyncResult(0, localStore.countWords(),
                "同步异常: ${e.message}")
        }
    }

    fun searchLocal(keyword: String): List<Pair<String, Int>> {
        return localStore.search(keyword)
    }

    fun bumpWordFrequency(word: String) {
        try {
            localStore.bumpFrequency(word)
        } catch (e: Exception) {
            LOGGER.log(Level.WARNING, "Failed to bump frequency: ${e.message}", e)
        }
    }

    fun getLocalWordCount(): Int = localStore.countWords()
}
