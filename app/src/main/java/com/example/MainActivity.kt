package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.AppDatabase
import com.example.data.repository.ConversationRepository
import com.example.data.repository.EndpointRepository
import com.example.data.repository.WorkspaceRepository
import com.example.ui.screens.addmodel.AddModelScreen
import com.example.ui.screens.addmodel.AddModelViewModel
import com.example.ui.screens.chat.ChatScreen
import com.example.ui.screens.chat.ChatViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.theme.RonovaTheme

sealed class Screen {
    data object Home : Screen()
    data class AddModel(val returnToConversationId: String? = null) : Screen()
    data class Chat(val conversationId: String) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getInstance(applicationContext)
        val endpointRepository = EndpointRepository(database.endpointProfileDao())
        val workspaceRepository = WorkspaceRepository(database.workspaceDao())
        val conversationRepository = ConversationRepository(
            database.conversationDao(),
            database.messageDao()
        )

        setContent {
            RonovaTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RonovaAppNavigation(
                        endpointRepository = endpointRepository,
                        workspaceRepository = workspaceRepository,
                        conversationRepository = conversationRepository
                    )
                }
            }
        }
    }
}

@Composable
fun RonovaAppNavigation(
    endpointRepository: EndpointRepository,
    workspaceRepository: WorkspaceRepository,
    conversationRepository: ConversationRepository
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen_navigation"
    ) { screen ->
        when (screen) {
            is Screen.Home -> {
                val homeViewModel = remember {
                    HomeViewModel(
                        workspaceRepository = workspaceRepository,
                        conversationRepository = conversationRepository,
                        endpointRepository = endpointRepository
                    )
                }
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToAddModel = { currentScreen = Screen.AddModel() },
                    onOpenConversation = { convId -> currentScreen = Screen.Chat(convId) }
                )
            }

            is Screen.AddModel -> {
                val addModelViewModel = remember {
                    AddModelViewModel(endpointRepository = endpointRepository)
                }
                AddModelScreen(
                    viewModel = addModelViewModel,
                    onBack = {
                        val returnConvId = screen.returnToConversationId
                        currentScreen = if (returnConvId != null) {
                            Screen.Chat(returnConvId)
                        } else {
                            Screen.Home
                        }
                    },
                    onProfileSaved = { savedProfile ->
                        val returnConvId = screen.returnToConversationId
                        currentScreen = if (returnConvId != null) {
                            Screen.Chat(returnConvId)
                        } else {
                            Screen.Home
                        }
                    }
                )
            }

            is Screen.Chat -> {
                val chatViewModel = remember(screen.conversationId) {
                    ChatViewModel(
                        conversationId = screen.conversationId,
                        conversationRepository = conversationRepository,
                        endpointRepository = endpointRepository
                    )
                }
                ChatScreen(
                    viewModel = chatViewModel,
                    onBack = { currentScreen = Screen.Home },
                    onNavigateToAddModel = {
                        currentScreen = Screen.AddModel(returnToConversationId = screen.conversationId)
                    }
                )
            }
        }
    }
}
