package ai.smartico.fakecasino

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.decode.ImageSource
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.runInterruptible
import java.nio.ByteBuffer

/**
 * Animated AVIF ("avis") decoder.
 *
 * Coil hands GIF, animated WebP and animated HEIF to the platform ImageDecoder,
 * but it does not recognise AVIF sequences — they fall through to the still
 * decoder, which is exactly why the slot sprites rendered one frozen frame.
 * The platform decoder plays them fine, so this factory claims the file by its
 * ftyp brand and decodes it into an AnimatedImageDrawable (Compose starts any
 * Animatable drawable itself).
 */
class AnimatedAvifDecoder(private val source: ImageSource) : Decoder {

    override suspend fun decode(): DecodeResult = runInterruptible {
        val bytes = source.source().readByteArray()
        val drawable = ImageDecoder.decodeDrawable(
            ImageDecoder.createSource(ByteBuffer.wrap(bytes)),
        ) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        if (drawable is AnimatedImageDrawable) {
            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        }
        DecodeResult(drawable = drawable, isSampled = false)
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceResult, options: Options, imageLoader: ImageLoader): Decoder? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null // AVIF: API 31+
            // The ftyp box sits at the head of the file; an image sequence lists
            // "avis" as its major or a compatible brand.
            val head = result.source.source().peek().readByteArray(64)
            if (!head.contains(AVIS)) return null
            return AnimatedAvifDecoder(result.source)
        }

        private companion object {
            val AVIS = "avis".toByteArray()

            fun ByteArray.contains(needle: ByteArray): Boolean {
                outer@ for (i in 0..size - needle.size) {
                    for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
                    return true
                }
                return false
            }
        }
    }
}
