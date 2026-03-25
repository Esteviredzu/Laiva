package com.example.laiva

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.laiva.data.AppDatabase
import com.example.laiva.data.MessengerRepository
import com.example.laiva.services.LaivaMessagingService
import com.example.laiva.ui.screens.*
import com.example.laiva.ui.theme.LaivaTheme

class MainActivity : ComponentActivity() {

    companion object {
        var activeChatEmail: String? = null
    }

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        val dataStoreManager = DataStoreManager(applicationContext)
        val repository = MessengerRepository(
            AppDatabase.getDatabase(applicationContext),
            dataStoreManager
        )

        setContent {

            val isLocked by viewModel.isAppLocked.collectAsState()

            val dataStoreManager = remember { DataStoreManager(applicationContext) }
            val pinHash by dataStoreManager.pinHashFlow.collectAsState(initial = null)
            val panicHash by dataStoreManager.panicPinHashFlow.collectAsState(initial = null)

            LaivaTheme {
                val navController = rememberNavController()
                val isLoggedIn by viewModel.isLoggedIn.collectAsState()
                val email by viewModel.email.collectAsState()
                val password by viewModel.password.collectAsState()

                val navBackStackEntry by navController.currentBackStackEntryAsState()

                LaunchedEffect(navBackStackEntry) {
                    val route = navBackStackEntry?.destination?.route
                    if (route?.startsWith("dialog/") == true) {
                        val contact = navBackStackEntry?.arguments?.getString("contactEmail")
                        activeChatEmail = contact
                        contact?.let { cancelNotificationForContact(it) }
                    } else {
                        activeChatEmail = null
                    }
                }

                LaunchedEffect(intent) {
                    checkIntentForEmail(intent, navController)
                }

                LaunchedEffect(isLoggedIn) {
                    if (isLoggedIn && email != null && password != null) {
                        val serviceIntent = Intent(this@MainActivity, LaivaMessagingService::class.java).apply {
                            putExtra("email", email)
                            putExtra("password", password)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {


                    if (isLocked) {
                        PinScreen(
                            correctHash = pinHash,
                            panicHash = panicHash,
                            onSuccess = { viewModel.unlockApp() },
                            onPanic = { viewModel.triggerPanic(this@MainActivity) }
                        )
                    } else {

                        NavHost(
                            navController = navController,
                            startDestination = if (isLoggedIn) "chat" else "login",
                            enterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Start,
                                    tween(400, easing = FastOutSlowInEasing)
                                ) + fadeIn(tween(400))
                            },
                            exitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Start,
                                    tween(400, easing = FastOutSlowInEasing),
                                    targetOffset = { it / 4 }) + fadeOut(tween(400))
                            },
                            popEnterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.End,
                                    tween(400, easing = FastOutSlowInEasing),
                                    initialOffset = { it / 4 }) + fadeIn(tween(400))
                            },
                            popExitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.End,
                                    tween(400, easing = FastOutSlowInEasing)
                                ) + fadeOut(tween(400))
                            }
                        ) {
                            // Экран логина
                            composable("login") {
                                LoginScreen(
                                    onLoginSuccess = { inputEmail, inputPassword ->
                                        viewModel.login(inputEmail, inputPassword)
                                        navController.navigate("chat") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }
                                )
                            }


                            composable("chat") {
                                ChatScreen(
                                    email = email ?: "",
                                    repository = repository,
                                    dataStoreManager = dataStoreManager,
                                    onAddContactClick = { navController.navigate("add") },
                                    onAddGroupClick = { navController.navigate("add_group") },
                                    onContactClick = { contactEmail -> navController.navigate("dialog/$contactEmail") },
                                    onGroupClick = { groupId -> navController.navigate("dialog/$groupId") },
                                    onSettingsClick = { navController.navigate("settings") }
                                )
                            }

                            composable("settings") {
                                SettingsScreen(onBack = { navController.popBackStack() })
                            }

                            composable("add") {
                                AddContactScreen(
                                    repository = repository,
                                    myEmail = email ?: "",
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable("add_group") {
                                AddGroupScreen(
                                    repository = repository,
                                    myEmail = email ?: "",
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = "dialog/{contactEmail}",
                                arguments = listOf(navArgument("contactEmail") {
                                    type = NavType.StringType
                                })
                            ) { backStackEntry ->
                                val contactEmail =
                                    backStackEntry.arguments?.getString("contactEmail") ?: ""
                                DialogScreen(
                                    contactEmail = contactEmail,
                                    userEmail = email ?: "",
                                    userPassword = password ?: "",
                                    repository = repository,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun checkIntentForEmail(intent: Intent?, navController: NavHostController) {
        val emailFromNotification = intent?.getStringExtra("OPEN_CHAT_EMAIL")
        if (emailFromNotification != null) {
            navController.navigate("dialog/$emailFromNotification") {
                popUpTo("chat") { inclusive = false }
                launchSingleTop = true
            }
            intent.removeExtra("OPEN_CHAT_EMAIL")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "LAIVA_MESSAGES",
                "Laiva",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Messages"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun cancelNotificationForContact(contactEmail: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(contactEmail.hashCode())
    }
}