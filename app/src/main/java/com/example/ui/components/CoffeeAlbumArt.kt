package com.example.ui.components

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberSongArtwork(songPath: String?): ImageBitmap? {
    if (songPath == null || songPath.startsWith("generative://") || songPath.isEmpty()) return null
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(songPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(songPath) {
        withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                if (songPath.startsWith("content://")) {
                    retriever.setDataSource(context, Uri.parse(songPath))
                } else {
                    retriever.setDataSource(songPath)
                }
                val artBytes = retriever.embeddedPicture
                retriever.release()
                if (artBytes != null) {
                    val opts = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, opts)
                    var sampleSize = 1
                    while ((opts.outWidth / sampleSize) > 400 || (opts.outHeight / sampleSize) > 400) {
                        sampleSize *= 2
                    }
                    val decodeOpts = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    val bmp = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, decodeOpts)
                    bitmap = bmp?.asImageBitmap()
                }
            } catch (e: Exception) {
                try {
                    if (songPath.startsWith("content://")) {
                        val uri = Uri.parse(songPath)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val bmp = context.contentResolver.loadThumbnail(uri, Size(300, 300), null)
                            bitmap = bmp?.asImageBitmap()
                        }
                    }
                } catch (e2: Exception) {
                    // ignore
                }
            }
        }
    }
    return bitmap
}

@Composable
fun CoffeeAlbumArt(
    presetId: String,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    songPath: String? = null
) {
    val artwork = rememberSongArtwork(songPath)
    if (artwork != null) {
        CoffeeAlbumArtContent(presetId = presetId, modifier = modifier, angle = 0f, artwork = artwork)
    } else {
        if (isPlaying) {
            val infiniteTransition = rememberInfiniteTransition(label = "VinylRotation")
            val rotationAngle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(12000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "Angle"
            )
            CoffeeAlbumArtContent(presetId = presetId, modifier = modifier, angle = rotationAngle, artwork = null)
        } else {
            CoffeeAlbumArtContent(presetId = presetId, modifier = modifier, angle = 0f, artwork = null)
        }
    }
}

