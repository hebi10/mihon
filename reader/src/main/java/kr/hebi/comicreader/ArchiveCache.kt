package kr.hebi.comicreader

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Reads ZIP/CBZ without altering the source archive. Image entries are streamed to
 * an app-private, bounded cache and the app never transfers content to a server.
 */
internal class ArchiveCache(private val context: Context) {
    private val cacheRoot = File(context.cacheDir, "comic_archive_pages")

    fun readPages(archive: DocumentFile): List<ComicPage> {
        require(isArchive(archive.name.orEmpty()))
        val identity = "${archive.uri}|${archive.length()}|${archive.lastModified()}"
        val cacheKey = sha256(identity)
        val directory = File(cacheRoot, cacheKey)
        val manifest = File(directory, "pages.json")

        if (manifest.exists()) {
            readManifest(manifest, directory)?.let { cached ->
                if (cached.isNotEmpty()) return cached
            }
        }

        directory.deleteRecursively()
        check(directory.mkdirs() || directory.isDirectory) { "압축 파일 캐시 생성에 실패했습니다." }

        try {
            val extracted = mutableListOf<Pair<String, File>>()
            val buffer = ByteArray(64 * 1024)
            var totalBytes = 0L
            var allEntries = 0
            context.contentResolver.openInputStream(archive.uri).use { input ->
                requireNotNull(input) { "압축 파일을 열 수 없습니다." }
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        allEntries++
                        check(allEntries <= MAX_ENTRIES) { "압축 파일 항목이 너무 많습니다." }

                        if (!entry.isDirectory && isImageFilename(entry.name)) {
                            check(extracted.size < MAX_IMAGES) { "이미지 수가 너무 많습니다." }
                            check(entry.size <= MAX_ENTRY_BYTES) { "너무 큰 이미지가 포함되어 있습니다." }

                            // Numeric output names prevent archive path traversal and file overwrite.
                            val extension = entry.name.substringAfterLast('.').lowercase()
                            val file = File(directory, "${extracted.size.toString().padStart(5, '0')}.$extension")
                            FileOutputStream(file).use { output ->
                                var entryBytes = 0L
                                while (true) {
                                    val read = zip.read(buffer)
                                    if (read < 0) break
                                    entryBytes += read
                                    totalBytes += read
                                    check(entryBytes <= MAX_ENTRY_BYTES && totalBytes <= MAX_TOTAL_BYTES) {
                                        "압축 파일의 이미지 용량이 허용 범위를 초과했습니다."
                                    }
                                    output.write(buffer, 0, read)
                                }
                            }
                            extracted += entry.name to file
                        }
                        zip.closeEntry()
                    }
                }
            }

            val sorted = extracted.sortedWith { first, second -> compareNatural(first.first, second.first) }
            check(sorted.isNotEmpty()) { "압축 파일에 지원되는 이미지가 없습니다." }
            val array = JSONArray()
            sorted.forEach { (name, file) ->
                array.put(JSONObject().put("name", name).put("file", file.name))
            }
            manifest.writeText(array.toString(), Charsets.UTF_8)
            return sorted.map { (name, file) -> ComicPage(Uri.fromFile(file), name) }
        } catch (e: Exception) {
            directory.deleteRecursively()
            throw e
        }
    }

    private fun readManifest(manifest: File, directory: File): List<ComicPage>? = try {
        val array = JSONArray(manifest.readText(Charsets.UTF_8))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val name = item.getString("name")
                val fileName = item.getString("file")
                require(fileName.matches(Regex("[0-9]{5}\\.[a-z0-9]+")))
                val file = File(directory, fileName)
                require(file.isFile)
                add(ComicPage(Uri.fromFile(file), name))
            }
        }
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val MAX_ENTRIES = 5_000
        private const val MAX_IMAGES = 1_500
        private const val MAX_ENTRY_BYTES = 50L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 400L * 1024 * 1024

        fun isArchive(name: String): Boolean =
            name.endsWith(".cbz", ignoreCase = true) || name.endsWith(".zip", ignoreCase = true)

        fun isImageFilename(name: String): Boolean =
            name.substringAfterLast('.', "").lowercase() in
                setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "heif")

        private fun sha256(source: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(source.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
