package com.ronin71.myopencv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.ronin71.myopencv.services.BackgroundRemoveService
import com.ronin71.myopencv.services.EdgeDetectionService
import com.ronin71.myopencv.services.ObjectDetectionService
import com.ronin71.myopencv.ui.theme.MyOpenCVTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader


val BgDeep        = Color(0xFF0A0A0F)
val BgCard        = Color(0xFF13131A)
val BgCardBorder  = Color(0xFF1E1E2E)
val AccentCyan    = Color(0xFF00FFCC)
val AccentPurple  = Color(0xFF9B5DE5)
val AccentOrange  = Color(0xFFFF6B35)
val TextPrimary   = Color(0xFFEEEEF5)
val TextSecondary = Color(0xFF7A7A9A)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyOpenCVTheme {
                CVLabApp()
            }
        }
    }
}


@Composable
fun CVLabApp() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Edge Detection", "Object Detection")
    LaunchedEffect(Unit) {
        if (OpenCVLoader.initLocal()) {
            Log.d("OutlineProcessor", "OpenCV initialized successfully")
        } else {
            Log.e("OutlineProcessor", "OpenCV initialization failed")
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BgDeep,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ──────────────────────────────────────────────────────
            CVHeader()

            // ── Tab Row ─────────────────────────────────────────────────────
            CVTabRow(
                tabs = tabs,
                selectedIndex = selectedTab,
                onTabSelected = { selectedTab = it },
            )

            // ── Tab Content ─────────────────────────────────────────────────
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                },
                modifier = Modifier.fillMaxSize(),
            ) { tab ->
                when (tab) {
                    0 -> EdgeDetectionTab()
                    1 -> BackgroundRemoveTab()
                }
            }
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────
@Composable
fun CVHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgDeep)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(AccentCyan, AccentPurple)
                        )
                    )
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "CV Lab",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                )
                Text(
                    "Computer Vision Toolkit",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                )
            }
        }
    }

    // Divider with gradient
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, AccentCyan.copy(alpha = 0.4f), Color.Transparent)
                )
            )
    )
}

// ─── Custom Tab Row ───────────────────────────────────────────────────────────
@Composable
fun CVTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
) {
    val accentColors = listOf(AccentCyan, AccentPurple)

    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = BgDeep,
        contentColor = TextPrimary,
        indicator = { tabPositions ->
            Box(
                Modifier
                    .tabIndicatorOffset(tabPositions[selectedIndex])
                    .height(2.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(accentColors[selectedIndex])
            )
        },
        divider = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BgCardBorder)
            )
        },
    ) {
        tabs.forEachIndexed { index, title ->
            val selected = selectedIndex == index
            Tab(
                selected = selected,
                onClick = { onTabSelected(index) },
                modifier = Modifier.height(48.dp),
            ) {
                val color by animateColorAsState(
                    if (selected) accentColors[index] else TextSecondary,
                    tween(200),
                )
                Text(
                    title,
                    color = color,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    letterSpacing = 0.3.sp,
                )
            }
        }
    }
}

// ─── Edge Detection Tab ───────────────────────────────────────────────────────
@Composable
fun EdgeDetectionTab() {
    var sourceBitmap  by remember { mutableStateOf<Bitmap?>(null) }
    var resultBitmap  by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing  by remember { mutableStateOf(false) }
    var errorMsg      by remember { mutableStateOf<String?>(null) }
    val scope         = rememberCoroutineScope()
    val context       = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            isProcessing = true
            errorMsg     = null
            resultBitmap = null
            try {
                val bmp = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } ?: throw Exception("Cannot decode image")

                sourceBitmap = bmp

                // Process on IO thread, keep original size & quality
                val result = withContext(Dispatchers.Default) {
                    EdgeDetectionService.generateContourEdgeImage(bmp, thickness = 1)
                }
                resultBitmap = result
            } catch (e: Exception) {
                errorMsg = e.message
            } finally {
                isProcessing = false
            }
        }
    }

    ProcessingTabLayout(
        tabTitle       = "Edge Detection",
        tabDescription = "Extracts object contours as a binary mask.\nWhite = edge, Black = background.",
        accentColor    = AccentCyan,
        sourceBitmap   = sourceBitmap,
        resultBitmap   = resultBitmap,
        resultLabel    = "Contour Edge Mask",
        isProcessing   = isProcessing,
        errorMsg       = errorMsg,
        onPickImage    = { picker.launch("image/*") },
    )
}

