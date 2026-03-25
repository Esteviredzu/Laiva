package com.example.laiva.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.Session
import javax.mail.Store
import androidx.compose.ui.res.stringResource
import com.example.laiva.R
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val registrationUrl = "https://reg.stupidsitec.mooo.com"

    val errInvalidEmail = stringResource(R.string.error_invalid_email)
    val errAuthFailed = stringResource(R.string.error_auth_failed)

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.login_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it.trim()
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.label_email)) },
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.label_password)) },
                    singleLine = true,
                    isError = errorMessage != null,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !isLoading
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(top = 8.dp, start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        if (email.contains("@")) {
                            isLoading = true
                            errorMessage = null

                            scope.launch {
                                val domain = email.substringAfter("@")
                                val isValid = verifyMailCredentials(email, password, domain)

                                if (isValid) {
                                    onLoginSuccess(email, password)
                                } else {
                                    isLoading = false
                                    errorMessage = errAuthFailed
                                }
                            }
                        } else {
                            errorMessage = errInvalidEmail
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large,
                    enabled = !isLoading && email.isNotEmpty() && password.isNotEmpty()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.btn_next), style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                TextButton(
                    onClick = { uriHandler.openUri(registrationUrl) },
                    enabled = !isLoading
                ) {
                    Text(
                        stringResource(R.string.btn_register_desc),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

suspend fun verifyMailCredentials(user: String, pass: String, host: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val props = Properties().apply {
                setProperty("mail.store.protocol", "imaps")
                setProperty("mail.imaps.host", host)
                setProperty("mail.imaps.port", "993")
                setProperty("mail.imaps.timeout", "5000")
                setProperty("mail.imaps.connectiontimeout", "5000")
            }

            val session = Session.getInstance(props)
            val store: Store = session.getStore("imaps")

            store.connect(host, 993, user, pass)
            store.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}