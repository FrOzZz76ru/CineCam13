package com.cinecam13

import android.graphics.Bitmap
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object LutProcessor {

    data class CubeLut(
        val size: Int,
        val values: FloatArray
    )

    enum class Style {
        // Исходные 11 стилей
        NAT,
        KODACHROME_64,
        PORTRA_400,
        PORTRA_800,
        FUJI_FILM,
        FUJI_PORTRAIT,
        CINE,
        TEAL_ORANGE,
        MOODY_AQUA,
        WARM,
        COOL,

        // Новые 14 стилей
        PORTRA_160,
        KODAK_GOLD_200,
        KODAK_EKTAR_100,
        FUJI_PRO_400H,
        FUJI_VELVIA,
        CINEMATIC_TEAL,
        BLEACH_BYPASS,
        VINTAGE_FILM,
        MATTE_FILM,
        BLACK_AND_WHITE,
        NOIR,
        GOLDEN_HOUR,
        NIGHT_CINEMA,
        ARCTIC_BLUE
    }

    private val cache = HashMap<Style, CubeLut>()

    /**
     * Создаёт процедурный LUT.
     *
     * Важно: это авторские цветовые стили, вдохновлённые
     * характером плёнки, а не официальные LUT Kodak или Fujifilm.
     */
    @Synchronized
    fun createStyle(
        style: Style,
        size: Int = 33
    ): CubeLut {
        require(size >= 2) {
            "Размер LUT должен быть не меньше 2"
        }

        if (size == 33) {
            cache[style]?.let { return it }
        }

        val lut = generateLut(style, size)

        if (size == 33) {
            cache[style] = lut
        }

        return lut
    }

    private fun generateLut(
        style: Style,
        size: Int
    ): CubeLut {
        val values = FloatArray(size * size * size * 3)
        var index = 0

        for (bIndex in 0 until size) {
            val b = bIndex.toFloat() / (size - 1)

            for (gIndex in 0 until size) {
                val g = gIndex.toFloat() / (size - 1)

                for (rIndex in 0 until size) {
                    val r = rIndex.toFloat() / (size - 1)

                    val result = transform(r, g, b, style)

                    values[index++] = result[0].coerceIn(0f, 1f)
                    values[index++] = result[1].coerceIn(0f, 1f)
                    values[index++] = result[2].coerceIn(0f, 1f)
                }
            }
        }

        return CubeLut(size, values)
    }

    /**
     * Применяет LUT с полной интенсивностью.
     * Сохраняет совместимость с прежними вызовами.
     */
    fun apply(
        source: Bitmap,
        lut: CubeLut
    ): Bitmap {
        return apply(source, lut, 1f)
    }

    /**
     * Применяет LUT с регулируемой интенсивностью.
     *
     * intensity:
     * 0.0 = оригинальное изображение
     * 0.5 = половина эффекта
     * 1.0 = полный эффект
     */
    fun apply(
        source: Bitmap,
        lut: CubeLut,
        intensity: Float
    ): Bitmap {
        require(lut.size >= 2) {
            "Размер LUT должен быть не меньше 2"
        }

        require(lut.values.size == lut.size * lut.size * lut.size * 3) {
            "Неверный размер данных LUT"
        }

        val amount = intensity.coerceIn(0f, 1f)

        if (amount <= 0f) {
            return source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }

        val width = source.width
        val height = source.height

        val pixels = IntArray(width * height)
        source.getPixels(
            pixels,
            0,
            width,
            0,
            0,
            width,
            height
        )

        val result = IntArray(pixels.size)

        for (i in pixels.indices) {
            val pixel = pixels[i]

            val alpha = (pixel ushr 24) and 0xFF

            val originalR = ((pixel ushr 16) and 0xFF) / 255f
            val originalG = ((pixel ushr 8) and 0xFF) / 255f
            val originalB = (pixel and 0xFF) / 255f

            val filtered = sample(
                originalR,
                originalG,
                originalB,
                lut
            )

            val r = mix(originalR, filtered[0], amount)
            val g = mix(originalG, filtered[1], amount)
            val b = mix(originalB, filtered[2], amount)

            result[i] =
                (alpha shl 24) or
                (toByte(r) shl 16) or
                (toByte(g) shl 8) or
                toByte(b)
        }

        return Bitmap.createBitmap(
            result,
            width,
            height,
            Bitmap.Config.ARGB_8888
        )
    }

    /**
     * Загружает LUT из стандартного .cube-файла.
     */
    fun loadCube(input: InputStream): CubeLut {
        val rows = ArrayList<FloatArray>()
        var lutSize: Int? = null

        input.bufferedReader().use { reader ->
            reader.forEachLine { originalLine ->
                val line = originalLine
                    .substringBefore("#")
                    .trim()

                if (line.isEmpty()) {
                    return@forEachLine
                }

                val parts = line.split(Regex("\\s+"))
                if (parts.isEmpty()) {
                    return@forEachLine
                }

                when (parts[0].uppercase()) {
                    "TITLE",
                    "DOMAIN_MIN",
                    "DOMAIN_MAX",
                    "LUT_1D_SIZE" -> {
                        // Эти директивы не используются в 3D LUT.
                        return@forEachLine
                    }

                    "LUT_3D_SIZE" -> {
                        require(parts.size >= 2) {
                            "В LUT_3D_SIZE отсутствует значение"
                        }

                        lutSize = parts[1].toIntOrNull()
                            ?: throw IllegalArgumentException(
                                "Неверный размер LUT_3D_SIZE"
                            )

                        require(lutSize!! >= 2) {
                            "Размер LUT должен быть не меньше 2"
                        }

                        return@forEachLine
                    }
                }

                if (parts.size >= 3) {
                    val red = parts[0].toFloatOrNull()
                    val green = parts[1].toFloatOrNull()
                    val blue = parts[2].toFloatOrNull()

                    if (red != null && green != null && blue != null) {
                        rows.add(
                            floatArrayOf(
                                red.coerceIn(0f, 1f),
                                green.coerceIn(0f, 1f),
                                blue.coerceIn(0f, 1f)
                            )
                        )
                    }
                }
            }
        }

        val size = lutSize
            ?: throw IllegalArgumentException(
                "В .cube-файле не найден LUT_3D_SIZE"
            )

        val expectedRows = size * size * size

        require(rows.size == expectedRows) {
            "Неверное количество цветовых записей: " +
                "ожидалось $expectedRows, получено ${rows.size}"
        }

        val values = FloatArray(expectedRows * 3)
        var index = 0

        for (row in rows) {
            values[index++] = row[0]
            values[index++] = row[1]
            values[index++] = row[2]
        }

        return CubeLut(size, values)
    }

    fun loadCube(file: File): CubeLut {
        return file.inputStream().use { input ->
            loadCube(input)
        }
    }

    /**
     * Трёхлинейная интерполяция внутри 3D LUT.
     */
    private fun sample(
        red: Float,
        green: Float,
        blue: Float,
        lut: CubeLut
    ): FloatArray {
        val size = lut.size
        val maxIndex = size - 1

        val rPosition = red.coerceIn(0f, 1f) * maxIndex
        val gPosition = green.coerceIn(0f, 1f) * maxIndex
        val bPosition = blue.coerceIn(0f, 1f) * maxIndex

        val r0 = floor(rPosition).toInt().coerceIn(0, maxIndex)
        val g0 = floor(gPosition).toInt().coerceIn(0, maxIndex)
        val b0 = floor(bPosition).toInt().coerceIn(0, maxIndex)

        val r1 = min(r0 + 1, maxIndex)
        val g1 = min(g0 + 1, maxIndex)
        val b1 = min(b0 + 1, maxIndex)

        val rf = rPosition - r0
        val gf = gPosition - g0
        val bf = bPosition - b0

        val c000 = getColor(lut, r0, g0, b0)
        val c100 = getColor(lut, r1, g0, b0)
        val c010 = getColor(lut, r0, g1, b0)
        val c110 = getColor(lut, r1, g1, b0)

        val c001 = getColor(lut, r0, g0, b1)
        val c101 = getColor(lut, r1, g0, b1)
        val c011 = getColor(lut, r0, g1, b1)
        val c111 = getColor(lut, r1, g1, b1)

        val output = FloatArray(3)

        for (channel in 0..2) {
            val c00 = mix(c000[channel], c100[channel], rf)
            val c10 = mix(c010[channel], c110[channel], rf)
            val c01 = mix(c001[channel], c101[channel], rf)
            val c11 = mix(c011[channel], c111[channel], rf)

            val c0 = mix(c00, c10, gf)
            val c1 = mix(c01, c11, gf)

            output[channel] = mix(c0, c1, bf)
                .coerceIn(0f, 1f)
        }

        return output
    }

    private fun getColor(
        lut: CubeLut,
        r: Int,
        g: Int,
        b: Int
    ): FloatArray {
        val index = ((b * lut.size + g) * lut.size + r) * 3

        return floatArrayOf(
            lut.values[index],
            lut.values[index + 1],
            lut.values[index + 2]
        )
    }

    private fun transform(
        inputR: Float,
        inputG: Float,
        inputB: Float,
        style: Style
    ): FloatArray {
        var r = inputR
        var g = inputG
        var b = inputB

        val luminance =
            0.2126f * r +
            0.7152f * g +
            0.0722f * b

        val saturation = when (style) {
            Style.NAT -> 1.00f
            Style.KODACHROME_64 -> 1.17f
            Style.PORTRA_400 -> 0.91f
            Style.PORTRA_800 -> 0.88f
            Style.FUJI_FILM -> 1.12f
            Style.FUJI_PORTRAIT -> 0.86f
            Style.CINE -> 0.92f
            Style.TEAL_ORANGE -> 1.18f
            Style.MOODY_AQUA -> 0.90f
            Style.WARM -> 1.04f
            Style.COOL -> 1.04f

            Style.PORTRA_160 -> 0.88f
            Style.KODAK_GOLD_200 -> 1.18f
            Style.KODAK_EKTAR_100 -> 1.32f
            Style.FUJI_PRO_400H -> 0.83f
            Style.FUJI_VELVIA -> 1.42f
            Style.CINEMATIC_TEAL -> 1.12f
            Style.BLEACH_BYPASS -> 0.48f
            Style.VINTAGE_FILM -> 0.78f
            Style.MATTE_FILM -> 0.86f
            Style.BLACK_AND_WHITE -> 0f
            Style.NOIR -> 0f
            Style.GOLDEN_HOUR -> 1.08f
            Style.NIGHT_CINEMA -> 0.94f
            Style.ARCTIC_BLUE -> 0.96f
        }

        r = luminance + (r - luminance) * saturation
        g = luminance + (g - luminance) * saturation
        b = luminance + (b - luminance) * saturation

        when (style) {
            Style.NAT -> {
                r = contrast(r, 1.02f, 0.50f)
                g = contrast(g, 1.02f, 0.50f)
                b = contrast(b, 1.02f, 0.50f)
            }

            Style.KODACHROME_64 -> {
                r = contrast(r, 1.12f, 0.48f) + 0.035f
                g = contrast(g, 1.05f, 0.48f) + 0.008f
                b = contrast(b, 1.10f, 0.48f) - 0.025f

                r += luminance * 0.035f
                b -= luminance * 0.025f
            }

            Style.PORTRA_400 -> {
                r = contrast(r, 0.96f, 0.48f) + 0.035f
                g = contrast(g, 0.96f, 0.48f) + 0.018f
                b = contrast(b, 0.94f, 0.48f) + 0.012f

                r += (1f - luminance) * 0.012f
            }

            Style.PORTRA_800 -> {
                r = contrast(r, 0.92f, 0.48f) + 0.045f
                g = contrast(g, 0.93f, 0.48f) + 0.018f
                b = contrast(b, 0.91f, 0.48f) + 0.005f

                b -= luminance * 0.012f
            }

            Style.FUJI_FILM -> {
                r = contrast(r, 1.06f, 0.48f) + 0.006f
                g = contrast(g, 1.07f, 0.48f) + 0.018f
                b = contrast(b, 1.09f, 0.48f) + 0.022f

                g += (1f - luminance) * 0.008f
            }

            Style.FUJI_PORTRAIT -> {
                r = contrast(r, 0.95f, 0.48f) + 0.025f
                g = contrast(g, 0.96f, 0.48f) + 0.018f
                b = contrast(b, 0.97f, 0.48f) + 0.020f
            }

            Style.CINE -> {
                r = contrast(r, 1.08f, 0.48f) + 0.018f
                g = contrast(g, 1.04f, 0.48f) + 0.004f
                b = contrast(b, 1.08f, 0.48f) + 0.018f

                b += (1f - luminance) * 0.018f
                r += luminance * 0.012f
            }

            Style.TEAL_ORANGE -> {
                r += (luminance - 0.35f) * 0.10f
                g += (1f - luminance) * 0.008f
                b += (0.55f - luminance) * 0.12f
            }

            Style.MOODY_AQUA -> {
                r = contrast(r, 0.94f, 0.46f) - 0.012f
                g = contrast(g, 0.98f, 0.46f) + 0.010f
                b = contrast(b, 1.04f, 0.46f) + 0.035f

                b += (1f - luminance) * 0.025f
            }

            Style.WARM -> {
                r += 0.055f
                g += 0.018f
                b -= 0.035f
            }

            Style.COOL -> {
                r -= 0.025f
                g += 0.006f
                b += 0.045f
            }

            Style.PORTRA_160 -> {
                r = contrast(r, 0.97f, 0.48f) + 0.026f
                g = contrast(g, 0.97f, 0.48f) + 0.018f
                b = contrast(b, 0.95f, 0.48f) + 0.016f

                r += luminance * 0.008f
            }

            Style.KODAK_GOLD_200 -> {
                r = contrast(r, 1.08f, 0.47f) + 0.045f
                g = contrast(g, 1.04f, 0.47f) + 0.022f
                b = contrast(b, 1.03f, 0.47f) - 0.018f

                r += luminance * 0.025f
                g += luminance * 0.008f
            }

            Style.KODAK_EKTAR_100 -> {
                r = contrast(r, 1.13f, 0.48f) + 0.012f
                g = contrast(g, 1.13f, 0.48f) + 0.006f
                b = contrast(b, 1.15f, 0.48f) + 0.008f

                g += (1f - luminance) * 0.008f
            }

            Style.FUJI_PRO_400H -> {
                r = contrast(r, 0.94f, 0.49f) + 0.018f
                g = contrast(g, 0.97f, 0.49f) + 0.026f
                b = contrast(b, 0.98f, 0.49f) + 0.025f

                g += (1f - luminance) * 0.012f
            }

            Style.FUJI_VELVIA -> {
                r = contrast(r, 1.18f, 0.48f) + 0.006f
                g = contrast(g, 1.20f, 0.48f) + 0.012f
                b = contrast(b, 1.22f, 0.48f) + 0.018f

                g += luminance * 0.008f
            }

            Style.CINEMATIC_TEAL -> {
                r = contrast(r, 1.08f, 0.46f) + luminance * 0.025f
                g = contrast(g, 1.02f, 0.46f) + 0.008f
                b = contrast(b, 1.10f, 0.46f) + 0.035f

                b += (1f - luminance) * 0.025f
                r += luminance * 0.025f
            }

            Style.BLEACH_BYPASS -> {
                r = contrast(r, 1.28f, 0.48f)
                g = contrast(g, 1.28f, 0.48f)
                b = contrast(b, 1.28f, 0.48f)

                r = mix(r, luminance, 0.15f)
                g = mix(g, luminance, 0.15f)
                b = mix(b, luminance, 0.15f)
            }

            Style.VINTAGE_FILM -> {
                r = contrast(r, 0.90f, 0.46f) + 0.045f
                g = contrast(g, 0.89f, 0.46f) + 0.025f
                b = contrast(b, 0.86f, 0.46f) + 0.005f

                r += luminance * 0.015f
                b -= luminance * 0.012f
            }

            Style.MATTE_FILM -> {
                r = contrast(r, 0.82f, 0.50f) + 0.055f
                g = contrast(g, 0.82f, 0.50f) + 0.055f
                b = contrast(b, 0.82f, 0.50f) + 0.055f

                r += 0.012f
                g += 0.008f
                b += 0.012f
            }

            Style.BLACK_AND_WHITE -> {
                r = luminance
                g = luminance
                b = luminance
            }

            Style.NOIR -> {
                val mono = contrast(luminance, 1.45f, 0.46f)

                r = mono
                g = mono
                b = mono
            }

            Style.GOLDEN_HOUR -> {
                r = contrast(r, 1.04f, 0.48f) + 0.075f
                g = contrast(g, 1.02f, 0.48f) + 0.035f
                b = contrast(b, 0.98f, 0.48f) - 0.035f

                r += luminance * 0.025f
            }

            Style.NIGHT_CINEMA -> {
                r = contrast(r, 1.10f, 0.40f) - 0.010f
                g = contrast(g, 1.06f, 0.40f) + 0.006f
                b = contrast(b, 1.12f, 0.40f) + 0.035f

                b += (1f - luminance) * 0.035f

                r *= 0.94f
                g *= 0.96f
                b *= 0.99f
            }

            Style.ARCTIC_BLUE -> {
                r = contrast(r, 1.02f, 0.48f) - 0.025f
                g = contrast(g, 1.04f, 0.48f) + 0.008f
                b = contrast(b, 1.08f, 0.48f) + 0.055f

                b += (1f - luminance) * 0.018f
            }
        }

        return floatArrayOf(
            r.coerceIn(0f, 1f),
            g.coerceIn(0f, 1f),
            b.coerceIn(0f, 1f)
        )
    }

    private fun contrast(
        value: Float,
        amount: Float,
        midpoint: Float
    ): Float {
        return (value - midpoint) * amount + midpoint
    }

    private fun mix(
        a: Float,
        b: Float,
        amount: Float
    ): Float {
        return a + (b - a) * amount
    }

    private fun toByte(value: Float): Int {
        return (value.coerceIn(0f, 1f) * 255f)
            .toInt()
            .coerceIn(0, 255)
    }
}
