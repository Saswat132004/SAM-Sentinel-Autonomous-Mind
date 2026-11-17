package com.sam.assistant.knowledge.memory

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalFactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFact(fact: PersonalFactEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFacts(facts: List<PersonalFactEntity>)

    @Update
    suspend fun updateFact(fact: PersonalFactEntity)

    @Delete
    suspend fun deleteFact(fact: PersonalFactEntity)

    @Query("DELETE FROM personal_facts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM personal_facts WHERE factId = :factId")
    suspend fun deleteByFactId(factId: String): Int

    @Query("DELETE FROM personal_facts WHERE LOWER(subject) = LOWER(:subject)")
    suspend fun deleteBySubject(subject: String): Int

    @Query("SELECT * FROM personal_facts WHERE id = :id")
    suspend fun getFactById(id: Long): PersonalFactEntity?

    @Query("SELECT * FROM personal_facts WHERE factId = :factId")
    suspend fun getFactByFactId(factId: String): PersonalFactEntity?

    @Query("SELECT * FROM personal_facts ORDER BY updatedAt DESC")
    fun getAllFacts(): Flow<List<PersonalFactEntity>>

    @Query("SELECT * FROM personal_facts ORDER BY updatedAt DESC")
    suspend fun getAllFactsSync(): List<PersonalFactEntity>

    @Query("SELECT * FROM personal_facts WHERE category = :category ORDER BY updatedAt DESC")
    fun getFactsByCategory(category: String): Flow<List<PersonalFactEntity>>

    @Query("SELECT * FROM personal_facts WHERE LOWER(subject) LIKE '%' || LOWER(:subject) || '%' ORDER BY updatedAt DESC")
    suspend fun getFactsBySubject(subject: String): List<PersonalFactEntity>

    @Query("SELECT * FROM personal_facts WHERE LOWER(subject) LIKE '%' || LOWER(:query) || '%' OR LOWER(content) LIKE '%' || LOWER(:query) || '%' OR LOWER(tags) LIKE '%' || LOWER(:query) || '%' ORDER BY updatedAt DESC")
    suspend fun searchFacts(query: String): List<PersonalFactEntity>

    @Query("DELETE FROM personal_facts")
    suspend fun clearAllFacts()
}
