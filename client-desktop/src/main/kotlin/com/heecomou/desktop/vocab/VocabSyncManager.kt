package com.heecomou.desktop.vocab

import com.heecomou.desktop.network.VocabApiService
import java.util.logging.Level
import java.util.logging.Logger

class VocabSyncManager(
    private val apiService: VocabApiService,
    private val localStore: LocalVocabStore
) {
    companion object {
        private val LOGGER = Logger.getLogger(VocabSyncManager::class.java.name)
    }

    fun syncIfNeeded(): Boolean {
        return try {
            val maxVersion = localStore.getMaxVersion()
            var hasMore = true
            var syncedCount = 0

            while (hasMore) {
                val response = apiService.sync(maxVersion)
                if (response?.code == 200 && response.data != null) {
                    val items = response.data.items
                    if (items.isNotEmpty()) {
                        localStore.upsertBatch(items)
                        syncedCount += items.size
                    }
                    hasMore = items.size >= 500
                } else {
                    hasMore = false
                }
            }

            LOGGER.info("Sync completed: $syncedCount items synced")
            syncedCount > 0
        } catch (e: Exception) {
            LOGGER.log(Level.WARNING, "Sync failed: ${e.message}", e)
            false
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
