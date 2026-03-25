package com.example.laiva.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.laiva.R
import com.example.laiva.data.AppDatabase
import com.example.laiva.data.ChatEntity
import com.example.laiva.data.ContactEntity
import com.example.laiva.data.MessengerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.example.laiva.DataStoreManager

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    email: String,
    repository: MessengerRepository,
    dataStoreManager: DataStoreManager,
    onAddContactClick: () -> Unit,
    onAddGroupClick: () -> Unit,
    onContactClick: (String) -> Unit,
    onGroupClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val isPanicActive by dataStoreManager.isPanicActiveFlow.collectAsState(initial = null)
    if (isPanicActive != false) {
        var showErrorDialog by remember { mutableStateOf(false) }
        val context = LocalContext.current

        // Таймер на 10 секунд
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(10000)
            showErrorDialog = true
        }

        //Экран загрузки
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Подключение к серверу...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        //Диалог ошибки
        if (showErrorDialog) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Ошибка сети") },
                text = { Text("Не удалось установить стабильное соединение с сервером IMAP. Проверьте настройки прокси или состояние сети.") },
                confirmButton = {
                    Button(onClick = {
                        (context as? android.app.Activity)?.finishAffinity()
                    }) {
                        Text("Выход")
                    }
                }
            )
        }
    } else {
    val contacts by repository.getAllContacts().collectAsState(initial = emptyList())
    val groups by repository.getAllGroups().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var keyDialogOpen by remember { mutableStateOf(false) }
    var currentChatId by remember { mutableStateOf("") }
    var currentIsGroup by remember { mutableStateOf(false) }
    var enteredKey by remember { mutableStateOf("") }

    val itemsToShow = remember(contacts, groups) {
        val list = mutableListOf<Any>()
        list.addAll(groups)
        list.addAll(contacts)
        list
    }


        fun handleChatClick(chatId: String, isGroup: Boolean) {
            if (isGroup) {
                onGroupClick(chatId)
            } else {
                onContactClick(chatId)
            }
        }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        //Своя аватарка
                        val avatarFile = File(context.filesDir, "avatars/me.jpg")
                        val bitmap = remember(avatarFile.lastModified()) {
                            if (avatarFile.exists()) BitmapFactory.decodeFile(avatarFile.absolutePath) else null
                        }

                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "My Avatar",
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = email.firstOrNull()?.uppercase() ?: "?",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(stringResource(R.string.title_chats), fontWeight = FontWeight.Bold)
                            Text(
                                text = email,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FloatingActionButton(onClick = onAddContactClick) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить контакт")
                }
                FloatingActionButton(onClick = onAddGroupClick, containerColor = MaterialTheme.colorScheme.secondary) {
                    Icon(Icons.Default.AccountBox, contentDescription = "Создать группу")
                }
            }
        }
    ) { innerPadding ->
        if (itemsToShow.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_contacts), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(innerPadding)) {
                item {
                    Text(
                        stringResource(R.string.all_messages),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(itemsToShow) { item ->
                    when (item) {
                        is ChatEntity -> GroupItem(
                            name = item.name,
                            onClick = { handleChatClick(item.id, true) },
                            onRename = { newName -> scope.launch { repository.renameChat(item.id, newName) } },
                            onDelete = { scope.launch { repository.deleteChat(item.id) } }
                        )
                        is ContactEntity -> ContactItem(
                            name = item.name,
                            email = item.email,
                            hasNewMessages = item.hasNewMessages,
                            onClick = { handleChatClick(item.email, false) },
                            onRename = { newName -> scope.launch { repository.renameContact(item.email, newName) } },
                            onDelete = { scope.launch { repository.deleteContactCompletely(item.email) } }
                        )
                    }
                }
            }
        }
    }}
}



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupItem(name: String, onClick: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var renameDialogOpen by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(name) }

    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
        tonalElevation = 1.dp
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(name.take(1).uppercase(), color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }

    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menuOpen = false; renameDialogOpen = true })
        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menuOpen = false; onDelete() })
    }

    if (renameDialogOpen) {
        AlertDialog(
            onDismissRequest = { renameDialogOpen = false },
            title = { Text(stringResource(R.string.rename)) },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }) },
            confirmButton = {
                TextButton(onClick = { onRename(newName); renameDialogOpen = false }) { Text("ОК") }
            },
            dismissButton = { TextButton(onClick = { renameDialogOpen = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactItem(
    name: String,
    email: String,
    hasNewMessages: Boolean,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renameDialogOpen by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(name) }
    val context = LocalContext.current

    var avatarBitmap by remember(email) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(email) {
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val contact = db.contactDao().getContactByEmail(email)
                contact?.avatarPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        avatarBitmap = bitmap?.asImageBitmap()
                    }
                }
            } catch (e: Exception) {
                avatarBitmap = null
            }
        }
    }

    Column {
        Surface(
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap!!,
                        contentDescription = null,
                        modifier = Modifier.size(50.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }

                if (hasNewMessages) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.rename))}, onClick = { menuOpen = false; renameDialogOpen = true })
            DropdownMenuItem(text = { Text(stringResource(R.string.delete))}, onClick = { menuOpen = false; onDelete() })
        }

        if (renameDialogOpen) {
            AlertDialog(
                onDismissRequest = { renameDialogOpen = false },
                title = { Text(stringResource(R.string.rename)) },
                text = { OutlinedTextField(value = newName, onValueChange = { newName = it }) },
                confirmButton = { TextButton(onClick = { onRename(newName); renameDialogOpen = false }) { Text("ОК") } },
                dismissButton = { TextButton(onClick = { renameDialogOpen = false }) { Text(stringResource(R.string.cancel))} }
            )
        }
    }
}