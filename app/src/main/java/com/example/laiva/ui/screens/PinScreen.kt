package com.example.laiva.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.MessageDigest



import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback






@Composable
fun PinScreen(
    correctHash: String?,
    panicHash: String?,
    onSuccess: () -> Unit,
    onPanic: () -> Unit,
    isSettingUp: Boolean = false
) {
    var input by remember { mutableStateOf("") }

    LaunchedEffect(input) {
        if (input.isNotEmpty()) {
            val hashed = hashString(input)
            if (hashed == correctHash) {
                onSuccess()
                input = ""
            } else if (hashed == panicHash) {
                onPanic()
                input = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isSettingUp) "Установите PIN-код" else "Введите PIN-код",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(48.dp))


        Row(
            modifier = Modifier.height(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (input.isEmpty()) {

                Surface(
                    modifier = Modifier.size(12.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {}
            } else {
                repeat(input.length) {
                    Surface(
                        modifier = Modifier.size(14.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {}
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))

        // Сетка кнопок
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "DEL")
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { char ->
                        if (char.isEmpty()) {
                            Spacer(modifier = Modifier.size(84.dp))
                        } else {
                            PinButton(
                                text = char,
                                onClick = {
                                    if (char == "DEL") {
                                        if (input.isNotEmpty()) input = input.dropLast(1)
                                    } else {
                                        if (input.length < 12) {
                                            input += char
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinButton(text: String, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    FilledTonalButton(
        onClick = {onClick()
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)},
        modifier = Modifier.size(84.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        if (text == "DEL") {
            Icon(Icons.Default.Backspace, contentDescription = null, modifier = Modifier.size(28.dp))
        } else {
            Text(text = text, fontSize = 30.sp, fontWeight = FontWeight.Normal)
        }
    }
}

private fun hashString(input: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }
}