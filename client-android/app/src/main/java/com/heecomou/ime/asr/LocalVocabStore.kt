package com.heecomou.ime.asr

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.heecomou.ime.model.ApiResponse
import com.heecomou.ime.model.VocabListResponse
import com.heecomou.ime.model.VocabVO
import com.heecomou.ime.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalVocabStore(context: Context) : SQLiteOpenHelper(
    context, "vocab.db", null, 1
) {

    companion object {
        const val TABLE = "vocab"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY,
                user_id INTEGER NOT NULL,
                word TEXT NOT NULL,
                pinyin TEXT,
                category TEXT,
                frequency INTEGER DEFAULT 1,
                version INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_vocab_word ON $TABLE(word)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun getMaxVersion(): Long {
        val cursor = readableDatabase.rawQuery(
            "SELECT MAX(version) FROM $TABLE", null
        )
        val version = if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        cursor.close()
        return version
    }

    fun upsertBatch(items: List<VocabVO>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (item in items) {
                val values = ContentValues().apply {
                    put("id", item.id)
                    put("user_id", item.userId)
                    put("word", item.word)
                    put("pinyin", item.pinyin)
                    put("category", item.category)
                    put("frequency", item.frequency)
                    put("version", item.version)
                }
                db.insertWithOnConflict(TABLE, null, values,
                    SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun search(keyword: String): List<Pair<String, Int>> {
        val cursor = readableDatabase.rawQuery(
            "SELECT word, frequency FROM $TABLE WHERE word LIKE ? ORDER BY frequency DESC LIMIT 20",
            arrayOf("%$keyword%")
        )
        val results = mutableListOf<Pair<String, Int>>()
        while (cursor.moveToNext()) {
            results.add(cursor.getString(0) to cursor.getInt(1))
        }
        cursor.close()
        return results
    }

    fun getWord(word: String): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT frequency FROM $TABLE WHERE word = ? LIMIT 1",
            arrayOf(word)
        )
        val freq = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        return freq
    }

    fun bumpFrequency(word: String) {
        writableDatabase.execSQL(
            "UPDATE $TABLE SET frequency = frequency + 1, version = ? WHERE word = ?",
            arrayOf(System.currentTimeMillis(), word)
        )
    }

    fun countWords(): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE", null
        )
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        return count
    }
}

class VocabSyncManager(
    private val context: Context,
    private val localStore: LocalVocabStore
) {
    suspend fun syncIfNeeded() {
        val maxVersion = withContext(Dispatchers.IO) {
            localStore.getMaxVersion()
        }

        var hasMore = true
        var cursor = maxVersion

        while (hasMore) {
            val response: ApiResponse<VocabListResponse> = ApiClient.vocabApiService.list(1, 500)
            response.data?.let { data ->
                withContext(Dispatchers.IO) {
                    localStore.upsertBatch(data.items)
                }
                hasMore = data.items.size >= 500
            } ?: run { hasMore = false }
        }
    }
}
