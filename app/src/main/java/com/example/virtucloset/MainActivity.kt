package com.example.virtucloset

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.BottomAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.virtucloset.ui.theme.VirtuClosetTheme
import com.example.virtucloset.util.ImageUtil
import com.example.virtucloset.util.ImageUtil.createImageFile
import com.example.virtucloset.util.ImageUtil.rotateSavedImageIfNeeded
import com.example.virtucloset.util.ImageUtil.saveImageToStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

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
    val savedImageUris = remember { mutableStateOf(setOf<Uri>()) }

    LaunchedEffect(key1 = true) {
        savedImageUris.value = ImageUtil.getImageUris(context)
    }

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
                        savedUri?.let {
                            ImageUtil.saveImageUri(context, it)
                            withContext(Dispatchers.Main) {
                                savedImageUris.value = savedImageUris.value + savedUri
                                imageBitmapState.value = null
                                imageUri.value = null
                            }
                        }
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
                .fillMaxSize()
        ) {
            if (savedImageUris.value.isNotEmpty() || imageBitmapState.value != null) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp)
                ) {
                    items(savedImageUris.value.toList(), key = { uri -> uri.toString() }) { uri ->
                        ImageDisplay(context, uri = uri)
                    }
                }
            } else {
                Text("No image captured", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
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
fun ImageDisplay(context: Context, uri: Uri) {
    val imageSize = 100.dp
    val bitmap = remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                bitmap.value = BitmapFactory.decodeStream(stream)
            }
        } catch (e: FileNotFoundException) {
            Log.e("ImageDisplay", "File not found: $uri", e)
        }
    }
    bitmap.value?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Captured Image",
            modifier = Modifier
                .size(width = imageSize, height = imageSize)
                .border(2.dp, Color.Red)
                .clip(RoundedCornerShape(2.dp)),
            contentScale = ContentScale.Crop
        )
    }
}