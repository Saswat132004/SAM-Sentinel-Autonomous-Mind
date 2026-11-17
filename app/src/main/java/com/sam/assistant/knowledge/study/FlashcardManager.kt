package com.sam.assistant.knowledge.study

class FlashcardManager(private val dao: FlashcardDao) {

    companion object {
        // Spaced repetition intervals: 1 day, 3 days, 7 days, 14 days, 30 days
        private val INTERVAL_MS = listOf(
            86400000L,       // Level 1: 1 day
            259200000L,      // Level 2: 3 days
            604800000L,      // Level 3: 7 days
            1209600000L,     // Level 4: 14 days
            2592000000L      // Level 5: 30 days
        )
    }

    suspend fun createFlashcard(
        deckName: String,
        question: String,
        answer: String,
        topic: String,
        difficulty: String = "INTERMEDIATE"
    ): Long {
        val card = FlashcardEntity(
            deckName = deckName,
            question = question,
            answer = answer,
            topic = topic,
            difficulty = difficulty,
            reviewCount = 0,
            masteryLevel = 0,
            nextReviewTimestamp = System.currentTimeMillis()
        )
        return dao.insertFlashcard(card)
    }

    suspend fun recordReview(cardId: Long, wasCorrect: Boolean): FlashcardEntity? {
        val card = dao.getFlashcardsByDeck("").find { it.id == cardId }
            ?: dao.getDueFlashcards(Long.MAX_VALUE).find { it.id == cardId }
            ?: return null

        val newMastery = if (wasCorrect) {
            (card.masteryLevel + 1).coerceAtMost(5)
        } else {
            (card.masteryLevel - 1).coerceAtLeast(0)
        }

        val intervalIndex = (newMastery - 1).coerceIn(0, INTERVAL_MS.size - 1)
        val nextReview = System.currentTimeMillis() + INTERVAL_MS[intervalIndex]

        val updated = card.copy(
            reviewCount = card.reviewCount + 1,
            masteryLevel = newMastery,
            nextReviewTimestamp = nextReview
        )
        dao.updateFlashcard(updated)
        return updated
    }

    suspend fun getDueCards(): List<FlashcardEntity> {
        return dao.getDueFlashcards(System.currentTimeMillis())
    }

    suspend fun getCardsByDeck(deckName: String): List<FlashcardEntity> {
        return dao.getFlashcardsByDeck(deckName)
    }

    suspend fun deleteCard(cardId: Long) {
        dao.deleteById(cardId)
    }

    suspend fun deleteDeck(deckName: String) {
        dao.deleteByDeck(deckName)
    }
}
