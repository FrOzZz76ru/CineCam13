package com.cinecam13

import android.graphics.Bitmap
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object LutProcessor {

    data class CubeLut(
        val size: Int,
        val values: FloatArray
    )

    enum class Style {
        NAT,
        CINE,
        TEAL_ORANGE,
        WARM,
        COOL
    }

    fun loadCube(input: InputStream): CubeLut {
        val points = mutableListOf<Float>()
        var lutSize = 0

        BufferedReader(InputStreamReader(input)).use { reader ->
            reader.forEachLine { original ->
                val line = original.trim()

                if (
                    line.isEmpty() ||
                    line.startsWith("#") ||
                    line.startsWith("TITLE")
                ) {
                    return@forEachLine
                }

                val parts = line.split(Regex("\\s+"))

                when (parts[0].uppercase()) {
                    "LUT_3D_SIZE" -> {
                        lutSize = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    }

                    "DOMAIN_MIN", "DOMAIN_MAX" -> Unit

                    else -> {
                        if (parts.size >= 3) {
                            val r = parts[0].toFloatOrNull()
                            val g = parts[1].toFloatOrNull()
                            val b = parts[2].toFloatOrNull()

                            if (r != null && g != null && b != null) {
                                points.add(r)
                                points.add(g)
                                points.add(b)
                            }
                        }
                    }
                }
            }
        }

        require(lutSize >= 2) { "Invalid LUT size" }

        val expected = lutSize * lutSize * lutSize * 3
        require(points.size == expected) { "Invalid LUT data" }

        return CubeLut(lutSize, points.toFloatArray())
    }

    fun loadCube(file: File): CubeLut =
        file.inputStream().use { loadCube(it) }

    fun apply(source: Bitmap, lut: CubeLut): Bitmap {
        require(!source.isRecycled)
        require(lut.size >= 2)
        require(lut.values.size == lut.size * lut.size * lut.size * 3)

        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)

        source.getPixels(
            pixels, 0, width, 0, 0, width, height
        )

        val output = IntArray(pixels.size)

        for (i in pixels.indices) {
            val p = pixels[i]

            val a = p ushr 24 and 255
            val r = (p ushr 16 and 255) / 255f
            val g = (p ushr 8 and 255) / 255f
            val b = (p and 255) / 255f

            val result = sample(r, g, b, lut)

            output[i] =
                (a shl 24) or
                (toByte(result[0]) shl 16) or
                (toByte(result[1]) shl 8) or
                toByte(result[2])
        }

        return Bitmap.createBitmap(
            output, width, height, Bitmap.Config.ARGB_8888
        )
    }

    private fun sample(
        r: Float,
        g: Float,
        b: Float,
        lut: CubeLut
    ): FloatArray {
        val n = lut.size
        val table = lut.values

        val x = r.coerceIn(0f, 1f) * (n - 1)
        val y = g.coerceIn(0f, 1f) * (n - 1)
        val z = b.coerceIn(0f, 1f) * (n - 1)

        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val z0 = floor(z).toInt()

        val x1 = min(x0 + 1, n - 1)
        val y1 = min(y0 + 1, n - 1)
        val z1 = min(z0 + 1, n - 1)

        val fx = x - x0
        val fy = y - y0
        val fz = z - z0

        fun value(ix: Int, iy: Int, iz: Int, c: Int): Float {
            return table[((iz * n + iy) * n + ix) * 3 + c]
        }

        fun mix(a: Float, b: Float, t: Float): Float =
            a + (b - a) * t

        val result = FloatArray(3)

        for (c in 0..2) {
            val c00 = mix(
                value(x0, y0, z0, c),
                value(x1, y0, z0, c),
                fx
            )

            val c10 = mix(
                value(x0, y1, z0, c),
                value(x1, y1, z0, c),
                fx
            )

            val c01 = mix(
                value(x0, y0, z1, c),
                value(x1, y0, z1, c),
                fx
            )

            val c11 = mix(
                value(x0, y1, z1, c),
                value(x1, y1, z1, c),
                fx
            )

            result[c] = mix(
                mix(c00, c10, fy),
                mix(c01, c11, fy),
                fz
            )
        }

        return result
    }

    private fun toByte(value: Float): Int =
        (value.coerceIn(0f, 1f) * 255f)
            .toInt()
            .coerceIn(0, 255)

    fun createStyle(style: Style, size: Int = 33): CubeLut {
        require(size >= 2)

        val values = FloatArray(size * size * size * 3)
        var offset = 0

        for (bIndex in 0 until size) {
            for (gIndex in 0 until size) {
                for (rIndex in 0 until size) {
                    val r = rIndex.toFloat() / (size - 1)
                    val g = gIndex.toFloat() / (size - 1)
                    val b = bIndex.toFloat() / (size - 1)

                    val rgb = transform(r, g, b, style)

                    values[offset++] = rgb[0].coerceIn(0f, 1f)
                    values[offset++] = rgb[1].coerceIn(0f, 1f)
                    values[offset++] = rgb[2].coerceIn(0f, 1f)
                }
            }
        }

        return CubeLut(size, values)
    }

    private fun transform(
        r0: Float,
        g0: Float,
        b0: Float,
        style: Style
    ): FloatArray {
        if (style == Style.NAT) {
            return floatArrayOf(r0, g0, b0)
        }

        var r = r0
        var g = g0
        var b = b0

        val luma = 0.2126f * r0 + 0.7152f * g0 + 0.0722f * b0

        when (style) {
            Style.NAT -> Unit

            Style.CINE -> {
                // Больше контраста, прохладные тени и тёплые света.
                r = (r0 - 0.45f) * 1.18f + 0.45f
                g = (g0 - 0.45f) * 1.13f + 0.45f
                b = (b0 - 0.45f) * 1.10f + 0.45f

                val shadows = (1f - luma).coerceIn(0f, 1f)
                val highlights = luma.coerceIn(0f, 1f)

                r += highlights * 0.035f
                g += highlights * 0.008f
                b -= highlights * 0.025f

                g += shadows * 0.012f
                b += shadows * 0.035f
            }

            Style.TEAL_ORANGE -> {
                // Бирюзовые тени, тёплые светлые участки.
                val shadows = (1f - luma).coerceIn(0f, 1f)
                val highlights = luma.coerceIn(0f, 1f)

                r = r0 * 1.08f + highlights * 0.075f
                g = g0 * 1.01f + shadows * 0.025f
                b = b0 * 1.08f + shadows * 0.085f

                r -= shadows * 0.025f
                g += highlights * 0.012f
                b -= highlights * 0.055f
            }

            Style.WARM -> {
                // Выраженное тёплое, золотистое тонирование.
                r = r0 * 1.13f + 0.025f
                g = g0 * 1.035f + 0.008f
                b = b0 * 0.84f - 0.008f

                r = (r - 0.45f) * 1.04f + 0.45f
            }

            Style.COOL -> {
                // Холодные сине-голубые оттенки.
                r = r0 * 0.86f - 0.008f
                g = g0 * 1.015f
                b = b0 * 1.15f + 0.025f

                b = (b - 0.45f) * 1.035f + 0.45f
            }
        }

        return floatArrayOf(
            r.coerceIn(0f, 1f),
            g.coerceIn(0f, 1f),
            b.coerceIn(0f, 1f)
        )
    }
}
