package com.cabeceira.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ReadingStatus(val label: String) {
    READING("Lendo"),
    WANT_TO_READ("Quero ler"),
    FINISHED("Lidos"),
    ABANDONED("Abandonados"),
}

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Autores separados por vírgula. */
    val authors: String,
    val isbn: String? = null,
    val pageCount: Int,
    val currentPage: Int = 0,
    val status: ReadingStatus = ReadingStatus.WANT_TO_READ,
    val coverUrl: String? = null,
    val description: String? = null,
    val publishedDate: String? = null,
    /** Ordem na fila de "Quero ler" (menor = lê antes). */
    val queuePosition: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
)

val Book.remainingPages: Int get() = (pageCount - currentPage).coerceAtLeast(0)
val Book.progress: Float get() = if (pageCount > 0) (currentPage.toFloat() / pageCount).coerceIn(0f, 1f) else 0f
val Book.firstAuthor: String? get() = authors.split(",").map { it.trim() }.firstOrNull { it.isNotEmpty() }

@Entity(
    tableName = "reading_sessions",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("bookId")],
)
data class ReadingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val date: Long = System.currentTimeMillis(),
    val pagesRead: Int,
    val minutes: Int,
)
