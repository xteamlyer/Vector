package org.matrix.vector.manager.ui.screens.web

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches a WebView subresource through the shared client, or refuses it.
 *
 * Handed to `StoreHtmlPane` as its `fetchSubresource` so the shared renderer's images get the app's
 * DoH resolver and disk cache, and so anything that is not an image over http(s) is refused. It does
 * not stop a README from reporting the reader's IP to a host it points at — only proxying would, and
 * there is nothing to proxy through — but it does bound what can be loaded.
 *
 * Runs on a WebView background thread, so the blocking call is correct here. The returned stream is
 * closed by the WebView, which is what releases the connection; closing the response here instead
 * would truncate the image.
 */
fun fetchStoreSubresource(
    client: OkHttpClient,
    request: WebResourceRequest,
): WebResourceResponse? {
    val url = request.url ?: return blocked()
    when (url.scheme?.lowercase()) {
        // Inline images and the document itself. Inert, and nothing to fetch.
        "data", "about", null -> return null
        "http", "https" -> Unit
        else -> return blocked()
    }
    if (!request.method.equals("GET", ignoreCase = true)) return blocked()

    return try {
        val response = client.newCall(Request.Builder().url(url.toString()).build()).execute()
        val type = response.body.contentType()
        if (!response.isSuccessful || type?.type != "image") {
            response.close()
            blocked()
        } else {
            WebResourceResponse("${type.type}/${type.subtype}", "UTF-8", response.body.byteStream())
        }
    } catch (_: Exception) {
        blocked()
    }
}

/**
 * A 1×1 transparent GIF. A refused subresource should render as nothing, not as a broken image.
 *
 * It has to be a real, decodable image. An empty body, or one under a type no `<img>` can decode,
 * is a decode failure, and WebKit answers that with its broken-image glyph and the alt text beside
 * it — which is what a README linking a chart that has since started 404ing would show.
 */
private fun blocked() =
    WebResourceResponse("image/gif", null, ByteArrayInputStream(TRANSPARENT_GIF))

private val TRANSPARENT_GIF =
    byteArrayOf(
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61, // GIF89a
        0x01, 0x00, 0x01, 0x00, // 1 × 1
        0x80.toByte(), 0x00, 0x00, // global colour table present, background 0, square pixels
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // the table: two entries, both black
        0x21, 0xF9.toByte(), 0x04, 0x01, 0x00, 0x00, 0x00, 0x00, // colour 0 is transparent
        0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, // image descriptor
        0x02, 0x02, 0x44, 0x01, 0x00, // one pixel of LZW
        0x3B, // trailer
    )
