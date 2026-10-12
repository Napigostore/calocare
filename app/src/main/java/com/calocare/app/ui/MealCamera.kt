package com.calocare.app.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt

/** The guide circle's diameter as a share of the preview's width. The crop uses the same number. */
private const val CIRCLE = 0.88f
private val Guide = Color(0xFF2EE58A)

/**
 * In-app camera with a green guide circle. Only the square around the circle is kept: the user
 * frames the meal inside it, so nothing but food is sent to the AI and paid for.
 */
@Composable
fun MealCamera(onCaptured: (Bitmap) -> Unit, onGallery: () -> Unit, onClose: () -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var busy by remember { mutableStateOf(false) }
    val worker = remember { Executors.newSingleThreadExecutor() }
    val capture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            // The crop is scaled down to a few hundred pixels anyway; a full-resolution shot would only be slower.
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(Size(1600, 1200), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build()
            )
            .build()
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            worker.shutdown()
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        // Bind after layout: the view port (what is visible on screen) is only known then, and
                        // sharing it with the capture is what makes the saved crop match the circle on screen.
                        post {
                            val future = ProcessCameraProvider.getInstance(ctx)
                            future.addListener({
                                runCatching {
                                    val provider = future.get()
                                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
                                    val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(capture)
                                    viewPort?.let { group.setViewPort(it) }
                                    provider.unbindAll()
                                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group.build())
                                }.onFailure { onError("Không mở được camera.") }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    }
                },
            )

            // Dim everything outside the circle so it is obvious what will be analysed.
            Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                val radius = size.width * CIRCLE / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                drawRect(Color.Black.copy(alpha = 0.55f))
                drawCircle(Color.Transparent, radius, center, blendMode = BlendMode.Clear)
                drawCircle(Guide, radius, center, style = Stroke(width = 4.dp.toPx()))
            }

            Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Đóng", tint = Color.White) }
                }
                Text("Đưa món ăn vào trong vòng tròn xanh", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text("Chỉ phần trong vòng tròn được phân tích", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                Box(Modifier.weight(1f))
                Text(
                    "Ảnh trong vòng tròn và tóm tắt hồ sơ sức khỏe của bạn sẽ được gửi tới AI để phân tích.",
                    color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = onGallery, enabled = !busy) { Icon(Icons.Default.PhotoLibrary, "Chọn từ thư viện", tint = Color.White, modifier = Modifier.size(30.dp)) }
                    Box(
                        Modifier.size(76.dp).clip(CircleShape).border(4.dp, Guide, CircleShape).padding(7.dp).clip(CircleShape).background(Color.White)
                            .clickable(enabled = !busy) {
                                busy = true
                                capture.takePicture(worker, object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val result = runCatching { image.use { circleCrop(it) } }
                                        ContextCompat.getMainExecutor(context).execute {
                                            busy = false
                                            result.onSuccess(onCaptured).onFailure { onError("Không xử lý được ảnh vừa chụp.") }
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        ContextCompat.getMainExecutor(context).execute { busy = false; onError("Chụp ảnh thất bại. Hãy thử lại.") }
                                    }
                                })
                            },
                        contentAlignment = Alignment.Center,
                    ) { if (busy) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp) }
                    // Keeps the shutter centred.
                    Box(Modifier.size(48.dp))
                }
            }
        }
    }
}

/** The upright square around the guide circle, cut from the captured frame. */
private fun circleCrop(image: ImageProxy): Bitmap {
    val rotation = image.imageInfo.rotationDegrees
    // cropRect is the part of the sensor frame that was visible in the preview (shared view port).
    val visible = image.cropRect
    val frame = image.toBitmap()
    // The circle is sized from the preview's width, which lies along the frame's height when the sensor is rotated.
    val visibleWidth = if (rotation % 180 == 0) visible.width() else visible.height()
    val side = min((visibleWidth * CIRCLE).roundToInt(), min(frame.width, frame.height))
    val left = (visible.centerX() - side / 2).coerceIn(0, frame.width - side)
    val top = (visible.centerY() - side / 2).coerceIn(0, frame.height - side)
    return Bitmap.createBitmap(frame, left, top, side, side, Matrix().apply { postRotate(rotation.toFloat()) }, true)
}
