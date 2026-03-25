package com.example.laiva.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis

import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.laiva.data.MessengerRepository
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.concurrent.Executors
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Intent

import androidx.compose.ui.res.stringResource
import com.example.laiva.R
import android.content.Context

fun generateQrCode(text: String): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(
    repository: MessengerRepository,
    myEmail: String,
    onBack: () -> Unit
) {
    var myPublicKey by remember { mutableStateOf<String?>(null) }
    var myName by remember { mutableStateOf(myEmail.substringBefore("@")) }
    LaunchedEffect(Unit) {
        myPublicKey = repository.getMyPublicKey()
    }


    val myQrData = remember(myPublicKey) {
        myPublicKey?.let {
            "laiva://contact?" +
                    "name=${Uri.encode(myName)}&" +
                    "email=${Uri.encode(myEmail)}&" +
                    "pubkey=${Uri.encode(it)}"
        }
    }

    val myQrBitmap = remember(myQrData) {
        myQrData?.let { generateQrCode(it) }
    }

    var contactLink by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    var showHandshake by remember { mutableStateOf(false) }
    var publicKey by remember { mutableStateOf("") }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> if (isGranted) showHandshake = true }



    if (showHandshake) {
        ScanContactDialog(
            onResult = { scannedName, scannedEmail, scannedKey ->
                name = scannedName
                email = scannedEmail
                publicKey = scannedKey

                contactLink =
                    "laiva://contact?" +
                            "name=${Uri.encode(scannedName)}&" +
                            "email=${Uri.encode(scannedEmail)}&" +
                            "pubkey=${Uri.encode(scannedKey)}"
            },
            onDismiss = { showHandshake = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_new_contact), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    Button(
                        onClick = {
                            val permission = Manifest.permission.CAMERA
                            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                                showHandshake = true
                            } else {
                                cameraPermissionLauncher.launch(permission)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Sync, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.qr_scan_desc))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)

            if (email.isNotBlank()) {
                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.contact_label),
                    fontWeight = FontWeight.Bold
                )

                Text(text = stringResource(R.string.name_label, name))

                Text(text = stringResource(R.string.email_label, email))
            }

            Spacer(Modifier.height(40.dp))
            Button(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = publicKey.isNotBlank(),
                onClick = {
                    scope.launch {
                        repository.addContact(
                            email.trim(),
                            name.trim(),
                            publicKey.trim()
                        )
                        onBack()
                    }
                }
            ) { Text(stringResource(R.string.btn_save_contact)) }

            Spacer(Modifier.height(24.dp))

            Text(stringResource(R.string.my_qr_code), fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(12.dp))

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            //qr
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                myQrBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(300.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    //кнопка копирования
                    Surface(
                        modifier = Modifier
                            .size(56.dp)
                            .clickable {
                                myQrData?.let {
                                    clipboard.setPrimaryClip(ClipData.newPlainText("contact", it))
                                }
                            },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,

                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Копировать",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    //кнопка поделиться
                    Surface(
                        modifier = Modifier
                            .size(56.dp)
                            .clickable {
                                myQrData?.let {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, it)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Поделиться"))
                                }
                            },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondary,

                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Поделиться",
                                tint = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    }
                }
            }



            Spacer(Modifier.height(24.dp))


            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = contactLink,
                    onValueChange = { input ->
                        contactLink = input

                        parseContactLink(input) { parsedName, parsedEmail, parsedKey ->
                            name = parsedName
                            email = parsedEmail
                            publicKey = parsedKey
                        }
                    },
                    label = { Text(stringResource(R.string.enter_contact_string)) },
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        val clip = clipboard.primaryClip
                        val text = clip?.getItemAt(0)?.text?.toString() ?: ""

                        contactLink = text

                        parseContactLink(text) { parsedName, parsedEmail, parsedKey ->
                            name = parsedName
                            email = parsedEmail
                            publicKey = parsedKey
                        }
                    }
                ) {
                    Icon(Icons.Default.ContentPaste, null)
                }
            }


        }
    }
}
fun parseContactLink(
    input: String,
    onParsed: (String, String, String) -> Unit
) {
    if (!input.startsWith("laiva://contact")) return

    try {
        val uri = Uri.parse(input)

        val parsedName = uri.getQueryParameter("name") ?: ""
        val parsedEmail = uri.getQueryParameter("email") ?: ""
        val parsedKey = uri.getQueryParameter("pubkey") ?: ""

        if (parsedEmail.isNotBlank() && parsedKey.isNotBlank()) {
            onParsed(parsedName, parsedEmail, parsedKey)
        }
    } catch (_: Exception) {}
}
@Composable
fun ScanContactDialog(
    onResult: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isDone by remember { mutableStateOf(false) }
    val scanner = remember { BarcodeScanning.getClient() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {

            Box(modifier = Modifier.fillMaxSize()) {

                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = androidx.camera.core.Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image

                                if (mediaImage != null && !isDone) {
                                    val image = InputImage.fromMediaImage(
                                        mediaImage,
                                        imageProxy.imageInfo.rotationDegrees
                                    )

                                    scanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            for (barcode in barcodes) {
                                                val raw = barcode.rawValue ?: continue

                                                if (raw.startsWith("laiva://contact")) {
                                                    val uri = Uri.parse(raw)

                                                    val name = uri.getQueryParameter("name") ?: ""
                                                    val email = uri.getQueryParameter("email") ?: ""
                                                    val pubkey = uri.getQueryParameter("pubkey") ?: ""

                                                    if (email.isNotBlank() && pubkey.isNotBlank()) {
                                                        isDone = true
                                                        onResult(name, email, pubkey)
                                                        onDismiss()
                                                    }
                                                }
                                            }
                                        }
                                        .addOnCompleteListener { imageProxy.close() }

                                } else {
                                    imageProxy.close()
                                }
                            }

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )

                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Text(
                    stringResource(R.string.enter_camera),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                    color = Color.White
                )

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}