@Composable
fun CoffeeAlbumArtContent(
    presetId: String,
    modifier: Modifier = Modifier,
    angle: Float,
    artwork: ImageBitmap? = null
) {
    // Define colors
    val appColors = com.example.ui.theme.LocalAppColors.current
    val deepEspresso = appColors.deepEspresso
    val darkMocha = appColors.darkMocha
    val coffeeBrown = appColors.coffeeBrown
    val softLatte = appColors.softLatte
    val warmCream = appColors.warmCream
    val themeBrightness = appColors.themeBrightness

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.radialGradient(listOf(darkMocha, deepEspresso)))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val center = Offset(width / 2f, height / 2f)
            val radius = Math.min(width, height) / 2f

            if (artwork != null) {
                drawImage(
                    image = artwork,
                    dstOffset = IntOffset(0, 0),
                    dstSize = IntSize(width.toInt(), height.toInt())
                )
            } else {
                // Rotate entire canvas to reflect playback spin
                rotate(angle, center) {
                    // 1. Draw outer stylized vinyl grooves
                    drawCircle(
                        color = Color(0x28000000),
                        radius = radius * 0.95f,
                        style = Stroke(width = 8f)
                    )
                    drawCircle(
                        color = Color(0x1A000000),
                        radius = radius * 0.88f,
                        style = Stroke(width = 4f)
                    )
                    drawCircle(
                        color = Color(0x1EFFFFFF),
                        radius = radius * 0.8f,
                        style = Stroke(width = 2f)
                    )

                    // 2. Render preset-specific illustration
                    when (presetId) {
                        "mocha_breeze" -> {
                            // Heart shaped latte art swirl
                            val path = Path().apply {
                                moveTo(center.x, center.y - radius * 0.35f)
                                cubicTo(
                                    center.x + radius * 0.45f, center.y - radius * 0.7f,
                                    center.x + radius * 0.5f, center.y + radius * 0.1f,
                                    center.x, center.y + radius * 0.4f
                                )
                                cubicTo(
                                    center.x - radius * 0.5f, center.y + radius * 0.1f,
                                    center.x - radius * 0.45f, center.y - radius * 0.7f,
                                    center.x, center.y - radius * 0.35f
                                )
                            }
                            // Fill with gold gradient
                            drawPath(
                                path = path,
                                brush = Brush.linearGradient(listOf(softLatte, coffeeBrown))
                            )
                            // Accent swirls
                            drawCircle(
                                color = warmCream.copy(alpha = 0.5f),
                                radius = radius * 0.18f,
                                center = Offset(center.x - radius * 0.12f, center.y - radius * 0.08f),
                                style = Stroke(width = 4f)
                            )
                        }
                        
                        "espresso_accent" -> {
                            // Geometric Bauhaus Espresso theme
                            drawRect(
                                brush = Brush.verticalGradient(listOf(coffeeBrown, darkMocha)),
                                topLeft = Offset(center.x - radius * 0.4f, center.y - radius * 0.4f),
                                size = ComposeSize(radius * 0.8f, radius * 0.8f),
                                alpha = 0.85f
                            )
                            drawCircle(
                                color = warmCream,
                                radius = radius * 0.28f,
                                center = center
                            )
                            drawCircle(
                                color = coffeeBrown,
                                radius = radius * 0.18f,
                                center = center
                            )
                        }
                        
                        "coffee_rain" -> {
                            // Concentric water drop cascades
                            drawCircle(
                                color = softLatte.copy(alpha = 0.7f),
                                radius = radius * 0.45f,
                                style = Stroke(width = 6f)
                            )
                            drawCircle(
                                color = coffeeBrown.copy(alpha = 0.6f),
                                radius = radius * 0.32f,
                                style = Stroke(width = 4f)
                            )
                            drawCircle(
                                color = warmCream.copy(alpha = 0.8f),
                                radius = radius * 0.16f,
                                style = Stroke(width = 3f)
                            )
                            // Splash drops
                            drawCircle(color = warmCream, radius = 6f, center = Offset(center.x + radius*0.3f, center.y - radius*0.2f))
                            drawCircle(color = softLatte, radius = 10f, center = Offset(center.x - radius*0.2f, center.y + radius*0.3f))
                        }
                        
                        "caramel_latte" -> {
                            // Infinite Caramel spirals
                            val path = Path()
                            for (theta in 0..1080 step 10) {
                                val r = (radius * 0.45f) * (theta / 1080f)
                                val rad = Math.toRadians(theta.toDouble())
                                val x = center.x + r * Math.cos(rad).toFloat()
                                val y = center.y + r * Math.sin(rad).toFloat()
                                if (theta == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(
                                path = path,
                                color = softLatte,
                                style = Stroke(width = 12f)
                            )
                            drawCircle(color = warmCream, radius = radius * 0.12f, center = center)
                        }
                        
                        "velvet_morning" -> {
                            // Rising golden morning sun visual over mountains
                            drawCircle(
                                color = softLatte,
                                radius = radius * 0.35f,
                                center = Offset(center.x, center.y - radius * 0.1f)
                            )
                            val mountainPath = Path().apply {
                                moveTo(center.x - radius * 0.6f, center.y + radius * 0.4f)
                                lineTo(center.x - radius * 0.1f, center.y - radius * 0.1f)
                                lineTo(center.x + radius * 0.4f, center.y + radius * 0.4f)
                                close()
                            }
                            drawPath(
                                path = mountainPath,
                                brush = Brush.verticalGradient(listOf(coffeeBrown, deepEspresso))
                            )
                        }
                        
                        else -> {
                            // Standard physical music tracks (draw a retro shiny CD vinyl texture with a gold label!)
                            drawCircle(
                                color = coffeeBrown,
                                radius = radius * 0.45f
                            )
                            drawCircle(
                                color = deepEspresso,
                                radius = radius * 0.15f
                            )
                            drawCircle(
                                color = warmCream,
                                radius = radius * 0.05f
                            )
                        }
                    }
                }

                // 3. Central Vinyl Spindle
                drawCircle(
                    color = deepEspresso,
                    radius = radius * 0.04f,
                    center = center
                )
            }

            // Apply theme brightness overlay
            if (themeBrightness < 1.0f) {
                drawRect(
                    color = Color.Black,
                    alpha = 1f - themeBrightness,
                    size = size
                )
            }
        }
    }
}