// ─── Object Detection Tab ─────────────────────────────────────────────────────
@Composable
fun BackgroundRemoveTab() {
    var sourceBitmap  by remember { mutableStateOf<Bitmap?>(null) }
    var resultBitmap  by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing  by remember { mutableStateOf(false) }
    var errorMsg      by remember { mutableStateOf<String?>(null) }
    val scope         = rememberCoroutineScope()
    val context       = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            isProcessing = true
            errorMsg     = null
            resultBitmap = null
            try {
                val bmp = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } ?: throw Exception("Cannot decode image")

                sourceBitmap = bmp

                val result = withContext(Dispatchers.Default) {
                    BackgroundRemoveService().handle(
                        bmp,
                        {it -> resultBitmap = it},
                        {e ->
                            errorMsg = e.message
                        }
                    )
                }

            } catch (e: Exception) {
                errorMsg = e.message
            } finally {
                isProcessing = false
            }
        }
    }

    ProcessingTabLayout(
        tabTitle       = "Background Remove",
        tabDescription = "Remove background and generates a binary mask.\nWhite = object, Black = background.",
        accentColor    = AccentPurple,
        sourceBitmap   = sourceBitmap,
        resultBitmap   = resultBitmap,
        resultLabel    = "Background Remove Mask",
        isProcessing   = isProcessing,
        errorMsg       = errorMsg,
        onPickImage    = { picker.launch("image/*") },
    )
}

// ─── Shared Processing Layout ─────────────────────────────────────────────────
@Composable
fun ProcessingTabLayout(
    tabTitle: String,
    tabDescription: String,
    accentColor: Color,
    sourceBitmap: Bitmap?,
    resultBitmap: Bitmap?,
    resultLabel: String,
    isProcessing: Boolean,
    errorMsg: String?,
    onPickImage: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {

        // ── Description card ──────────────────────────────────────────────
        CVCard(accentColor = accentColor) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    tabTitle,
                    color = accentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tabDescription,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }

        // ── Pick image button ─────────────────────────────────────────────
        PickImageButton(
            accentColor = accentColor,
            isProcessing = isProcessing,
            onClick = onPickImage,
        )

        // ── Error ─────────────────────────────────────────────────────────
        if (errorMsg != null) {
            CVCard(accentColor = AccentOrange) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⚠", fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        errorMsg,
                        color = AccentOrange,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        // ── Source image ──────────────────────────────────────────────────
        if (sourceBitmap != null) {
            ImageResultCard(
                bitmap = sourceBitmap,
                label = "Source Image",
                labelColor = TextSecondary,
                accentColor = accentColor,
            )
        }

        // ── Processing indicator ──────────────────────────────────────────
        if (isProcessing) {
            ProcessingCard(accentColor = accentColor)
        }

        // ── Result image ──────────────────────────────────────────────────
        if (resultBitmap != null && !isProcessing) {
            ImageResultCard(
                bitmap = resultBitmap,
                label = resultLabel,
                labelColor = accentColor,
                accentColor = accentColor,
                showDimensions = true,
            )
        }

        // Bottom nav spacing
        Spacer(Modifier.height(16.dp))
    }
}

// ─── Pick Image Button ────────────────────────────────────────────────────────
@Composable
fun PickImageButton(
    accentColor: Color,
    isProcessing: Boolean,
    onClick: () -> Unit,
) {
    val borderAlpha by animateFloatAsState(
        if (isProcessing) 0.3f else 1f,
        tween(300),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.08f))
            .drawBehind {
                drawRoundRect(
                    color = accentColor.copy(alpha = borderAlpha),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 1.5f),
                )
            }
            .clickable(enabled = !isProcessing, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("＋", color = accentColor, fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                if (isProcessing) "Processing…" else "Select Image",
                color = if (isProcessing) TextSecondary else accentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ─── Processing Card ──────────────────────────────────────────────────────────
@Composable
fun ProcessingCard(accentColor: Color) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(700),
            RepeatMode.Reverse,
        ),
    )

    CVCard(accentColor = accentColor) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                color = accentColor,
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(14.dp))
            Text(
                "Processing image…",
                color = accentColor.copy(alpha = alpha),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ─── Image Result Card ────────────────────────────────────────────────────────
@Composable
fun ImageResultCard(
    bitmap: Bitmap,
    label: String,
    labelColor: Color,
    accentColor: Color,
    showDimensions: Boolean = false,
) {
    CVCard(accentColor = accentColor) {
        Column(modifier = Modifier.padding(14.dp)) {

            // Label row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    color = labelColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                )
                if (showDimensions) {
                    Text(
                        "${bitmap.width} × ${bitmap.height}",
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Image — render at full quality, no scaling artifacts
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF050508)),
            )
        }
    }
}

// ─── Generic Card ─────────────────────────────────────────────────────────────
@Composable
fun CVCard(
    accentColor: Color,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .drawBehind {
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.18f),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(width = 1f),
                )
            }
    ) {
        content()
    }
}