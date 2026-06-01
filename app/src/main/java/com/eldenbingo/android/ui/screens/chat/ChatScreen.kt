package com.eldenbingo.android.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eldenbingo.android.data.model.ChatMessage
import com.eldenbingo.android.ui.components.ChatMessageItem
import com.eldenbingo.android.ui.theme.EldenGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        TopAppBar(
            title = {
                Text(
                    "Chat",
                    color = EldenGold,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = EldenGold)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF2D2D2D)
            )
        )

        // Messages list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(messages) { message ->
                ChatMessageItem(message = message)
            }

            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No messages yet. Say hello!",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D2D))
                .navigationBarsPadding()
                .imePadding()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                placeholder = { Text("Type a message...", color = Color.Gray) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF3D3D3D),
                    unfocusedContainerColor = Color(0xFF3D3D3D),
                    focusedBorderColor = EldenGold,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = EldenGold
                )
            )

            IconButton(
                onClick = {
                    if (messageText.isNotBlank()) {
                        onSendMessage(messageText)
                        messageText = ""
                    }
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = EldenGold
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.Black
                )
            }
        }
    }
}
