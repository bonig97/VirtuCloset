package com.example.virtucloset.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageUtil {
    fun saveImageToStorage(context: Context, bitmap: Bitmap, fileName: String = "image_${System.currentTimeMillis()}"): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        try {
            context.contentResolver.openOutputStream(uri ?: return null).use { outputStream ->
                if (!outputStream?.let { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }!!) {
                    throw IOException("Failed to save bitmap to output stream")
                }
            }
            Log.d("ImageStorageUtil", "Image saved to storage: $uri")
            return uri
        } catch (e: Exception) {
            uri?.let { orphanUri ->
                context.contentResolver.delete(orphanUri, null, null)
            }
            Log.e("ImageStorageUtil", "Failed to save image to storage", e)
            return null
        }
    }

    fun createImageFile(context: Context): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_$timeStamp"
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        return File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        ).apply {
            val currentPhotoPath = absolutePath
            Log.d("MainActivity", "Current photo path: $currentPhotoPath")
        }
    }

    fun rotateSavedImageIfNeeded(context: Context, imageUri: Uri): Bitmap? {
        val rotationDegrees = when (getExifOrientation(context, imageUri)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        if (rotationDegrees != 0f) {
            val imageStream = context.contentResolver.openInputStream(imageUri) ?: return null
            val bitmap = BitmapFactory.decodeStream(imageStream)
            imageStream.close()

            return bitmap?.let {
                rotateImage(it, rotationDegrees)
            }
        }
        return null
    }

    private fun rotateImage(originalBitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
    }

    private fun getExifOrientation(context: Context, imageUri: Uri): Int {
        val inputStream = context.contentResolver.openInputStream(imageUri)
        return try {
            val exifInterface = inputStream?.let { ExifInterface(it) }
            exifInterface?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: IOException) {
            Log.e("getExifOrientation", "Failed to read image EXIF orientation", e)
            ExifInterface.ORIENTATION_NORMAL
        } finally {
            inputStream?.close()
        }
    }
}