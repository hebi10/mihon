package kr.hebi.comicreader

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ComicPage(
    val uri: Uri,
    val filename: String,
)

internal data class ComicChapter(
    val title: String,
    val pages: List<ComicPage>,
)

internal data class ComicBook(
    val id: String,
    val title: String,
    val rootUri: Uri,
    val chapters: List<ComicChapter>,
) {
    val pages: List<ComicPage> = chapters.flatMap { it.pages }
}

internal enum class ReadingMode { PAGED, WEBTOON }
internal enum class PageDirection { LEFT_TO_RIGHT, RIGHT_TO_LEFT }
internal enum class ScreenOrientation { AUTO, PORTRAIT, LANDSCAPE }

/**
 * All state and URI access remain on the device. No network clients are used.
 */
internal class ComicRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("offline_comic_reader", Context.MODE_PRIVATE)

    fun roots(): List<Uri> = preferences.getStringSet("selected_folders", emptySet())
        .orEmpty()
        .map(Uri::parse)
        .sortedBy { it.toString() }

    fun addRoot(uri: Uri) {
        val updated = roots().map(Uri::toString).toMutableSet().apply { add(uri.toString()) }
        preferences.edit().putStringSet("selected_folders", updated).apply()
    }

    fun removeRoot(uri: Uri) {
        val updated = roots().map(Uri::toString).toMutableSet().apply { remove(uri.toString()) }
        preferences.edit().putStringSet("selected_folders", updated).apply()
        // Removing a bookmark never deletes the user's files.
    }

    fun rootTitle(uri: Uri): String = DocumentFile.fromTreeUri(context, uri)?.name
        ?: uri.lastPathSegment.orEmpty()

    fun progress(book: ComicBook): Int = preferences.getInt("page_${book.id}", 0)
        .coerceIn(0, (book.pages.size - 1).coerceAtLeast(0))

    fun saveProgress(book: ComicBook, pageIndex: Int) {
        preferences.edit().putInt("page_${book.id}", pageIndex).apply()
    }

    fun readMode(): ReadingMode = getEnum("reading_mode", ReadingMode.PAGED)
    fun direction(): PageDirection = getEnum("page_direction", PageDirection.RIGHT_TO_LEFT)
    fun orientation(): ScreenOrientation = getEnum("screen_orientation", ScreenOrientation.AUTO)

    fun saveMode(value: ReadingMode) = preferences.edit().putString("reading_mode", value.name).apply()
    fun saveDirection(value: PageDirection) = preferences.edit().putString("page_direction", value.name).apply()
    fun saveOrientation(value: ScreenOrientation) = preferences.edit().putString("screen_orientation", value.name).apply()

    private inline fun <reified T : Enum<T>> getEnum(key: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == preferences.getString(key, fallback.name) } ?: fallback

    suspend fun scanBooks(): List<ComicBook> = withContext(Dispatchers.IO) {
        roots().flatMap { uri ->
            try {
                val selected = DocumentFile.fromTreeUri(context, uri)
                if (selected?.isDirectory == true && selected.canRead()) {
                    scanSelectedFolder(selected, uri)
                } else {
                    emptyList()
                }
            } catch (_: SecurityException) {
                emptyList()
            }
        }.distinctBy(ComicBook::id).sortedWith { first, second ->
            compareNatural(first.title, second.title)
        }
    }

    private fun scanSelectedFolder(folder: DocumentFile, sourceUri: Uri): List<ComicBook> {
        val items = folder.listFiles()
        val childFolders = items.filter(DocumentFile::isDirectory)
            .sortedWith { a, b -> compareNatural(a.name.orEmpty(), b.name.orEmpty()) }
        val directImages = imagePages(items)

        if (directImages.isNotEmpty()) {
            return listOfNotNull(createBook(folder, sourceUri, items))
        }

        if (childFolders.isEmpty()) return emptyList()

        val chapters = childFolders.all {
            looksLikeChapter(it.name.orEmpty()) && imagePages(it.listFiles()).isNotEmpty()
        }
        if (chapters) {
            return listOfNotNull(createBook(folder, sourceUri, items))
        }

        return childFolders.mapNotNull { createBook(it, sourceUri) }
    }

    private fun createBook(
        folder: DocumentFile,
        sourceUri: Uri,
        preloaded: Array<DocumentFile>? = null,
    ): ComicBook? {
        val children = preloaded ?: folder.listFiles()
        val chapters = mutableListOf<ComicChapter>()
        val directPages = imagePages(children)

        if (directPages.isNotEmpty()) {
            chapters += ComicChapter("본편", directPages)
        }

        children.filter(DocumentFile::isDirectory)
            .sortedWith { a, b -> compareNatural(a.name.orEmpty(), b.name.orEmpty()) }
            .forEach { chapterFolder ->
                val pages = imagePages(chapterFolder.listFiles())
                if (pages.isNotEmpty()) {
                    chapters += ComicChapter(chapterFolder.name ?: "회차", pages)
                }
            }

        if (chapters.isEmpty()) return null
        return ComicBook(
            id = folder.uri.toString(),
            title = folder.name ?: "제목 없음",
            rootUri = sourceUri,
            chapters = chapters,
        )
    }

    private fun imagePages(files: Array<DocumentFile>): List<ComicPage> =
        files.asSequence()
            .filter { it.isFile && isSupportedImage(it.name.orEmpty()) }
            .sortedWith { a, b -> compareNatural(a.name.orEmpty(), b.name.orEmpty()) }
            .map { ComicPage(it.uri, it.name.orEmpty()) }
            .toList()

    private fun isSupportedImage(name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "heif")
    }

    private fun looksLikeChapter(name: String): Boolean =
        name.matches(Regex("(?i)^(chapter|ch|episode|ep|part|vol|volume|제?\\d+|\\d+).*$"))
}

/**
 * Natural order for image filenames: 1.jpg, 2.jpg, 10.jpg.
 * Compare numeric runs as strings to avoid integer overflow.
 */
internal fun compareNatural(first: String, second: String): Int {
    var left = 0
    var right = 0

    while (left < first.length && right < second.length) {
        val a = first[left]
        val b = second[right]
        if (a.isDigit() && b.isDigit()) {
            val startLeft = left
            val startRight = right
            while (left < first.length && first[left].isDigit()) left++
            while (right < second.length && second[right].isDigit()) right++
            val digitsLeft = first.substring(startLeft, left).trimStart('0').ifEmpty { "0" }
            val digitsRight = second.substring(startRight, right).trimStart('0').ifEmpty { "0" }
            val sizeCompare = digitsLeft.length.compareTo(digitsRight.length)
            if (sizeCompare != 0) return sizeCompare
            val valueCompare = digitsLeft.compareTo(digitsRight)
            if (valueCompare != 0) return valueCompare
            continue
        }

        val charCompare = a.lowercaseChar().compareTo(b.lowercaseChar())
        if (charCompare != 0) return charCompare
        left++
        right++
    }
    return first.length.compareTo(second.length)
}
