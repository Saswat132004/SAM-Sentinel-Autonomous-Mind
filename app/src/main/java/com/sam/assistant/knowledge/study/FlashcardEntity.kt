package com.sam.assistant.knowledge.study

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "flashcards")
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deckName: String,
    val question: String,
    val answer: String,
    val topic: String,
    val difficulty: String = "INTERMEDIATE", // "BEGINNER", "INTERMEDIATE", "ADVANCED"
    val reviewCount: Int = 0,
    val masteryLevel: Int = 0, // 0 to 5
    val nextReviewTimestamp: Long = System.currentTimeMillis()
)

@Dao
interface FlashcardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcard(card: FlashcardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcards(cards: List<FlashcardEntity>)

    @Update
    suspend fun updateFlashcard(card: FlashcardEntity)

    @Delete
    suspend fun deleteFlashcard(card: FlashcardEntity)

    @Query("SELECT * FROM flashcards ORDER BY id DESC")
    fun getAllFlashcards(): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE deckName = :deckName ORDER BY id ASC")
    suspend fun getFlashcardsByDeck(deckName: String): List<FlashcardEntity>

    @Query("SELECT * FROM flashcards WHERE nextReviewTimestamp <= :currentTimestamp ORDER BY masteryLevel ASC")
    suspend fun getDueFlashcards(currentTimestamp: Long = System.currentTimeMillis()): List<FlashcardEntity>

    @Query("DELETE FROM flashcards WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM flashcards WHERE deckName = :deckName")
    suspend fun deleteByDeck(deckName: String)

    @Query("DELETE FROM flashcards")
    suspend fun clearAll()
}
