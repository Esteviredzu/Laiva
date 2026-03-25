package com.example.laiva.ui.screens

import android.app.LocaleManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.laiva.DataStoreManager
import com.example.laiva.R
import kotlinx.coroutines.launch
import java.io.File
import com.example.laiva.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val dataStoreManager = remember { DataStoreManager(context) }

    // Состояния безопасности
    val isPinEnabled by dataStoreManager.isPinEnabledFlow.collectAsState(initial = false)
    val currentPinHash by dataStoreManager.pinHashFlow.collectAsState(initial = null)

    var showPinDialog by remember { mutableStateOf(false) }

    val ignoreNonContact by dataStoreManager.ignoreNonContactMessagesFlow.collectAsState(initial = false)
    val ignoreUnknownGroups by dataStoreManager.ignoreUnknownGroupsFlow.collectAsState(initial = false)



    val pinsSavedText = stringResource(R.string.pins_saved)
    // Состояние аватарки
    var avatarUri by remember {
        mutableStateOf<Uri?>(
            File(context.filesDir, "avatars/me.jpg").let { if (it.exists()) Uri.fromFile(it) else null }
        )
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            onAvatarSelected(context, it)
            avatarUri = it
        }
    }

    val currentLocale = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lm = context.getSystemService(Context.LOCALE_SERVICE) as LocaleManager
            val appLocales = lm.applicationLocales

            if (!appLocales.isEmpty) {
                appLocales.toLanguageTags()
            } else {
                LocaleList.getDefault()[0].language
            }
        } else {
            //Для старых android
            context.resources.configuration.locales[0].language
        }
    }

    //пин коды
    if (showPinDialog) {
        PinSetupDialog(
            onDismiss = { showPinDialog = false },
            onSave = { pin, panic ->
                scope.launch {
                    dataStoreManager.savePinSettings(true, pin, panic)
                    Toast.makeText(context, pinsSavedText, Toast.LENGTH_SHORT).show()
                    showPinDialog = false
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            ProfileHeader(
                avatarUri = avatarUri,
                onEditClick = { launcher.launch("image/*") },
                onDeleteClick = { deleteAvatar(context); avatarUri = null }
            )

            //Безопасность
            SettingsSectionCard(title = stringResource(R.string.section_security)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.pin_protection)) },
                    supportingContent = {
                        val text = if (!currentPinHash.isNullOrEmpty()) stringResource(R.string.pin_active) else stringResource(R.string.pin_not_set)
                        Text(text)
                    },
                    trailingContent = {
                        Switch(
                            checked = isPinEnabled && !currentPinHash.isNullOrEmpty(),
                            onCheckedChange = { enabled ->
                                if (enabled && currentPinHash.isNullOrEmpty()) {
                                    showPinDialog = true
                                } else {
                                    scope.launch { dataStoreManager.savePinSettings(enabled, null, null) }
                                }
                            }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

                ListItem(
                    modifier = Modifier.clickable { showPinDialog = true },
                    headlineContent = {
                        Text(if (currentPinHash.isNullOrEmpty()) stringResource(R.string.setup_pins) else stringResource(R.string.change_pins))
                    },
                    leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingContent = { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }


            SettingsSectionCard(title = stringResource(R.string.section_filtering)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.only_contacts)) },
                    supportingContent = { Text(stringResource(R.string.only_contacts_desc)) },
                    trailingContent = {
                        Switch(
                            checked = ignoreNonContact,
                            onCheckedChange = { scope.launch { dataStoreManager.setIgnoreNonContactMessages(it) } }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

                ListItem(
                    headlineContent = { Text(stringResource(R.string.ignore_new_groups)) },
                    supportingContent = { Text(stringResource(R.string.ignore_new_groups_desc)) },
                    trailingContent = {
                        Switch(
                            checked = ignoreUnknownGroups,
                            onCheckedChange = { scope.launch { dataStoreManager.setIgnoreUnknownGroups(it) } }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }




            //Язык
            SettingsSectionCard(title = stringResource(R.string.app_language)) {
                val languages = listOf(
                    stringResource(R.string.lang_en) to "en",
                    stringResource(R.string.lang_ru) to "ru",
                    stringResource(R.string.lang_lv) to "lv",
                    stringResource(R.string.lang_eo) to "eo"
                )
                languages.forEachIndexed { index, (name, code) ->
                    LanguageItem(
                        label = name,
                        isSelected = currentLocale.startsWith(code),
                        onClick = { updateLocale(context, code) },
                        isLast = index == languages.lastIndex
                    )
                }
            }

            // Эбаут
            SettingsSectionCard(title = stringResource(R.string.about_app)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_version)) },
                    trailingContent = { Text(
                        text = BuildConfig.VERSION_NAME,
                        color = MaterialTheme.colorScheme.outline
                    ) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
fun PinSetupDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var panic by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }



    val errLength = stringResource(R.string.error_pin_length)
    val errMatch = stringResource(R.string.error_pin_match)
    val errConflict = stringResource(R.string.error_pin_conflict)
    val saveBtnText = stringResource(R.string.save)
    val cancelBtnText = stringResource(R.string.cancel)



    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_pin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.pin_requirements),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { input ->
                        pin = input.filter { it.isDigit() }
                        errorText = null
                    },
                    label = { Text(stringResource(R.string.label_main_pin)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = panic,
                    onValueChange = { input ->
                        panic = input.filter { it.isDigit() }
                        errorText = null
                    },
                    label = { Text(stringResource(R.string.label_panic_pin)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },


        confirmButton = {
            Button(onClick = {
                val validationError = when {
                    pin.length < 4 || panic.length < 4 -> errLength
                    pin == panic -> errMatch
                    pin.startsWith(panic) || panic.startsWith(pin) -> errConflict
                    else -> null
                }

                if (validationError != null) {
                    errorText = validationError
                } else {
                    onSave(pin, panic)
                }
            }) {
                Text(saveBtnText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelBtnText) }
        }
    )
}

@Composable
fun ProfileHeader(avatarUri: Uri?, onEditClick: () -> Unit, onDeleteClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.size(110.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                if (avatarUri != null) {
                    AsyncImage(
                        model = avatarUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(modifier = Modifier.offset(y = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallFloatingActionButton(
                    onClick = onEditClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                if (avatarUri != null) {
                    SmallFloatingActionButton(
                        onClick = onDeleteClick,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(text = stringResource(R.string.profile_setup), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SettingsSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun LanguageItem(label: String, isSelected: Boolean, onClick: () -> Unit, isLast: Boolean) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                RadioButton(selected = isSelected, onClick = null)
            }
            if (!isLast) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

fun onAvatarSelected(context: Context, uri: Uri) {
    try {
        val input = context.contentResolver.openInputStream(uri) ?: return
        val bytes = input.readBytes()
        val avatarsDir = File(context.filesDir, "avatars")
        if (!avatarsDir.exists()) avatarsDir.mkdirs()
        File(avatarsDir, "me.jpg").writeBytes(bytes)
    } catch (e: Exception) { e.printStackTrace() }
}

fun deleteAvatar(context: Context) {
    val file = File(context.filesDir, "avatars/me.jpg")
    if (file.exists()) file.delete()
}

private fun updateLocale(context: Context, languageCode: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val lm = context.getSystemService(Context.LOCALE_SERVICE) as LocaleManager
        lm.applicationLocales = LocaleList.forLanguageTags(languageCode)
    }
}