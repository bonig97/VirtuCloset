package com.example.virtucloset.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

object ImageStorageUtil {
    fun saveImageToStorage(context: Context, bitmap: Bitmap, fileName: String = "image_${System.currentTimeMillis()}") {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        try {
            context.contentResolver.openOutputStream(uri ?: return).use { outputStream ->
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                }
            }
        } catch (e: Exception) {
            uri?.let { orphanUri ->
                context.contentResolver.delete(orphanUri, null, null)
            }
        }
    }
}