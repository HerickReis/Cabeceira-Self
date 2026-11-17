package com.cabeceira.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY queuePosition ASC, addedAt ASC")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: Long): Flow<Book?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): Book?

    @Query("SELECT * FROM books WHERE isbn = :isbn LIMIT 1")
    suspend fun findByIsbn(isbn: String): Book?

    @Query("SELECT COALESCE(MAX(queuePosition), 0) FROM books")
    suspend fun maxQueuePosition(): Int

    @Insert
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    @Update
    suspend fun updateAll(books: List<Book>)

    @Delete
    suspend fun delete(book: Book)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY date DESC")
    fun observeForBook(bookId: Long): Flow<List<ReadingSession>>

    @Query("SELECT * FROM reading_sessions ORDER BY date DESC")
    fun observeAll(): Flow<List<ReadingSession>>

    @Insert
    suspend fun insert(session: ReadingSession): Long

    @Delete
    suspend fun delete(session: ReadingSession)
}
