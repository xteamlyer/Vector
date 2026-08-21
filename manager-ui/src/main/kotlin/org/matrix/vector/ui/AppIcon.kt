package org.matrix.vector.ui

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App icons, straight from `PackageManager`.
 *
 * Deliberately not routed through an image-loading library. These are not network images: they come
 * from a local drawable that has to be rasterised anyway, and a manager renders hundreds of them in
 * scrolling lists, so what actually matters is a bounded cache and doing the rasterisation off the
 * main thread. That is this file, and it costs no dependency.
 *
 * Bounded is the point. Decoding every installed package's icon up front is what a manager reaches
 * for first, and it costs a scan that walks every app and a map that never gives the memory back.
 */
object AppIconCache {

    // Twelve megabytes: roughly 150 icons at the 48 dp of a module row on an xxhdpi screen, or 270
    // at the 36 dp of a scope row. Sized by bytes rather than count so a device with a large
    // density does not quietly use several times more memory.
    private val cache = object : LruCache<String, ImageBitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun cached(key: String): ImageBitmap? = cache.get(key)

    /**
     * The cache key for one app at one size.
     *
     * The uid is in the key because an app can be reinstalled or updated with a new icon while the
     * process lives, and the size because the same app drawn at two sizes is two rasterisations.
     * A package parsed out of an apk file has no uid, which is what keeps a module's own icon from
     * colliding with the installed copy of the same package.
     */
    fun keyFor(info: ApplicationInfo, sizePx: Int): String =
        "${info.packageName}:${info.uid}:$sizePx"

    suspend fun load(
        info: ApplicationInfo,
        packageManager: android.content.pm.PackageManager,
        sizePx: Int,
    ): ImageBitmap? = withContext(Dispatchers.IO) { loadBlocking(info, packageManager, sizePx) }

    /**
     * The same, for a caller that is already on a worker thread.
     *
     * Rasterising a drawable is not something to do on the main thread, and this does not move
     * itself off it -- so it is for code that is already off it, such as a package scan.
     */
    fun loadBlocking(
        info: ApplicationInfo,
        packageManager: android.content.pm.PackageManager,
        sizePx: Int,
    ): ImageBitmap? {
        val key = keyFor(info, sizePx)
        cache.get(key)?.let {
            return it
        }
        val bitmap =
            runCatching {
                    val drawable = info.loadIcon(packageManager)
                    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, sizePx, sizePx)
                    drawable.draw(canvas)
                    bmp.asImageBitmap()
                }
                .getOrNull() ?: return null
        cache.put(key, bitmap)
        return bitmap
    }
}

@Composable
fun AppIcon(
    applicationInfo: ApplicationInfo,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val key = AppIconCache.keyFor(applicationInfo, sizePx)

    // Seeded from the cache so an already-loaded icon draws on the first frame and the list does
    // not flicker while scrolling back over rows it has already shown.
    var image by remember(key) { mutableStateOf(AppIconCache.cached(key)) }

    LaunchedEffect(key) {
        if (image == null) {
            image = AppIconCache.load(applicationInfo, context.packageManager, sizePx)
        }
    }

    val bitmap = image
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier.size(size),
        )
    } else {
        // A blank box of the right size, so rows do not resize as icons arrive.
        androidx.compose.foundation.layout.Box(modifier.size(size))
    }
}
