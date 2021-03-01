package com.example.project1.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.Image
import android.util.Log
import android.view.View
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import java.io.IOException


class TextRecognizer(
    private val textFoundListener: (String) -> Unit
) : ImageAnalysis.Analyzer {

    @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        imageProxy.image?.let { process(it, imageProxy) }
    }

    private fun process(image: Image, imageProxy: ImageProxy) {
        try {
            readTextFromImage(InputImage.fromMediaImage(image, 90), imageProxy)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun readTextFromImage(image: InputImage, imageProxy: ImageProxy) {
        TextRecognition.getClient()
            .process(image)
            .addOnSuccessListener { visionText ->
                processTextFromImage(visionText, imageProxy)
                val rect = RectF(visionText.textBlocks.firstOrNull()?.lines?.firstOrNull()?.boundingBox)
            }
            .addOnFailureListener { error ->
                error.printStackTrace()
            }
        imageProxy.close()
    }
    private fun processTextFromImage(visionText: Text, imageProxy: ImageProxy) {
        for (block in visionText.textBlocks) {
            // You can access whole block of text using block.text
            for (line in block.lines) {
                // You can access whole line of text using line.text
                for (element in line.elements) {
                    textFoundListener(element.text)
                    Log.d(TextRecognizer::class.java.simpleName, element.text)
                }
            }
        }
    }
}