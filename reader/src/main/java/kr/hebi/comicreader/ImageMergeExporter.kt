package kr.hebi.comicreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

internal enum class MergeDirection { VERTICAL, HORIZONTAL }

internal class ImageMergeExporter(private val context: Context) {
    private data class ImageSize(val width: Int, val height: Int)

    /**
     * Combines the current chapter into a single JPEG without modifying input files.
     * Intentionally refuses oversized outputs to prevent out-of-memory crashes.
     */
    suspend fun save(
        pages: List<ComicPage>,
        direction: MergeDirection,
        destination: Uri,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(pages.isNotEmpty()) { "합칠 이미지가 없습니다." }

            val sizes = pages.map { imageBounds(it.uri) }
            val draws = sizes.map { size ->
                if (direction == MergeDirection.VERTICAL) {
                    val width = MAX_PAGE_WIDTH.coerceAtMost(size.width)
                    width to max(1, (size.height.toDouble() * width / size.width).roundToInt())
                } else {
                    val height = MAX_PAGE_HEIGHT.coerceAtMost(size.height)
                    max(1, (size.width.toDouble() * height / size.height).roundToInt()) to height
                }
            }
            val outputWidth: Long
            val outputHeight: Long
            if (direction == MergeDirection.VERTICAL) {
                outputWidth = draws.maxOf { it.first }.toLong()
                outputHeight = draws.sumOf { it.second.toLong() }
            } else {
                outputWidth = draws.sumOf { it.first.toLong() }
                outputHeight = draws.maxOf { it.second }.toLong()
            }

            require(outputWidth > 0 && outputHeight > 0 &&
                outputWidth <= MAX_DIMENSION && outputHeight <= MAX_DIMENSION &&
                outputWidth * outputHeight <= MAX_PIXELS
            ) {
                "합친 이미지가 너무 큽니다. 이미지 수가 적은 회차를 선택해 주세요."
            }

            val result = Bitmap.createBitmap(
                outputWidth.toInt(),
                outputHeight.toInt(),
                Bitmap.Config.ARGB_8888,
            )
            try {
                val canvas = Canvas(result)
                canvas.drawColor(Color.WHITE)
                var position = 0
                pages.forEachIndexed { index, page ->
                    val (width, height) = draws[index]
                    val sourceSize = sizes[index]
                    val sample = sampleSize(sourceSize, width, height)
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    val image = context.contentResolver.openInputStream(page.uri)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    } ?: error("이미지를 읽을 수 없습니다: ${page.filename}")

                    try {
                        val targetLeft = if (direction == MergeDirection.VERTICAL) {
                            (outputWidth.toInt() - width) / 2
                        } else {
                            position
                        }
                        val targetTop = if (direction == MergeDirection.VERTICAL) {
                            position
                        } else {
                            (outputHeight.toInt() - height) / 2
                        }
                        canvas.drawBitmap(
                            image,
                            null,
                            Rect(targetLeft, targetTop, targetLeft + width, targetTop + height),
                            null,
                        )
                        position += if (direction == MergeDirection.VERTICAL) height else width
                    } finally {
                        image.recycle()
                    }
                }

                context.contentResolver.openOutputStream(destination, "wt")?.use { out ->
                    check(result.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                        "이미지 저장에 실패했습니다."
                    }
                    out.flush()
                } ?: error("선택한 위치에 파일을 저장할 수 없습니다.")

                "현재 회차의 이미지 ${pages.size}장을 합쳐 저장했습니다."
            } finally {
                result.recycle()
            }
        }.recoverCatching { error ->
            if (error is OutOfMemoryError) {
                throw IllegalStateException("이미지를 합치기 위한 메모리가 부족합니다.", error)
            }
            throw error
        }
    }

    private fun imageBounds(uri: Uri): ImageSize {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("이미지를 열 수 없습니다.")
        require(options.outWidth > 0 && options.outHeight > 0) {
            "이미지 크기를 읽을 수 없습니다."
        }
        return ImageSize(options.outWidth, options.outHeight)
    }

    private fun sampleSize(source: ImageSize, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (source.width / (sample * 2) >= targetWidth &&
            source.height / (sample * 2) >= targetHeight &&
            sample < 128
        ) {
            sample *= 2
        }
        return sample
    }

    companion object {
        private const val MAX_PAGE_WIDTH = 1440
        private const val MAX_PAGE_HEIGHT = 1600
        private const val MAX_DIMENSION = 16384
        private const val MAX_PIXELS = 24_000_000L
    }
}
