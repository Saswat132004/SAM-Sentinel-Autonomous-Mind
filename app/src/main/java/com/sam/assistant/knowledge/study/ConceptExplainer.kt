package com.sam.assistant.knowledge.study

enum class ExplanationLevel {
    BEGINNER,
    DETAILED,
    CODE_ALGORITHM
}

data class ConceptExplanation(
    val concept: String,
    val level: ExplanationLevel,
    val explanationText: String,
    val keyPoints: List<String>,
    val practicalExample: String? = null
)

class ConceptExplainer {

    fun explain(concept: String, level: ExplanationLevel = ExplanationLevel.DETAILED): ConceptExplanation {
        val lower = concept.lowercase().trim()

        return when {
            lower.contains("dijkstra") -> ConceptExplanation(
                concept = "Dijkstra's Algorithm",
                level = level,
                explanationText = "Dijkstra's algorithm finds the shortest path from a starting node to all other nodes in a weighted graph with non-negative edge weights.",
                keyPoints = listOf(
                    "Greedy algorithm strategy using a priority queue (Min-Heap)",
                    "Maintains a set of visited vertices and minimum tentative distances",
                    "Time Complexity: O((V + E) log V) with adjacency lists and binary heap",
                    "Fails on graphs with negative edge weights (requires Bellman-Ford instead)"
                ),
                practicalExample = "Used in GPS navigation systems and OSPF network routing to compute minimum-cost paths."
            )

            lower.contains("quicksort") || lower.contains("quick sort") -> ConceptExplanation(
                concept = "QuickSort",
                level = level,
                explanationText = "QuickSort is a highly efficient divide-and-conquer sorting algorithm that selects a pivot element and partitions the array into sub-arrays.",
                keyPoints = listOf(
                    "Picks a pivot element (Lomuto or Hoare partitioning)",
                    "Partitions array so elements smaller than pivot precede it, and larger elements follow it",
                    "Recursively sorts left and right sub-arrays",
                    "Average Time Complexity: O(n log n); Worst Case: O(n^2) if pivot selection is unbalanced",
                    "In-place sorting with O(log n) auxiliary stack space"
                ),
                practicalExample = "Standard sorting primitive used in C qsort() and Java dual-pivot primitives."
            )

            lower.contains("virtual memory") || lower.contains("paging") -> ConceptExplanation(
                concept = "Virtual Memory & Paging",
                level = level,
                explanationText = "Virtual memory provides an idealized, contiguous address space to processes by mapping virtual addresses to physical RAM via page tables and the MMU.",
                keyPoints = listOf(
                    "Splits memory into fixed-size blocks called Pages (typically 4 KB) and Page Frames",
                    "The Memory Management Unit (MMU) uses the Page Table and TLB for fast hardware translation",
                    "Page Fault occurs when a referenced page is not present in physical RAM and must be swapped from storage",
                    "Provides process isolation, memory protection, and prevents memory fragmentation"
                ),
                practicalExample = "Enables running applications whose memory requirements exceed available physical RAM."
            )

            lower.contains("concurrency") || lower.contains("deadlock") -> ConceptExplanation(
                concept = "Concurrency & Deadlock Prevention",
                level = level,
                explanationText = "Concurrency involves multiple execution sequences running in overlapping time periods. Deadlock happens when processes cannot proceed because each holds a resource the other needs.",
                keyPoints = listOf(
                    "Four Coffman conditions: Mutual Exclusion, Hold and Wait, No Preemption, Circular Wait",
                    "Prevention strategies: Strict hierarchical resource ordering breaks circular wait",
                    "Synchronization primitives: Mutex, Semaphores, Lock-Free Atomics"
                ),
                practicalExample = "Database transactions acquiring lock IDs in strict ascending order to prevent deadlocks."
            )

            else -> ConceptExplanation(
                concept = concept,
                level = level,
                explanationText = "Technical analysis of \"$concept\": As an on-device personal assistant, SAM analyzes core architectural principles, computational complexity, and modular trade-offs.",
                keyPoints = listOf(
                    "Encapsulated modular design",
                    "Predictable offline performance",
                    "Transparent resource management"
                ),
                practicalExample = "Applied in scalable smartphone software engineering."
            )
        }
    }
}
