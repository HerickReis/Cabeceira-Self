package com.cabeceira.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** Livro vindo de uma API pública, ainda não salvo na estante. */
data class RemoteBook(
    val title: String,
    val authors: List<String>,
    val isbn: String?,
    val pageCount: Int?,
    val coverUrl: String?,
    val description: String?,
    val publishedDate: String?,
) {
    val authorsText: String get() = authors.joinToString(", ")
}

/**
 * Busca livros em APIs públicas e gratuitas:
 *  - Google Books (principal: bom acervo em português e traz número de páginas)
 *  - Open Library (fallback quando o Google não encontra)
 */
class BookApi(
    private val client: OkHttpClient,
    private val googleApiKey: String = "",
) {

    suspend fun searchByTitle(title: String): List<RemoteBook> = withFallback {
        google("intitle:$title").ifEmpty { google(title) }.ifEmpty { openLibrarySearch("title", title) }
    }

    suspend fun searchByAuthor(author: String): List<RemoteBook> = withFallback {
        google("inauthor:\"$author\"").ifEmpty { openLibrarySearch("author", author) }
    }

    suspend fun findByIsbn(rawIsbn: String): List<RemoteBook> {
        val isbn = normalizeIsbn(rawIsbn)
        return withFallback {
            google("isbn:$isbn").ifEmpty { brasilApiIsbn(isbn) }.ifEmpty { openLibraryIsbn(isbn) }
        }
    }

    /**
     * Procura uma capa quando a fonte do livro não trouxe nenhuma (comum na BrasilAPI):
     *  1. Open Library Covers pelo ISBN exato;
     *  2. Open Library por título + autor — pode trazer a capa de outra edição.
     */
    suspend fun findCover(isbn: String?, title: String, author: String?): String? = withContext(Dispatchers.IO) {
        isbn?.let(::normalizeIsbn)?.takeIf { it.isNotEmpty() }?.let { n ->
            val url = "https://covers.openlibrary.org/b/isbn/$n-M.jpg"
            val req = Request.Builder().url("$url?default=false").head().build()
            val exists = runCatching { client.newCall(req).execute().use { it.isSuccessful } }.getOrDefault(false)
            if (exists) return@withContext url
        }
        val search = "https://openlibrary.org/search.json".toHttpUrl().newBuilder()
            .addQueryParameter("title", title)
            .apply { if (!author.isNullOrBlank()) addQueryParameter("author", author) }
            .addQueryParameter("limit", "5")
            .addQueryParameter("fields", "cover_i")
            .build()
        val docs = runCatching { getJson(search) }.getOrNull()?.optJSONArray("docs")?.objects().orEmpty()
        docs.firstNotNullOfOrNull { d -> d.optInt("cover_i", 0).takeIf { it > 0 } }
            ?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" }
    }

    /**
     * A Google Books sem chave costuma estourar a cota (HTTP 429). Nesse caso a busca
     * segue pela Open Library; só reporta erro se nada for encontrado em nenhuma das duas.
     */
    private suspend fun withFallback(block: suspend () -> List<RemoteBook>): List<RemoteBook> {
        googleRateLimited = false
        val result = block()
        if (result.isEmpty() && googleRateLimited) {
            throw ApiException(
                "A Google Books atingiu o limite de buscas e a Open Library não encontrou nada. " +
                    "Tente outra grafia ou configure uma chave da Google Books."
            )
        }
        return result
    }

    @Volatile private var googleRateLimited = false

    // ---------------- Google Books ----------------

    private suspend fun google(query: String): List<RemoteBook> {
        if (googleRateLimited) return emptyList() // não insiste se já levou 429 nesta busca
        val url = "https://www.googleapis.com/books/v1/volumes".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("maxResults", "30")
            .addQueryParameter("printType", "books")
            .apply { if (googleApiKey.isNotBlank()) addQueryParameter("key", googleApiKey) }
            .build()
        val json = try {
            getJson(url)
        } catch (e: RateLimitException) {
            googleRateLimited = true
            null
        } ?: return emptyList()
        val items = json.optJSONArray("items") ?: return emptyList()
        return items.objects().mapNotNull { item ->
            val info = item.optJSONObject("volumeInfo") ?: return@mapNotNull null
            val title = info.optStringOrNull("title") ?: return@mapNotNull null
            val subtitle = info.optStringOrNull("subtitle")
            val identifiers = info.optJSONArray("industryIdentifiers")?.objects().orEmpty()
            val isbn = identifiers.firstOrNull { it.optString("type") == "ISBN_13" }?.optString("identifier")
                ?: identifiers.firstOrNull { it.optString("type") == "ISBN_10" }?.optString("identifier")
            RemoteBook(
                title = if (subtitle != null) "$title: $subtitle" else title,
                authors = info.optJSONArray("authors")?.strings().orEmpty(),
                isbn = isbn,
                pageCount = info.optInt("pageCount", 0).takeIf { it > 0 },
                coverUrl = info.optJSONObject("imageLinks")?.let {
                    it.optStringOrNull("thumbnail") ?: it.optStringOrNull("smallThumbnail")
                }?.replace("http://", "https://"),
                description = info.optStringOrNull("description"),
                publishedDate = info.optStringOrNull("publishedDate"),
            )
        }.distinctBy { it.title.lowercase() + "|" + it.authorsText.lowercase() }
    }

    // ---------------- BrasilAPI (ISBN de livros brasileiros, sem chave) ----------------

    private suspend fun brasilApiIsbn(isbn: String): List<RemoteBook> {
        val url = "https://brasilapi.com.br/api/isbn/v1/$isbn".toHttpUrl()
        val data = runCatching { getJson(url) }.getOrNull() ?: return emptyList()
        val title = data.optStringOrNull("title") ?: return emptyList()
        val subtitle = data.optStringOrNull("subtitle")
        return listOf(
            RemoteBook(
                title = if (subtitle != null) "$title: $subtitle" else title,
                authors = data.optJSONArray("authors")?.strings().orEmpty(),
                isbn = isbn,
                pageCount = data.optInt("page_count", 0).takeIf { it > 0 },
                coverUrl = data.optStringOrNull("cover_url")?.replace("http://", "https://"),
                description = data.optStringOrNull("synopsis"),
                publishedDate = data.optInt("year", 0).takeIf { it > 0 }?.toString(),
            )
        )
    }

    // ---------------- Open Library ----------------

    private suspend fun openLibrarySearch(field: String, value: String): List<RemoteBook> {
        val url = "https://openlibrary.org/search.json".toHttpUrl().newBuilder()
            .addQueryParameter(field, value)
            .addQueryParameter("limit", "30")
            .addQueryParameter(
                "fields",
                "title,author_name,isbn,number_of_pages_median,cover_i,first_publish_year"
            )
            .build()
        val json = getJson(url) ?: return emptyList()
        return json.optJSONArray("docs")?.objects().orEmpty().mapNotNull { doc ->
            val title = doc.optStringOrNull("title") ?: return@mapNotNull null
            val isbns = doc.optJSONArray("isbn")?.strings().orEmpty()
            RemoteBook(
                title = title,
                // Open Library mistura tradutores/ilustradores; mantém só os primeiros nomes
                authors = doc.optJSONArray("author_name")?.strings().orEmpty().distinct().take(2),
                isbn = isbns.firstOrNull { it.length == 13 } ?: isbns.firstOrNull(),
                pageCount = doc.optInt("number_of_pages_median", 0).takeIf { it > 0 },
                coverUrl = doc.optInt("cover_i", 0).takeIf { it > 0 }
                    ?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" },
                description = null,
                publishedDate = doc.optInt("first_publish_year", 0).takeIf { it > 0 }?.toString(),
            )
        }
    }

    private suspend fun openLibraryIsbn(isbn: String): List<RemoteBook> {
        val url = "https://openlibrary.org/api/books".toHttpUrl().newBuilder()
            .addQueryParameter("bibkeys", "ISBN:$isbn")
            .addQueryParameter("format", "json")
            .addQueryParameter("jscmd", "data")
            .build()
        val data = getJson(url)?.optJSONObject("ISBN:$isbn") ?: return emptyList()
        val title = data.optStringOrNull("title") ?: return emptyList()
        return listOf(
            RemoteBook(
                title = title,
                authors = data.optJSONArray("authors")?.objects().orEmpty()
                    .mapNotNull { it.optStringOrNull("name") },
                isbn = isbn,
                pageCount = data.optInt("number_of_pages", 0).takeIf { it > 0 },
                coverUrl = data.optJSONObject("cover")?.let {
                    it.optStringOrNull("medium") ?: it.optStringOrNull("large")
                },
                description = null,
                publishedDate = data.optStringOrNull("publish_date"),
            )
        )
    }

    // ---------------- helpers ----------------

    private suspend fun getJson(url: HttpUrl): JSONObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 429) throw RateLimitException()
                return@withContext null
            }
            response.body?.string()?.let { JSONObject(it) }
        }
    }

    companion object {
        fun normalizeIsbn(raw: String): String = raw.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()

        /** Códigos de barras de livros são EAN-13 começando com 978/979 (ou ISBN-10). */
        fun looksLikeIsbn(raw: String): Boolean {
            val n = normalizeIsbn(raw)
            return (n.length == 13 && (n.startsWith("978") || n.startsWith("979"))) || n.length == 10
        }
    }
}

class ApiException(message: String) : Exception(message)
private class RateLimitException : Exception("HTTP 429")

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
