package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Ronova Brand Identity Colors: Deep Purple to Red/Crimson
val RonovaDeepPurple = Color(0xFF6B11B0)
val RonovaPurple = Color(0xFF8A2BE2)
val RonovaPurpleLight = Color(0xFFA855F7)
val RonovaCrimson = Color(0xFFE01E5A)
val RonovaRed = Color(0xFFFF2A6D)
val RonovaGlow = Color(0xFFFF4D85)

// Category Colors
val CategoryFlash = Color(0xFFFFB703)     // ⚡ Flash
val CategorySystem = Color(0xFF8B5CF6)    // 🧠 System
val CategoryAstra = Color(0xFF06D6A0)     // ☄️ Astra

// Neutral Dark-first Backgrounds (Soft charcoal / near-black)
val DarkBackground = Color(0xFF111016)
val DarkSurface = Color(0xFF181720)
val DarkSurfaceVariant = Color(0xFF22202C)
val DarkSurfaceElevated = Color(0xFF2C2939)
val DarkOutline = Color(0xFF383447)
val DarkOutlineSubtle = Color(0xFF262332)

// Dark-first Typography & Content Colors
val DarkOnBackground = Color(0xFFF4F2F8)
val DarkOnSurface = Color(0xFFE6E3ED)
val DarkOnSurfaceVariant = Color(0xFFA5A0B4)
val DarkOnSurfaceMuted = Color(0xFF757085)

// Light Theme Counterparts
val LightBackground = Color(0xFFF9F8FD)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF0EDF7)
val LightSurfaceElevated = Color(0xFFE7E3F0)
val LightOutline = Color(0xFFD6D0E2)
val LightOutlineSubtle = Color(0xFFE9E5F2)
val LightOnBackground = Color(0xFF16141D)
val LightOnSurface = Color(0xFF1F1B28)
val LightOnSurfaceVariant = Color(0xFF5E586E)
val LightOnSurfaceMuted = Color(0xFF8A849B)

// Shared Brand Gradients
val RonovaBrandGradient = Brush.horizontalGradient(
    colors = listOf(RonovaPurple, RonovaCrimson)
)

val RonovaVerticalGradient = Brush.verticalGradient(
    colors = listOf(RonovaPurple, RonovaCrimson)
)

val RonovaOrbitalGradient = Brush.linearGradient(
    colors = listOf(RonovaDeepPurple, RonovaPurple, RonovaCrimson, RonovaRed)
)
