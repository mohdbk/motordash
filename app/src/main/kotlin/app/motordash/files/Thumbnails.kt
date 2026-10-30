package app.motordash.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Small previews for the file list: photos, video frames and APK icons. Cached in memory. */
class Thumbnails(private val context: Context) {
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Null for other file types, or when the file can't be decoded. */
    suspend fun get(file: File, size: Int): Bitmap? {
        val key = "${file.path}|${file.lastModified()}|$size"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) { runCatching { make(file, size) }.getOrNull() }?.also { cache.put(key, it) }
    }

    @Suppress("DEPRECATION")
    private fun make(file: File, size: Int): Bitmap? {
        val mime = mimeTypeOf(file)
        return when {
            mime.startsWith("image/") ->
                if (Build.VERSION.SDK_INT >= 29) ThumbnailUtils.createImageThumbnail(file, Size(size, size), null)
                else BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize(file, size) })
                    ?.let { ThumbnailUtils.extractThumbnail(it, size, size) }
            mime.startsWith("video/") ->
                if (Build.VERSION.SDK_INT >= 29) ThumbnailUtils.createVideoThumbnail(file, Size(size, size), null)
                else ThumbnailUtils.createVideoThumbnail(file.path, MediaStore.Images.Thumbnails.MINI_KIND)
            file.extension.equals("apk", ignoreCase = true) -> context.packageManager.getPackageArchiveInfo(file.path, 0)?.applicationInfo?.let { app ->
                app.sourceDir = file.path
                app.publicSourceDir = file.path
                app.loadIcon(context.packageManager).toBitmap(size, size)
            }
            else -> null
        }
    }

    /** Power-of-two downsampling so big photos don't have to be decoded at full size. */
    private fun sampleSize(file: File, size: Int): Int {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
        return sample
    }
}
