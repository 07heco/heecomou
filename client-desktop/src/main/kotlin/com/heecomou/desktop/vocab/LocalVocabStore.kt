package com.heecomou.desktop.vocab

import com.heecomou.desktop.network.VocabVO
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.util.logging.Level
import java.util.logging.Logger

class LocalVocabStore(dbPath: String = "heecomou-vocab.db") {

    companion object {
        private val LOGGER = Logger.getLogger(LocalVocabStore::class.java.name)
    }

    private val connection: Connection

    init {
        connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        createTableIfNotExists()
    }

    private fun createTableIfNotExists() {
        connection.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS vocab (
                    id INTEGER PRIMARY KEY,
                    user_id INTEGER NOT NULL,
                    word TEXT NOT NULL,
                    pinyin TEXT,
                    category TEXT,
                    frequency INTEGER DEFAULT 1,
                    version INTEGER NOT NULL DEFAULT 1
                )
            """.trimIndent())
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_vocab_word ON vocab(word)")
        }
    }

    fun getMaxVersion(): Long {
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT COALESCE(MAX(version), 0) FROM vocab")
            return if (rs.next()) rs.getLong(1) else 0L
        }
    }

    fun upsertBatch(items: List<VocabVO>) {
        connection.autoCommit = false
        try {
            val sql = """
                INSERT OR REPLACE INTO vocab (id, user_id, word, pinyin, category, frequency, version)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            connection.prepareStatement(sql).use { pstmt ->
                for (item in items) {
                    pstmt.setLong(1, item.id)
                    pstmt.setLong(2, item.userId)
                    pstmt.setString(3, item.word)
                    pstmt.setString(4, item.pinyin)
                    pstmt.setString(5, item.category)
                    pstmt.setInt(6, item.frequency)
                    pstmt.setLong(7, item.version)
                    pstmt.addBatch()
                }
                pstmt.executeBatch()
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            LOGGER.log(Level.WARNING, "Batch upsert failed: ${e.message}", e)
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    fun search(keyword: String, limit: Int = 20): List<Pair<String, Int>> {
        val results = mutableListOf<Pair<String, Int>>()
        val sql = """
            SELECT word, frequency FROM vocab
            WHERE word LIKE ? OR pinyin LIKE ?
            ORDER BY frequency DESC
            LIMIT ?
        """.trimIndent()
        connection.prepareStatement(sql).use { pstmt ->
            pstmt.setString(1, "%$keyword%")
            pstmt.setString(2, "%$keyword%")
            pstmt.setInt(3, limit)
            val rs = pstmt.executeQuery()
            while (rs.next()) {
                results.add(rs.getString("word") to rs.getInt("frequency"))
            }
        }
        return results
    }

    fun getWord(word: String): Int {
        connection.prepareStatement(
            "SELECT frequency FROM vocab WHERE word = ?"
        ).use { pstmt ->
            pstmt.setString(1, word)
            val rs = pstmt.executeQuery()
            return if (rs.next()) rs.getInt("frequency") else 0
        }
    }

    fun bumpFrequency(word: String) {
        connection.prepareStatement(
            "UPDATE vocab SET frequency = frequency + 1, version = ? WHERE word = ?"
        ).use { pstmt ->
            pstmt.setLong(1, System.currentTimeMillis())
            pstmt.setString(2, word)
            pstmt.executeUpdate()
        }
    }

    fun countWords(): Int {
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT COUNT(*) FROM vocab")
            return if (rs.next()) rs.getInt(1) else 0
        }
    }

    fun close() {
        try {
            connection.close()
        } catch (e: Exception) {
            LOGGER.log(Level.WARNING, "Error closing vocab store: ${e.message}", e)
        }
    }
}
