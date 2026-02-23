package com.unamentis.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.unamentis.R
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import java.util.concurrent.Executors

/**
 * QR Code scanner screen for server discovery.
 *
 * Uses CameraX for camera preview and ML Kit for barcode detection.
 * Scans for QR codes containing server configuration (host and port).
 *
 * @param onScanned Callback with parsed host and port from QR code
 * @param onManualEntry Callback to switch to manual server entry
 * @param onNavigateBack Callback to navigate back
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCodeScannerScreen(
    onScanned: (host: String, port: Int) -> Unit,
    onManualEntry: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var scannedSuccessfully by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            hasCameraPermission = granted
        }

    val backCd = stringResource(R.string.cd_go_back)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics { contentDescription = backCd },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.qr_scanner_title),
                        style = IOSTypography.headline,
                    )
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            if (hasCameraPermission && !scannedSuccessfully) {
                // Camera preview with barcode scanning
                CameraPreviewWithScanner(
                    onBarcodeDetected = { rawValue ->
                        if (!scannedSuccessfully) {
                            val parsed = parseServerQRCode(rawValue)
                            if (parsed != null) {
                                scannedSuccessfully = true
                                onScanned(parsed.first, parsed.second)
                            }
                        }
                    },
                )

                // Scanning overlay
                ScanningOverlay(
                    modifier = Modifier.align(Alignment.Center),
                )

                // Instructions and manual entry at bottom
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(Dimensions.ScreenHorizontalPadding)
                            .padding(bottom = Dimensions.SpacingXLarge),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
                ) {
                    Text(
                        text = stringResource(R.string.qr_scanner_instructions),
                        style = IOSTypography.body,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onManualEntry) {
                        Text(
                            text = stringResource(R.string.qr_scanner_manual_entry),
                            color = Color.White,
                        )
                    }
                }
            } else if (!hasCameraPermission) {
                // Permission request UI
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(Dimensions.ScreenHorizontalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.qr_scanner_permission_required),
                        style = IOSTypography.body,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                    ) {
                        Text(stringResource(R.string.qr_scanner_grant_permission))
                    }
                    TextButton(onClick = onManualEntry) {
                        Text(stringResource(R.string.qr_scanner_manual_entry))
                    }
                }
            }
        }
    }
}

/**
 * Camera preview composable with ML Kit barcode scanning.
 */
@Composable
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun CameraPreviewWithScanner(onBarcodeDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val scannerOptions =
        remember {
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        }
    val barcodeScanner = remember { BarcodeScanning.getClient(scannerOptions) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    val viewfinderCd = stringResource(R.string.cd_qr_scanner_viewfinder)

    AndroidView(
        modifier =
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = viewfinderCd },
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview =
                    Preview.Builder()
                        .build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }

                val imageAnalysis =
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val inputImage =
                                        InputImage.fromMediaImage(
                                            mediaImage,
                                            imageProxy.imageInfo.rotationDegrees,
                                        )
                                    barcodeScanner.process(inputImage)
                                        .addOnSuccessListener { barcodes ->
                                            for (barcode in barcodes) {
                                                barcode.rawValue?.let { raw ->
                                                    onBarcodeDetected(raw)
                                                }
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis,
                    )
                } catch (_: Exception) {
                    // Camera bind failed
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
    )
}

/**
 * Animated scanning frame overlay.
 */
@Composable
private fun ScanningOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning_line")
    val scanLinePosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 2000),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "scan_line_y",
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier =
            modifier
                .size(250.dp),
    ) {
        val cornerLength = 40f
        val strokeWidth = 4f
        val w = size.width
        val h = size.height

        // Top-left corner
        drawLine(Color.White, Offset(0f, 0f), Offset(cornerLength, 0f), strokeWidth, StrokeCap.Round)
        drawLine(Color.White, Offset(0f, 0f), Offset(0f, cornerLength), strokeWidth, StrokeCap.Round)

        // Top-right corner
        drawLine(Color.White, Offset(w - cornerLength, 0f), Offset(w, 0f), strokeWidth, StrokeCap.Round)
        drawLine(Color.White, Offset(w, 0f), Offset(w, cornerLength), strokeWidth, StrokeCap.Round)

        // Bottom-left corner
        drawLine(Color.White, Offset(0f, h - cornerLength), Offset(0f, h), strokeWidth, StrokeCap.Round)
        drawLine(Color.White, Offset(0f, h), Offset(cornerLength, h), strokeWidth, StrokeCap.Round)

        // Bottom-right corner
        drawLine(Color.White, Offset(w, h - cornerLength), Offset(w, h), strokeWidth, StrokeCap.Round)
        drawLine(Color.White, Offset(w - cornerLength, h), Offset(w, h), strokeWidth, StrokeCap.Round)

        // Animated scanning line
        val lineY = scanLinePosition * size.height
        drawLine(
            color = primaryColor,
            start = Offset(strokeWidth, lineY),
            end = Offset(size.width - strokeWidth, lineY),
            strokeWidth = 2f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Parse a QR code string into server host and port.
 *
 * Expected format: JSON or key-value containing "host" and "port".
 * Returns null if the QR code is invalid.
 */
private fun parseServerQRCode(rawValue: String): Pair<String, Int>? {
    // Check if the raw value contains host and port
    if (!rawValue.contains("host") || !rawValue.contains("port")) {
        return null
    }

    return try {
        // Try JSON parsing
        val json = org.json.JSONObject(rawValue)
        val host = json.optString("host", "")
        val port = json.optInt("port", 0)
        if (host.isNotBlank() && port > 0) {
            host to port
        } else {
            null
        }
    } catch (_: Exception) {
        // Try simple key=value parsing
        val hostMatch = Regex("""host[=:]\s*"?([^",}\s]+)"?""").find(rawValue)
        val portMatch = Regex("""port[=:]\s*"?(\d+)"?""").find(rawValue)
        val host = hostMatch?.groupValues?.get(1)
        val port = portMatch?.groupValues?.get(1)?.toIntOrNull()
        if (!host.isNullOrBlank() && port != null && port > 0) {
            host to port
        } else {
            null
        }
    }
}
