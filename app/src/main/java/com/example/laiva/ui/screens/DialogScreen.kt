package com.example.laiva.ui.screens

import android.util.Log
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.laiva.R
import com.example.laiva.data.CryptoManager
import androidx.compose.foundation.background
import com.example.laiva.data.MailConfig
import com.example.laiva.data.MessengerRepository
import com.example.laiva.sendEmail
import com.example.laiva.MainActivity
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import coil.compose.rememberAsyncImagePainter
import com.example.laiva.ui.components.AudioMessagePlayer
import androidx.compose.ui.draw.clip
import android.graphics.BitmapFactory
import com.example.laiva.data.ChatMemberDao
import kotlinx.coroutines.withContext
import com.example.laiva.audio.SoundManager



import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.combinedClickable


import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.key

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Environment
import android.widget.Toast
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale

import androidx.compose.foundation.gestures.detectTransformGestures

fun formatTimestamp(timestamp: Long): String {
    return when {
        DateUtils.isToday(timestamp) -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
        DateUtils.isToday(timestamp + DateUtils.DAY_IN_MILLIS) -> {
            "Вчера, " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
        else -> {
            SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }
}


fun isEmail(value: String): Boolean {
    return value.contains("@")
}






@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogScreen(
    contactEmail: String,
    userEmail: String,
    userPassword: String,
    repository: MessengerRepository,
    onBack: () -> Unit
) {
    var fullscreenImagePath by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var messageText by remember { mutableStateOf("") }

    var selectionResetKey by remember { mutableStateOf(0) }
    val resetSelection = { selectionResetKey++ }


    val dont_overload_server = stringResource(R.string.dont_overload_server)

    LaunchedEffect(contactEmail) {
        repository.markAsRead(contactEmail)

        try {
            Log.d("LAIVA_DEBUG", "Запуск разовой синхронизации для $contactEmail")
            withContext(Dispatchers.IO) {
                repository.syncMessages(
                    context = context,
                    imapHost = MailConfig.getImapHost(userEmail),
                    imapPort = 993,
                    email = userEmail,
                    password = userPassword,
                    contactEmail = contactEmail
                )
            }
        } catch (e: Exception) {
            Log.e("LAIVA_DEBUG", "Ошибка синхронизации: ${e.message}")
        }
    }

    val contact by produceState<com.example.laiva.data.ContactEntity?>(initialValue = null, contactEmail) {
        value = repository.getContact(contactEmail)
    }
    val displayName = contact?.name ?: contactEmail

    DisposableEffect(contactEmail) {
        MainActivity.activeChatEmail = contactEmail
        (context as? MainActivity)?.cancelNotificationForContact(contactEmail)
        onDispose { MainActivity.activeChatEmail = null }
    }

    val messages = repository.getMessagesPaged(contactEmail).collectAsLazyPagingItems()
    val offsetX = remember { Animatable(0f) }

    var selectedMessageIds by remember { mutableStateOf(setOf<Long>()) }
    val isInSelectionMode by remember { derivedStateOf { selectedMessageIds.isNotEmpty() } }
    val clipboardManager = LocalClipboardManager.current

    // Функция копирования пачки сообщений
    val copySelectedMessages = {
        val textToCopy = messages.itemSnapshotList
            .filter { it?.id in selectedMessageIds }
            .sortedBy { it?.timestamp } // От старых к новым
            .joinToString("\n\n") { it?.text ?: "" }

        if (textToCopy.isNotEmpty()) {
            clipboardManager.setText(AnnotatedString(textToCopy))
        }
        selectedMessageIds = emptySet()
    }



// Файлпикер -----------------------
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val resolver = context.contentResolver


            val projection = arrayOf(
                android.provider.OpenableColumns.DISPLAY_NAME,
                android.provider.OpenableColumns.SIZE
            )

            var fileName = "file_${System.currentTimeMillis()}"
            var fileSize: Long = 0

            resolver.query(selectedUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.SIZE)

                    fileName = cursor.getString(nameIndex)
                    fileSize = cursor.getLong(sizeIndex)
                }
            }

            // Ограничение размера файла в 25 мегабайт
            val maxSizeInBytes = 25 * 1024 * 1024
            if (fileSize > maxSizeInBytes) {
                Toast.makeText(context, dont_overload_server, Toast.LENGTH_LONG).show()
                return@let
            }

            val file = File(context.cacheDir, fileName)
            try {
                resolver.openInputStream(selectedUri)?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.e("FILE_PICKER", "Ошибка копирования: ${e.message}")
                return@let
            }

            // определение типа
            val type = when {
                fileName.endsWith(".jpg", true) ||
                        fileName.endsWith(".jpeg", true) ||
                        fileName.endsWith(".png", true) -> "image"

                fileName.endsWith(".mp3", true) ||
                        fileName.endsWith(".wav", true) ||
                        fileName.endsWith(".ogg", true) -> "audio"

                else -> "file"
            }

            // шифрование и отправка
            scope.launch(Dispatchers.IO) {
                try {
                    val localId = repository.addOutgoingMessage(
                        contactEmail = contactEmail,
                        text = fileName,
                        type = type,
                        localPath = file.absolutePath
                    )

                    val isGroup = !isEmail(contactEmail)
                    val groupId = if (isGroup) contactEmail else null
                    val recipients = if (isGroup) {
                        repository.getChatMemberEmails(groupId!!).filter { it != userEmail }
                    } else {
                        listOf(contactEmail)
                    }

                    val encryptedFile = File(context.cacheDir, "${file.name}.enc")
                    val aesKeyBytes = CryptoManager.encryptFileWithAES(file, encryptedFile)

                    val recipientKeys = mutableMapOf<String, String>()
                    recipients.forEach { email ->
                        val pubKey = repository.getEncryptionKey(email) ?: return@forEach
                        val encryptedKey = CryptoManager.encryptAESKey(aesKeyBytes, pubKey)
                        recipientKeys[email] = encryptedKey
                    }

                    val success = sendEmail(
                        repository = repository,
                        smtpHost = MailConfig.getSmtpHost(userEmail),
                        smtpPort = 587,
                        fromEmail = userEmail,
                        password = userPassword,
                        type = type,
                        toEmails = recipients,
                        subject = "file",
                        body = "",
                        recipientKeys = recipientKeys,
                        filePath = encryptedFile.absolutePath,
                        group_id = groupId
                    )

                    if (encryptedFile.exists()) encryptedFile.delete()
                    repository.updateStatus(localId, if (success) "sent" else "error")

                    if (success) {
                        withContext(Dispatchers.Main) {
                            SoundManager.playSentSound(context)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FILE_PICKER", "Ошибка в процессе отправки: ${e.message}")
                }
            }
        }
    }
    //--------------------------------------------

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    if (isInSelectionMode) {
                        Text("${selectedMessageIds.size}")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(40.dp)) {
                                if (contact?.avatarPath != null) {
                                    AsyncImage(
                                        model = File(contact!!.avatarPath!!),
                                        contentDescription = "Аватар",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    // Заглушка, если аватара нет
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.padding(4.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(text = displayName, style = MaterialTheme.typography.titleMedium)
                                if (contact?.name != null) {
                                    Text(
                                        text = contactEmail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isInSelectionMode) selectedMessageIds = emptySet() else onBack()
                    }) {
                        Icon(
                            imageVector = if (isInSelectionMode) Icons.Default.Close else Icons.Default.ArrowBack,
                            contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (isInSelectionMode) {
                        IconButton(onClick = copySelectedMessages) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Копировать выбранное")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!isInSelectionMode) {
                Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        IconButton(onClick = { filePicker.launch("*/*") }) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach file")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        TextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.hint_message), style = MaterialTheme.typography.bodyLarge) },
                            shape = RoundedCornerShape(24.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            maxLines = 5
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                val text = messageText
                                if (text.isBlank()) return@IconButton
                                messageText = ""

                                scope.launch(Dispatchers.IO) {
                                    val localId = repository.addOutgoingMessage(contactEmail, text)
                                    val isGroup = !isEmail(contactEmail)
                                    val groupId = if (isGroup) contactEmail else null
                                    val recipients = if (isGroup) {
                                        repository.getChatMemberEmails(groupId!!).filter { it != userEmail }
                                    } else {
                                        listOf(contactEmail)
                                    }

                                    val (cipherTextBase64, aesKeyBytes) = CryptoManager.encryptTextWithAES(text)
                                    val recipientKeys = mutableMapOf<String, String>()

                                    recipients.forEach { email ->
                                        val pubKey = repository.getEncryptionKey(email) ?: return@forEach
                                        val encryptedKey = CryptoManager.encryptAESKey(aesKeyBytes, pubKey)
                                        recipientKeys[email] = encryptedKey
                                    }

                                    val success = sendEmail(
                                        repository = repository,
                                        smtpHost = MailConfig.getSmtpHost(userEmail),
                                        type = "text",
                                        smtpPort = 587,
                                        fromEmail = userEmail,
                                        password = userPassword,
                                        toEmails = recipients,
                                        myPublicKey = repository.getMyPublicKey(),
                                        subject = "msg",
                                        body = cipherTextBase64,
                                        recipientKeys = recipientKeys,
                                        group_id = groupId
                                    )

                                    repository.updateStatus(localId, if (success) "sent" else "error")
                                    if (success) {
                                        withContext(Dispatchers.Main) { SoundManager.playSentSound(context) }
                                    }
                                }
                            },
                            modifier = Modifier.size(48.dp).padding(bottom = 4.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { selectedMessageIds = emptySet() })
                },
            reverseLayout = true,
            contentPadding = PaddingValues(16.dp)
        ) {
            items(messages.itemCount) { index ->
                val msg = messages[index] ?: return@items
                val isSelected = selectedMessageIds.contains(msg.id)
                var showMenu by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .padding(vertical = 4.dp),
                    contentAlignment = if (msg.fromMe) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Box {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (msg.fromMe) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(
                                topStart = 20.dp,
                                topEnd = 20.dp,
                                bottomStart = if (msg.fromMe) 20.dp else 4.dp,
                                bottomEnd = if (msg.fromMe) 4.dp else 20.dp
                            ),
                            modifier = Modifier
                                .widthIn(max = 350.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (isInSelectionMode) {
                                            selectedMessageIds = if (isSelected) selectedMessageIds - msg.id else selectedMessageIds + msg.id
                                        } else {
                                            if (msg.type == "text" || msg.type == null) showMenu = true
                                        }
                                    },
                                    onLongClick = {
                                        if (!isInSelectionMode) {
                                            selectedMessageIds = setOf(msg.id)
                                        }
                                    }
                                )
                        ) {
                            Column(
                                modifier = if (msg.type == "image") Modifier else Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {

                                val isGroup = !isEmail(contactEmail)
                                if (isGroup && !msg.fromMe) {
                                    val senderNameState = produceState(initialValue = msg.senderEmail, msg.senderEmail) {
                                        value = repository.getContact(msg.senderEmail)?.name ?: msg.senderEmail
                                    }
                                    Text(
                                        text = senderNameState.value,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(bottom = 2.dp).padding(horizontal = 5.dp)
                                    )
                                }


                                when (msg.type) {
                                    "image" -> {
                                        msg.localPath?.let { path ->
                                            val ratio by remember(path) {
                                                mutableStateOf(try {
                                                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                                    BitmapFactory.decodeFile(path, options)
                                                    options.outWidth.toFloat() / options.outHeight.toFloat()
                                                } catch (e: Exception) { 1f })
                                            }
                                            Image(
                                                painter = rememberAsyncImagePainter(File(path)),
                                                contentDescription = null,
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                modifier = Modifier
                                                    .widthIn(max = 280.dp)
                                                    .aspectRatio(ratio)
                                                    //.clip(RoundedCornerShape(8.dp))
                                                    .combinedClickable(
                                                        onClick = {
                                                            if (isInSelectionMode) {
                                                                selectedMessageIds = if (isSelected) selectedMessageIds - msg.id else selectedMessageIds + msg.id
                                                            } else {
                                                                fullscreenImagePath = path
                                                            }
                                                        },
                                                        onLongClick = {
                                                            if (!isInSelectionMode) {
                                                                selectedMessageIds = setOf(msg.id)
                                                            }
                                                        }
                                                    )
                                            )
                                        }
                                    }
                                    "audio" -> msg.localPath?.let { path ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                AudioMessagePlayer(path)
                                            }
                                            IconButton(
                                                onClick = { saveFileToDownloads(context, path) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = "Download audio",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                    "file" -> Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .combinedClickable(
                                                onClick = {
                                                    if (isInSelectionMode) {
                                                        selectedMessageIds = if (isSelected) selectedMessageIds - msg.id else selectedMessageIds + msg.id
                                                    } else {
                                                        msg.localPath?.let { saveFileToDownloads(context, it) }
                                                    }
                                                },
                                                onLongClick = {
                                                    if (!isInSelectionMode) selectedMessageIds = setOf(msg.id)
                                                }
                                            )
                                            .padding(4.dp)
                                    ) {
                                        Icon(Icons.Default.AttachFile, null)
                                        Text(msg.text ?: "file", modifier = Modifier.padding(start = 8.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    else -> {
                                        if (isSelected && isInSelectionMode) {
                                            SelectionContainer {
                                                Text(text = msg.text ?: "", style = MaterialTheme.typography.bodyLarge)
                                            }
                                        } else {
                                            Text(text = msg.text ?: "", style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                }

                                // Время и статус
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp).align(if (msg.fromMe) Alignment.End else Alignment.Start)
                                ) {
                                    Text(
                                        text = formatTimestamp(msg.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    if (msg.fromMe) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        val (icon, color) = when (msg.status) {
                                            "pending" -> Icons.Default.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
                                            "error" -> Icons.Default.Error to MaterialTheme.colorScheme.error
                                            else -> Icons.Default.Done to MaterialTheme.colorScheme.primary
                                        }
                                        Icon(icon, null, modifier = Modifier.size(14.dp), tint = color.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }

                        if (!isInSelectionMode) {
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Копировать всё") },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(msg.text ?: ""))
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Выбрать") },
                                    leadingIcon = { Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        selectedMessageIds = setOf(msg.id)
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        fullscreenImagePath?.let { path ->
            FullScreenImageDialog(
                filePath = path,
                onDismiss = { fullscreenImagePath = null },
                onDownload = { saveFileToDownloads(context, path) }
            )
        }

    }
}




@Composable
fun FullScreenImageDialog(
    filePath: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.ui.graphics.Color.Black
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 10f)


                            if (newScale > 1f) {
                                val newOffset = offset + pan
                                offset = newOffset
                            } else {
                                offset = androidx.compose.ui.geometry.Offset.Zero
                            }
                            scale = newScale
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                scale = 1f
                                offset = androidx.compose.ui.geometry.Offset.Zero
                            },
                            onTap = {

                            }
                        )
                    }
            ) {
                Image(
                    painter = rememberAsyncImagePainter(File(filePath)),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = androidx.compose.ui.graphics.Color.White)
                    }
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Default.Download, "Download", tint = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }
    }
}


fun saveFileToDownloads(context: android.content.Context, filePath: String) {
    val file = File(filePath)

    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // для андроид 10+
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(file))
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                resolver.openOutputStream(it).use { output ->
                    file.inputStream().use { input ->
                        input.copyTo(output!!)
                    }
                }
                Toast.makeText(context, "Файл сохранен в Загрузки", Toast.LENGTH_SHORT).show()
            }
        } else {
            // для андроид 8-9
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destinationFile = File(downloadsDir, file.name)

            file.inputStream().use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(destinationFile.absolutePath),
                null,
                null
            )

            Toast.makeText(context, "Файл сохранён: ${destinationFile.absolutePath}", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e("SAVE_FILE", "Ошибка: ${e.message}")
        Toast.makeText(context, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
    }
}

fun getMimeType(file: File): String {
    return when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
    }
}