package com.cabeceira.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.cabeceira.app.data.local.AppDatabase
import com.cabeceira.app.data.local.Book
import com.cabeceira.app.data.local.ReadingSession
import com.cabeceira.app.data.local.ReadingStatus
import com.cabeceira.app.data.remote.BookApi
import com.cabeceira.app.data.remote.RemoteBook
import com.cabeceira.app.data.local.firstAuthor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class BookRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val api: BookApi,
) {
    private val books = db.bookDao()
    private val sessions = db.sessionDao()

    fun observeBooks(): Flow<List<Book>> = books.observeAll()
    fun observeBook(id: Long): Flow<Book?> = books.observe(id)
    fun observeSessions(bookId: Long): Flow<List<ReadingSession>> = sessions.observeForBook(bookId)
    fun observeAllSessions(): Flow<List<ReadingSession>> = sessions.observeAll()

    // ---- busca remota ----
    suspend fun searchByTitle(q: String) = api.searchByTitle(q)
    suspend fun searchByAuthor(q: String) = api.searchByAuthor(q)
    suspend fun findByIsbn(isbn: String) = api.findByIsbn(isbn)

    suspend fun findSavedByIsbn(isbn: String?): Book? =
        isbn?.let { books.findByIsbn(BookApi.normalizeIsbn(it)) }

    // ---- estante ----
    suspend fun addBook(
        remote: RemoteBook,
        title: String,
        authors: String,
        pageCount: Int,
        status: ReadingStatus,
    ): Long {
        val now = System.currentTimeMillis()
        val book = Book(
            title = title.trim(),
            authors = authors.trim(),
            isbn = remote.isbn?.let(BookApi::normalizeIsbn),
            pageCount = pageCount,
            currentPage = if (status == ReadingStatus.FINISHED) pageCount else 0,
            status = status,
            coverUrl = remote.coverUrl,
            description = remote.description,
            publishedDate = remote.publishedDate,
            queuePosition = books.maxQueuePosition() + 1,
            addedAt = now,
            startedAt = if (status == ReadingStatus.READING) now else null,
            finishedAt = if (status == ReadingStatus.FINISHED) now else null,
        )
        return books.insert(book)
    }

    // ---- capas ----

    /** Se o livro está sem capa, tenta achar uma online. Retorna true se encontrou. */
    suspend fun fillMissingCover(bookId: Long): Boolean {
        val book = books.get(bookId) ?: return false
        if (book.coverUrl != null) return true
        val url = runCatching { api.findCover(book.isbn, book.title, book.firstAuthor) }.getOrNull() ?: return false
        books.get(bookId)?.let { books.update(it.copy(coverUrl = url)) }
        return true
    }

    /** Busca outra capa online, mesmo que já exista uma. */
    suspend fun refreshCover(book: Book): Boolean {
        val url = runCatching { api.findCover(book.isbn, book.title, book.firstAuthor) }.getOrNull() ?: return false
        replaceCover(book, url)
        return true
    }

    /** Copia a imagem escolhida pelo usuário para o armazenamento interno do app. */
    suspend fun setCustomCover(book: Book, uri: Uri): Boolean {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "covers").apply { mkdirs() }
            val dest = File(dir, "${book.id}-${System.currentTimeMillis()}.jpg")
            runCatching {
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
                dest
            }.getOrNull()
        } ?: return false
        replaceCover(book, Uri.fromFile(file).toString())
        return true
    }

    suspend fun removeCover(book: Book) = replaceCover(book, null)

    private suspend fun replaceCover(book: Book, url: String?) {
        deleteLocalCover(book.coverUrl)
        books.update(book.copy(coverUrl = url))
    }

    private suspend fun deleteLocalCover(url: String?) = withContext(Dispatchers.IO) {
        if (url != null && url.startsWith("file:")) Uri.parse(url).path?.let { File(it).delete() }
    }

    suspend fun changeStatus(book: Book, status: ReadingStatus) {
        val now = System.currentTimeMillis()
        books.update(
            book.copy(
                status = status,
                startedAt = if (status == ReadingStatus.READING) book.startedAt ?: now else book.startedAt,
                finishedAt = if (status == ReadingStatus.FINISHED) now else null,
                currentPage = if (status == ReadingStatus.FINISHED) book.pageCount else book.currentPage,
                // ao voltar para a fila, vai para o final
                queuePosition = if (status == ReadingStatus.WANT_TO_READ && book.status != status)
                    books.maxQueuePosition() + 1 else book.queuePosition,
            )
        )
    }

    /** Ajuste manual de progresso (sem registrar tempo). */
    suspend fun updateProgress(book: Book, currentPage: Int, pageCount: Int) {
        val total = pageCount.coerceAtLeast(1)
        val page = currentPage.coerceIn(0, total)
        books.update(book.copy(currentPage = page, pageCount = total))
    }

    /** Registra uma sessão: "parei na página X depois de Y minutos". */
    suspend fun logSession(book: Book, reachedPage: Int, minutes: Int) {
        val page = reachedPage.coerceIn(0, book.pageCount)
        val pagesRead = (page - book.currentPage).coerceAtLeast(0)
        db.withTransaction {
            sessions.insert(ReadingSession(bookId = book.id, pagesRead = pagesRead, minutes = minutes))
            val finished = page >= book.pageCount
            val now = System.currentTimeMillis()
            books.update(
                book.copy(
                    currentPage = page,
                    status = if (finished) ReadingStatus.FINISHED else ReadingStatus.READING,
                    startedAt = book.startedAt ?: now,
                    finishedAt = if (finished) now else book.finishedAt,
                )
            )
        }
    }

    suspend fun deleteSession(session: ReadingSession) = sessions.delete(session)

    /** Salva a nova ordem da fila "Quero ler". */
    suspend fun reorderQueue(ordered: List<Book>) {
        books.updateAll(ordered.mapIndexed { i, b -> b.copy(queuePosition = i + 1) })
    }

    suspend fun delete(book: Book) {
        deleteLocalCover(book.coverUrl)
        books.delete(book)
    }
}
