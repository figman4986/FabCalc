package com.figapps.fractioncalculatorv1.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.figapps.fractioncalculatorv1.domain.LengthUnit
import com.figapps.fractioncalculatorv1.wire.WireDatasheet
import com.figapps.fractioncalculatorv1.wire.WireSizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Bitmap cache for rendered pages. Sized to a fraction of the VM heap — a deliberate tradeoff:
 * bigger keeps more pages resident when flipping, smaller recycles sooner and re-renders more.
 */
private class PdfPageCache(maxBytes: Int) : LruCache<String, Bitmap>(maxBytes) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

/**
 * In-app PDF viewer for a datasheet: page bitmaps rendered off the main thread, a vertical pager,
 * pinch-to-zoom and a tag summary header. A corrupt or missing file shows an error state with the
 * option to remove the orphaned entry.
 */
@Composable
fun WireViewerScreen(
    datasheet: WireDatasheet,
    unit: LengthUnit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = CalcTheme.colors
    val repository = LocalAppContainer.current.wireRepository
    val file = remember(datasheet.fileName) { repository.fileFor(datasheet.fileName) }

    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            runCatching {
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(descriptor).also { pageCount = it.pageCount }
            }.onSuccess { renderer = it }
                .onFailure { error = it.message ?: "This file could not be opened as a PDF." }
        }
    }

    DisposableEffect(renderer) {
        onDispose { runCatching { renderer?.close() } }
    }

    val mutex = remember { Mutex() }
    val cache = remember {
        val maxBytes = (Runtime.getRuntime().maxMemory() / CACHE_HEAP_DIVISOR).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        PdfPageCache(maxBytes)
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val pagerState = rememberPagerState(pageCount = { pageCount })

    Column(modifier = modifier.fillMaxSize()) {
        ViewerHeader(datasheet = datasheet, unit = unit, onBack = onBack)

        val activeRenderer = renderer
        when {
            error != null -> ViewerError(message = error!!, onDelete = onDelete)
            activeRenderer != null && pageCount > 0 -> {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    val baseWidth = constraints.maxWidth
                    // Re-render at higher resolution once the user zooms past the threshold.
                    val targetWidth = (baseWidth * if (scale > ZOOM_RERENDER_THRESHOLD) 2f else 1f)
                        .roundToInt().coerceAtLeast(1)

                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.filter { it.pressed }
                                            if (pressed.isEmpty()) break
                                            if (pressed.size >= 2) {
                                                val zoom = event.calculateZoom()
                                                val pan = event.calculatePan()
                                                if (zoom != 1f || pan != Offset.Zero) {
                                                    scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                                                    offset = if (scale > 1f) offset + pan else Offset.Zero
                                                    event.changes.forEach { it.consume() }
                                                }
                                            }
                                        }
                                    }
                                }
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            PdfPageImage(
                                renderer = activeRenderer,
                                pageIndex = index,
                                targetWidth = targetWidth,
                                cache = cache,
                                mutex = mutex,
                            )
                        }
                    }
                }
                Text(
                    text = "Page ${pagerState.currentPage + 1} of $pageCount  \u00B7  pinch to zoom",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = colors.numberKeyText.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
            else -> Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Loading\u2026", color = colors.numberKeyText.copy(alpha = 0.6f), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ViewerHeader(datasheet: WireDatasheet, unit: LengthUnit, onBack: () -> Unit) {
    val colors = CalcTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to library",
                    tint = colors.numberKeyText,
                )
            }
            Text(
                text = datasheet.productName.ifBlank { datasheet.fileName },
                color = colors.numberKeyText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        val summary = buildList {
            if (datasheet.brand.isNotBlank()) add(datasheet.brand)
            if (datasheet.awsClassification.isNotBlank()) add(datasheet.awsClassification)
            WireSizes.display(datasheet.wireSizeMm, unit)?.let(::add)
            add(datasheet.process.marker)
            add(datasheet.metalType.label)
        }.joinToString("  \u00B7  ")
        Text(
            text = summary,
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
            color = colors.numberKeyText.copy(alpha = 0.7f),
            fontSize = 12.sp,
        )
        if (!datasheet.notes.isNullOrBlank()) {
            Text(
                text = datasheet.notes,
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
                color = colors.numberKeyText.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ViewerError(message: String, onDelete: () -> Unit) {
    val colors = CalcTheme.colors
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.padding(24.dp),
            shape = RoundedCornerShape(CalcCornerRadius),
            color = colors.cardBackground,
            border = BorderStroke(1.dp, colors.divider),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Can't open this datasheet", color = colors.numberKeyText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(message, color = colors.numberKeyText.copy(alpha = 0.7f), fontSize = 13.sp)
                Text(
                    "The file may be corrupt or missing. You can remove the entry from the library.",
                    color = colors.numberKeyText.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = colors.errorText)
                        Spacer(Modifier.width(6.dp))
                        Text("Remove", color = colors.errorText)
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageImage(
    renderer: PdfRenderer,
    pageIndex: Int,
    targetWidth: Int,
    cache: PdfPageCache,
    mutex: Mutex,
    modifier: Modifier = Modifier,
) {
    val colors = CalcTheme.colors
    val bitmap by produceState<Bitmap?>(initialValue = null, renderer, pageIndex, targetWidth) {
        val key = "$pageIndex@$targetWidth"
        val cached = cache.get(key)
        if (cached != null) {
            value = cached
            return@produceState
        }
        val rendered = renderPage(renderer, pageIndex, targetWidth, mutex)
        if (rendered != null) cache.put(key, rendered)
        value = rendered
    }

    val image = bitmap
    if (image == null) {
        Text("Rendering\u2026", color = colors.numberKeyText.copy(alpha = 0.5f), fontSize = 13.sp)
    } else {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "Datasheet page ${pageIndex + 1}",
            modifier = modifier.fillMaxSize().background(colors.cardBackground),
            contentScale = ContentScale.Fit,
        )
    }
}

/** Renders one page at [targetWidth] px wide, serialised so a single PdfRenderer is never re-entered. */
private suspend fun renderPage(
    renderer: PdfRenderer,
    pageIndex: Int,
    targetWidth: Int,
    mutex: Mutex,
): Bitmap? = mutex.withLock {
    withContext(Dispatchers.IO) {
        runCatching {
            renderer.openPage(pageIndex).use { page ->
                val width = targetWidth.coerceAtLeast(1)
                val height = (page.height * (targetWidth.toFloat() / page.width)).roundToInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(android.graphics.Color.WHITE)
                    page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }.getOrNull()
    }
}

/** Fraction of the VM heap the page-bitmap cache may use. */
private const val CACHE_HEAP_DIVISOR = 6

/** Zoom past which pages are re-rendered at 2× resolution. */
private const val ZOOM_RERENDER_THRESHOLD = 1.5f

private const val MAX_ZOOM = 5f
