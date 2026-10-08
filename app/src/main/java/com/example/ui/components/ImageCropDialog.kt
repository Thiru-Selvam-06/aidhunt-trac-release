package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.DeepSageGreen
import com.example.ui.theme.ForestGreenHeader
import com.example.ui.theme.SageAccent
import com.example.ui.theme.SageOutline
import com.example.ui.theme.SoftSageGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

@Composable
fun ImageCropDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onCropSaved: (Uri) -> Unit
) {
    val context = LocalContext.current
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Load bitmap in background
    LaunchedEffect(imageUri) {
        isLoading = true
        originalBitmap = withContext(Dispatchers.IO) {
            loadBitmapFromUri(context, imageUri)
        }
        isLoading = false
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Crop, contentDescription = null, tint = DeepSageGreen)
                        Text(
                            text = "Crop & Adjust Photo",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreenHeader
                        )
                    }

                    IconButton(
                        onClick = {
                            scale = 1f
                            rotation = 0f
                            panOffset = Offset.Zero
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = SageAccent)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Crop Viewport Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1E2420))
                        .onSizeChanged { containerSize = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.8f, 4.0f)
                                panOffset += pan
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = SoftSageGreen)
                    } else if (originalBitmap != null) {
                        val bmp = originalBitmap!!
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            val cropRadius = min(canvasWidth, canvasHeight) * 0.44f
                            val cropCenter = Offset(canvasWidth / 2f, canvasHeight / 2f)

                            // 1. Draw transformed bitmap
                            val baseScale = max(canvasWidth / bmp.width.toFloat(), canvasHeight / bmp.height.toFloat())
                            val currentScale = baseScale * scale

                            drawContext.canvas.save()
                            drawContext.canvas.translate(cropCenter.x + panOffset.x, cropCenter.y + panOffset.y)
                            drawContext.canvas.rotate(rotation)
                            drawContext.canvas.scale(currentScale, currentScale)
                            drawContext.canvas.translate(-bmp.width / 2f, -bmp.height / 2f)

                            drawImage(bmp.asImageBitmap())
                            drawContext.canvas.restore()

                            // 2. Draw Vignette & Circular Crop Overlay
                            val overlayPath = Path().apply {
                                fillType = PathFillType.EvenOdd
                                addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                                addOval(Rect(cropCenter.x - cropRadius, cropCenter.y - cropRadius, cropCenter.x + cropRadius, cropCenter.y + cropRadius))
                            }
                            drawPath(overlayPath, color = Color.Black.copy(alpha = 0.65f))

                            // Circle Border & Grid
                            drawCircle(
                                color = SoftSageGreen,
                                radius = cropRadius,
                                center = cropCenter,
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                        }
                    } else {
                        Text("Could not load image", color = Color.White, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Adjustment Controls: Zoom & Rotate
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoftSageGreen.copy(alpha = 0.35f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = null, tint = DeepSageGreen, modifier = Modifier.size(18.dp))
                            Text("Zoom:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ForestGreenHeader)
                        }

                        Slider(
                            value = scale,
                            onValueChange = { scale = it },
                            valueRange = 0.8f..3.5f,
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = DeepSageGreen,
                                activeTrackColor = DeepSageGreen
                            )
                        )

                        IconButton(
                            onClick = { rotation = (rotation + 90f) % 360f },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90°", tint = DeepSageGreen)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons: Cancel & Crop Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isSaving
                    ) {
                        Text("Cancel", color = ForestGreenHeader)
                    }

                    Button(
                        onClick = {
                            if (originalBitmap != null && containerSize.width > 0) {
                                isSaving = true
                                val croppedUri = cropAndSaveBitmap(
                                    context = context,
                                    sourceBitmap = originalBitmap!!,
                                    scale = scale,
                                    panOffset = panOffset,
                                    rotation = rotation,
                                    containerSize = containerSize
                                )
                                isSaving = false
                                if (croppedUri != null) {
                                    Toast.makeText(context, "Profile photo cropped & saved!", Toast.LENGTH_SHORT).show()
                                    onCropSaved(croppedUri)
                                } else {
                                    Toast.makeText(context, "Failed to save cropped photo", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("btn_save_crop_photo"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen),
                        enabled = !isSaving && originalBitmap != null
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Crop & Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Loads a bitmap from Uri safely, downsampling if larger than 1600px.
 */
private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.isMutableRequired = true
                val maxDim = max(info.size.width, info.size.height)
                if (maxDim > 1600) {
                    val sample = maxDim / 1600 + 1
                    decoder.setTargetSampleSize(sample)
                }
            }
        } else {
            val inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val maxDim = max(options.outWidth, options.outHeight)
            var sampleSize = 1
            if (maxDim > 1600) {
                sampleSize = maxDim / 1600 + 1
            }

            val readStream = context.contentResolver.openInputStream(uri)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inMutable = true
            }
            val bmp = BitmapFactory.decodeStream(readStream, null, decodeOptions)
            readStream?.close()
            bmp
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Performs accurate square/circle crop based on viewport coordinates and saves to local app files directory.
 */
private fun cropAndSaveBitmap(
    context: Context,
    sourceBitmap: Bitmap,
    scale: Float,
    panOffset: Offset,
    rotation: Float,
    containerSize: IntSize
): Uri? {
    return try {
        val targetSize = 512 // 512x512 square crop
        val outputBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(outputBitmap)

        val canvasW = containerSize.width.toFloat()
        val canvasH = containerSize.height.toFloat()
        val cropRadius = min(canvasW, canvasH) * 0.44f
        val cropDiameter = cropRadius * 2f

        // Matrix mapping container viewport crop to targetSize
        val matrix = Matrix()

        // 1. Center in target bitmap
        matrix.postTranslate(-canvasW / 2f - panOffset.x, -canvasH / 2f - panOffset.y)

        // 2. Rotate
        if (rotation != 0f) {
            matrix.postRotate(rotation)
        }

        // 3. Scale
        val baseScale = max(canvasW / sourceBitmap.width.toFloat(), canvasH / sourceBitmap.height.toFloat())
        val currentScale = baseScale * scale
        val outputScaleFactor = targetSize / cropDiameter

        // Center the source image
        val sourceMatrix = Matrix()
        val sourceTranslateX = -sourceBitmap.width / 2f
        val sourceTranslateY = -sourceBitmap.height / 2f
        sourceMatrix.postTranslate(sourceTranslateX, sourceTranslateY)
        sourceMatrix.postScale(currentScale, currentScale)
        sourceMatrix.postRotate(rotation)
        sourceMatrix.postTranslate(canvasW / 2f + panOffset.x, canvasH / 2f + panOffset.y)

        // Now transform from canvas coordinates to 0..targetSize
        val viewMatrix = Matrix()
        viewMatrix.postTranslate(-canvasW / 2f, -canvasH / 2f)
        viewMatrix.postScale(outputScaleFactor, outputScaleFactor)
        viewMatrix.postTranslate(targetSize / 2f, targetSize / 2f)

        val finalMatrix = Matrix(sourceMatrix)
        finalMatrix.postConcat(viewMatrix)

        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(sourceBitmap, finalMatrix, paint)

        // Save to internal filesDir
        val imagesDir = File(context.filesDir, "profile_images").apply { mkdirs() }
        val file = File(imagesDir, "profile_crop_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)
        outputBitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
        outputStream.flush()
        outputStream.close()

        Uri.fromFile(file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
