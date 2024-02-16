package com.example.virtucloset

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.virtucloset.ui.theme.VirtuClosetTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.virtucloset.util.ImageStorageUtil.saveImageToStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VirtuClosetTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageUri = rememberSaveable { mutableStateOf<Uri?>(null) }
    val imageBitmapState = remember { mutableStateOf<Bitmap?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            Log.d("MainActivity", "Image captured successfully")
            imageUri.value?.let { uri ->
                val rotatedBitmap = rotateSavedImageIfNeeded(context, uri)
                val finalBitmap = rotatedBitmap ?: BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
                finalBitmap?.let { bmp ->
                    imageBitmapState.value = bmp
                    scope.launch(Dispatchers.IO) {
                        val savedUri = saveImageToStorage(context, bmp)
                        if (rotatedBitmap != null) {
                            context.contentResolver.delete(uri, null, null)
                        }
                        imageUri.value = savedUri
                    }
                }
            } ?: run {
                Log.e("MainActivity", "URI is null after taking picture")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "VirtuCloset", color = Color.White, fontWeight = FontWeight.Bold) }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CameraButton(takePictureLauncher = takePictureLauncher, imageUri = imageUri, context = context)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            imageBitmapState.value?.let { bitmap ->
                ImageDisplay(bitmap = bitmap)
            } ?: Text("No image captured", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun CameraButton(takePictureLauncher: ActivityResultLauncher<Uri>, imageUri: MutableState<Uri?>, context: Context) {
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val photoFile = createImageFile(context)
            val photoUri = FileProvider.getUriForFile(context, "${context.applicationContext.packageName}.fileprovider", photoFile)
            imageUri.value = photoUri
            takePictureLauncher.launch(photoUri)
        } else {
            Toast.makeText(context, "Camera permission is required to take pictures", Toast.LENGTH_LONG).show()
        }
    }

    Button(onClick = { cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA) }) {
        Text("Take Picture")
    }
}

@Composable
fun ImageDisplay(bitmap: Bitmap) {
    val imageSize = 100.dp
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Captured Image",
        modifier = Modifier
            .size(imageSize)
            .clip(RoundedCornerShape(4.dp)),
        contentScale = ContentScale.Crop
    )
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
        var currentPhotoPath = absolutePath
        Log.d("MainActivity", "Current photo path: $currentPhotoPath")
    }
}

fun rotateSavedImageIfNeeded(context: Context, imageUri: Uri): Bitmap? {
    val inputStream = context.contentResolver.openInputStream(imageUri) ?: return null
    val exifInterface = ExifInterface(inputStream)
    val orientation = exifInterface.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_UNDEFINED
    )
    inputStream.close()

    val rotationDegrees = when (orientation) {
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

fun rotateImage(originalBitmap: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
}