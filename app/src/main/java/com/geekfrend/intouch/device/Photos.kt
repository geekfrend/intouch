package com.geekfrend.intouch.device

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.util.LruCache

/** Contact photos from the address book, downsampled and cached so the grid and widgets stay light. */
object Photos {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Muted tiles for people without a photo, picked by name so each person keeps their color. */
    val FALLBACK_COLORS = longArrayOf(0xFF2E2A5A, 0xFF3B2650, 0xFF1F3A4A, 0xFF3A3044, 0xFF253B3A, 0xFF2A3350)

    fun fallbackColor(name: String): Long = FALLBACK_COLORS[Math.floorMod(name.hashCode(), FALLBACK_COLORS.size)]

    /** Soft pastels for the light (Monument) theme, behind dark initials. */
    val FALLBACK_COLORS_LIGHT = longArrayOf(0xFFF7C9B6, 0xFFCDBEE6, 0xFFB9DDD2, 0xFFEAD8C8, 0xFFF3D3AE, 0xFFD6E4F0)

    fun fallbackColorLight(name: String): Long =
        FALLBACK_COLORS_LIGHT[Math.floorMod(name.hashCode(), FALLBACK_COLORS_LIGHT.size)]

    fun initials(name: String): String =
        name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

    /** Loads [uri] scaled so its shorter side is about [maxPx]. Null if there is no photo or it can't be read. */
    fun load(context: Context, uri: String?, maxPx: Int): Bitmap? {
        if (uri.isNullOrBlank()) return null
        val key = "$uri@$maxPx"
        cache.get(key)?.let { return it }
        return runCatching {
            val resolver = context.contentResolver
            val parsed = Uri.parse(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            val shorter = minOf(bounds.outWidth, bounds.outHeight)
            while (shorter > 0 && shorter / (sample * 2) >= maxPx) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()?.also { cache.put(key, it) }
    }

    /** A round photo (or initials on the person's color) for widgets, which can't clip images themselves. */
    fun circle(context: Context, name: String, uri: String?, px: Int, textColor: Int, light: Boolean = false): Bitmap {
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val photo = load(context, uri, px)
        val r = px / 2f
        if (photo != null) {
            val side = minOf(photo.width, photo.height)
            val square = Bitmap.createBitmap(photo, (photo.width - side) / 2, (photo.height - side) / 2, side, side)
            val scaled = Bitmap.createScaledBitmap(square, px, px, true)
            paint.shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            canvas.drawCircle(r, r, r, paint)
        } else {
            paint.color = (if (light) fallbackColorLight(name) else fallbackColor(name)).toInt()
            canvas.drawCircle(r, r, r, paint)
            paint.color = textColor
            paint.textSize = px * 0.38f
            paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            paint.textAlign = Paint.Align.CENTER
            val y = r - (paint.descent() + paint.ascent()) / 2
            canvas.drawText(initials(name), r, y, paint)
        }
        return out
    }
}
