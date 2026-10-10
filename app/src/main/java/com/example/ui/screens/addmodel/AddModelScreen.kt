package com.example.ui.screens.addmodel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EndpointProfile
import com.example.data.model.ProfileCategory
import com.example.data.model.ProviderType
import com.example.ui.components.RonovaLoadingIndicator
import com.example.ui.theme.CategoryAstra
import com.example.ui.theme.CategoryFlash
import com.example.ui.theme.CategorySystem
import com.example.ui.theme.RonovaBrandGradient
import com.example.ui.theme.RonovaCrimson
import com.example.ui.theme.RonovaPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddModelScreen(
    viewModel: AddModelViewModel,
    onBack: () -> Unit,
    onProfileSaved: (EndpointProfile) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Add Model Endpoint",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("add_model_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Provider & Connection Type
            Text(
                text = "PROVIDER / PROTOCOL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProviderChip(
                    name = "OpenAI / Local",
                    isSelected = state.providerType == ProviderType.OPENAI_COMPATIBLE,
                    onClick = { viewModel.setProviderType(ProviderType.OPENAI_COMPATIBLE) }
                )
                ProviderChip(
                    name = "Google Gemini",
                    isSelected = state.providerType == ProviderType.GEMINI,
                    onClick = { viewModel.setProviderType(ProviderType.GEMINI) }
                )
                ProviderChip(
                    name = "Anthropic Claude",
                    isSelected = state.providerType == ProviderType.ANTHROPIC,
                    onClick = { viewModel.setProviderType(ProviderType.ANTHROPIC) }
                )
            }

            if (state.providerType == ProviderType.OPENAI_COMPATIBLE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ConnectionTypeCard(
                        title = "Local Cloud",
                        subtitle = "Ollama, LM Studio, vLLM on LAN",
                        icon = Icons.Default.Dns,
                        isSelected = state.connectionType == ConnectionType.LOCAL_CLOUD,
                        onClick = { viewModel.setConnectionType(ConnectionType.LOCAL_CLOUD) },
                        modifier = Modifier.weight(1f)
                    )

                    ConnectionTypeCard(
                        title = "Cloud API",
                        subtitle = "OpenAI, Groq, DeepSeek",
                        icon = Icons.Default.Cloud,
                        isSelected = state.connectionType == ConnectionType.CLOUD_API,
                        onClick = { viewModel.setConnectionType(ConnectionType.CLOUD_API) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quick Presets
            Text(
                text = "QUICK PRESETS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (state.providerType) {
                    ProviderType.GEMINI -> {
                        PresetChip("Gemini 2.5 Flash", "https://generativelanguage.googleapis.com", ProfileCategory.FLASH) { name, url, cat ->
                            viewModel.applyPreset(name, url, cat, ProviderType.GEMINI, "gemini-2.5-flash")
                        }
                        PresetChip("Gemini 2.5 Pro", "https://generativelanguage.googleapis.com", ProfileCategory.SYSTEM) { name, url, cat ->
                            viewModel.applyPreset(name, url, cat, ProviderType.GEMINI, "gemini-2.5-pro")
                        }
                        PresetChip("Gemini 2.0 Flash", "https://generativelanguage.googleapis.com", ProfileCategory.FLASH) { name, url, cat ->
                            viewModel.applyPreset(name, url, cat, ProviderType.GEMINI, "gemini-2.0-flash")
                        }
                    }
                    ProviderType.ANTHROPIC -> {
                        PresetChip("Claude 3.7 Sonnet (Thinking)", "https://api.anthropic.com/v1", ProfileCategory.SYSTEM) { name, url, cat ->
                            viewModel.applyPreset(name, url, cat, ProviderType.ANTHROPIC, "claude-3-7-sonnet-20250219")
                        }
                        PresetChip("Claude 3.5 Sonnet", "https://api.anthropic.com/v1", ProfileCategory.SYSTEM) { name, url, cat ->
                            viewModel.applyPreset(name, url, cat, ProviderType.ANTHROPIC, "claude-3-5-sonnet-20241022")
                        }
                        PresetChip("Claude 3.5 Haiku", "https://api.anthropic.com/v1", ProfileCategory.FLASH) { name, url, cat ->
                            viewModel.applyPreset(name, url, cat, ProviderType.ANTHROPIC, "claude-3-5-haiku-20241022")
                        }
                    }
                    else -> {
                        if (state.connectionType == ConnectionType.LOCAL_CLOUD) {
                            PresetChip("Ollama (Emulator)", "http://10.0.2.2:11434/v1", ProfileCategory.ASTRA) { name, url, cat ->
                                viewModel.applyPreset(name, url, cat, ProviderType.OPENAI_COMPATIBLE)
                            }
                            PresetChip("Ollama (LAN)", "http://192.168.1.100:11434/v1", ProfileCategory.ASTRA) { name, url, cat ->
                                viewModel.applyPreset(name, url, cat, ProviderType.OPENAI_COMPATIBLE)
                            }
                            PresetChip("LM Studio", "http://10.0.2.2:1234/v1", ProfileCategory.ASTRA) { name, url, cat ->
                                viewModel.applyPreset(name, url, cat, ProviderType.OPENAI_COMPATIBLE)
                            }
                            PresetChip("Localhost", "http://127.0.0.1:11434/v1", ProfileCategory.ASTRA) { name, url, cat ->
                                viewModel.applyPreset(name, url, cat, ProviderType.OPENAI_COMPATIBLE)
                            }
                        } else {
                            PresetChip("OpenAI", "https://api.openai.com/v1", ProfileCategory.FLASH) { name, url, cat ->
                                viewModel.applyPreset(name, url, cat, ProviderType.OPENAI_COMPATIBLE, "gpt-4o")
                            }
                            PresetChip("Groq Cloud", "https://api.groq.com/openai/v1", ProfileCategory.FLASH) { name, url, cat ->
                                viewModel.applyPreset(name, url, cat, ProviderType.OPENAI_COMPATIBLE, "llama-3.3-70b-versatile")
                            }
                            PresetChip("DeepSeek", "https://api.deepseek.com/v1", ProfileCategory.SYSTEM) { name, url, cat ->
                                viewModel.applyPreset(name, url, cat, ProviderType.OPENAI_COMPATIBLE, "deepseek-chat")
                            }
                            PresetChip("OpenRouter", "https://openrouter.ai/api/v1", ProfileCategory.FLASH) { name, url, cat ->
                                viewModel.applyPreset(name, url, cat, ProviderType.OPENAI_COMPATIBLE)
                            }
                        }
                    }
                }
            }

            // 2. Profile Details
            Text(
                text = "ENDPOINT CONFIGURATION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Display Name") },
                placeholder = { Text("e.g. My Local Qwen, Flash GPT-4o") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("model_name_input"),
                singleLine = true,
                colors = textFieldColors()
            )

            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = { viewModel.updateBaseUrl(it) },
                label = { Text("Base URL") },
                placeholder = {
                    Text(if (state.connectionType == ConnectionType.LOCAL_CLOUD) "http://10.0.2.2:11434/v1" else "https://api.openai.com/v1")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("base_url_input"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                supportingText = {
                    Text(
                        when (state.providerType) {
                            ProviderType.GEMINI -> "Official Google Generative AI REST endpoint (v1beta)"
                            ProviderType.ANTHROPIC -> "Official Anthropic Claude API (v1/messages)"
                            else -> if (state.connectionType == ConnectionType.LOCAL_CLOUD)
                                "Local LAN endpoints work over standard HTTP (e.g. http://192.168.x.x:port/v1)"
                            else
                                "Must implement OpenAI-compatible /models and /chat/completions"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                colors = textFieldColors()
            )

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = { viewModel.updateApiKey(it) },
                label = {
                    Text(
                        when (state.providerType) {
                            ProviderType.GEMINI -> "Gemini API Key"
                            ProviderType.ANTHROPIC -> "Anthropic API Key"
                            else -> if (state.connectionType == ConnectionType.LOCAL_CLOUD) "API Key (Optional for Local)" else "API Key"
                        }
                    )
                },
                placeholder = {
                    Text(
                        when (state.providerType) {
                            ProviderType.GEMINI -> "AIzaSy..."
                            ProviderType.ANTHROPIC -> "sk-ant-api..."
                            else -> if (state.connectionType == ConnectionType.LOCAL_CLOUD) "Optional" else "sk-..."
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_key_input"),
                singleLine = true,
                visualTransformation = if (state.isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { viewModel.toggleApiKeyVisibility() }) {
                        Icon(
                            imageVector = if (state.isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (state.isApiKeyVisible) "Hide Key" else "Show Key"
                        )
                    }
                },
                supportingText = {
                    Text(
                        "Stored securely in hardware-backed AndroidKeyStore. Never logged.",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                colors = textFieldColors()
            )

            // 3. Test Connection Action
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Connection Verification",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Must test connection before saving to discover models and verify reachability.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (state.isTesting) {
                            OutlinedButton(
                                onClick = { viewModel.cancelTest() },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = RonovaCrimson
                                )
                            ) {
                                Text("Cancel")
                            }
                        }
                    }

                    if (state.isTesting) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RonovaLoadingIndicator(
                                size = 44.dp,
                                modifier = Modifier.testTag("connection_test_indicator")
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Testing endpoint & discovering models...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Button(
                            onClick = { viewModel.testConnection() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("test_connection_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RonovaPurple,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (state.hasTestedSuccessfully) "Re-test Connection" else "Test Connection",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Test Error Banner
                    AnimatedVisibility(
                        visible = state.testError != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        state.testError?.let { err ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(RonovaCrimson.copy(alpha = 0.15f))
                                    .border(1.dp, RonovaCrimson.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = RonovaCrimson,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Test Success Banner
                    AnimatedVisibility(
                        visible = state.testSuccess != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        state.testSuccess?.let { status ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CategoryAstra.copy(alpha = 0.15f))
                                    .border(1.dp, CategoryAstra.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = CategoryAstra,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = status.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 4. Discovered Models (shown when test succeeds)
            AnimatedVisibility(visible = state.discoveredModels.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "DISCOVERED MODELS (${state.discoveredModels.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            state.discoveredModels.forEach { model ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.selectModel(model.id) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = state.selectedModel == model.id,
                                        onClick = { viewModel.selectModel(model.id) },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = RonovaCrimson,
                                            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = model.id,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (state.selectedModel == model.id) FontWeight.Bold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (!model.ownedBy.isNullOrEmpty()) {
                                            Text(
                                                text = "by ${model.ownedBy}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Category Selection (Flash / System / Astra)
            Text(
                text = "ASSIGN CATEGORY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChoiceChip(
                    emoji = "⚡",
                    name = "Flash",
                    desc = "Fast & lightweight",
                    accentColor = CategoryFlash,
                    isSelected = state.category == ProfileCategory.FLASH,
                    onClick = { viewModel.updateCategory(ProfileCategory.FLASH) },
                    modifier = Modifier.weight(1f)
                )

                CategoryChoiceChip(
                    emoji = "🧠",
                    name = "System",
                    desc = "Deep & capable",
                    accentColor = CategorySystem,
                    isSelected = state.category == ProfileCategory.SYSTEM,
                    onClick = { viewModel.updateCategory(ProfileCategory.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )

                CategoryChoiceChip(
                    emoji = "☄️",
                    name = "Astra",
                    desc = "Local & custom",
                    accentColor = CategoryAstra,
                    isSelected = state.category == ProfileCategory.ASTRA,
                    onClick = { viewModel.updateCategory(ProfileCategory.ASTRA) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 6. Save Profile Button
            Button(
                onClick = {
                    viewModel.saveProfile { saved ->
                        onProfileSaved(saved)
                    }
                },
                enabled = state.hasTestedSuccessfully && state.selectedModel.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_model_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RonovaCrimson,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = Color.White,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (state.hasTestedSuccessfully) "Save Endpoint Profile" else "Test Connection to Enable Saving",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConnectionTypeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) RonovaPurple else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) RonovaPurple.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) RonovaPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryChoiceChip(
    emoji: String,
    name: String,
    desc: String,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(10.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = emoji, fontSize = 20.sp)
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    url: String,
    category: String,
    onClick: (String, String, String) -> Unit
) {
    FilterChip(
        selected = false,
        onClick = { onClick(label, url, category) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = false,
            borderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

@Composable
private fun ProviderChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = RonovaPurple.copy(alpha = 0.2f),
            selectedLabelColor = RonovaPurple,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = if (isSelected) RonovaPurple else MaterialTheme.colorScheme.outlineVariant
        )
    )
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = RonovaPurple,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedLabelColor = RonovaPurple,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
)
