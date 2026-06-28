package com.pdfviewerapp.sunuy.services

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object ReaderViewFilterFactory {

    fun getPaintForTheme(theme: ReadingTheme): Paint {
        val paint = Paint()
        
        when (theme) {
            is ReadingTheme.Night -> {
                paint.colorFilter = ColorMatrixColorFilter(getInversionMatrix(0.8f))
            }
            is ReadingTheme.OledForest -> {
                val greenOledMatrix = ColorMatrix(floatArrayOf(
                    -0.5f,  0.0f,  0.0f, 0.0f, 0f,
                     0.0f, -1.0f,  0.0f, 0.0f, 180f,
                     0.0f,  0.0f, -0.5f, 0.0f, 0f,
                     0.0f,  0.0f,  0.0f, 1.0f, 0f
                ))
                paint.colorFilter = ColorMatrixColorFilter(greenOledMatrix)
            }
            is ReadingTheme.Sepia -> {
                val sepiaMatrix = ColorMatrix(floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0.0f, 0.0f,
                    0.349f, 0.686f, 0.168f, 0.0f, 0.0f,
                    0.272f, 0.534f, 0.131f, 0.0f, 0.0f,
                    0.0f,   0.0f,   0.0f,   1.0f, 0.0f
                ))
                paint.colorFilter = ColorMatrixColorFilter(sepiaMatrix)
            }
            is ReadingTheme.Solarized -> {
                val solarMatrix = ColorMatrix(floatArrayOf(
                    0.85f, 0.0f,  0.0f,  0.0f, 30f,
                    0.0f,  0.85f, 0.0f,  0.0f, 25f,
                    0.0f,  0.0f,  0.75f, 0.0f, 15f,
                    0.0f,  0.0f,  0.0f,  1.0f, 0.0f
                ))
                paint.colorFilter = ColorMatrixColorFilter(solarMatrix)
            }
            is ReadingTheme.Nord -> {
                val nordMatrix = ColorMatrix(floatArrayOf(
                    -0.7f,  0.0f,  0.0f,  0.0f, 60f,
                     0.0f, -0.7f,  0.0f,  0.0f, 80f,
                     0.0f,  0.0f, -0.9f,  0.0f, 120f,
                     0.0f,  0.0f,  0.0f,  1.0f, 0.0f
                ))
                paint.colorFilter = ColorMatrixColorFilter(nordMatrix)
            }
            else -> {
                paint.colorFilter = null
            }
        }
        return paint
    }

    private fun getInversionMatrix(saturation: Float): ColorMatrix {
        val invert = ColorMatrix(floatArrayOf(
            -1.0f,  0.0f,  0.0f, 0.0f, 255f,
             0.0f, -1.0f,  0.0f, 0.0f, 255f,
             0.0f,  0.0f, -1.0f, 0.0f, 255f,
             0.0f,  0.0f,  0.0f, 1.0f,   0f
        ))
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(saturation)
        invert.postConcat(satMatrix)
        return invert
    }
}
