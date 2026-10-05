package com.acme.tokens

data class FontWeightToken(val weight: Int, val italic: Boolean)

data class TypographyToken(
    val fontFamily: List<String>?,
    val fontSize: Float?,
    val fontWeight: FontWeightToken?,
    val lineHeight: Float?,
    val letterSpacing: Float?,
    val textCase: String?,
    val textDecoration: String?,
)

data class ShadowToken(
    val offsetX: Float,
    val offsetY: Float,
    val blur: Float,
    val spread: Float,
    val color: Long,
    val inset: Boolean,
)

data class Tokens(
    val space: TokensSpace,
    val radius: TokensRadius,
    val font: TokensFont,
    val type: TokensType,
    val shadow: TokensShadow,
    val opacity: TokensOpacity,
    val color: TokensColor,
    val library: TokensLibrary,
)

data class TokensSpace(
    val base: Float,
)

data class TokensRadius(
    val card: Float,
)

data class TokensFont(
    val weightStrong: FontWeightToken,
)

data class TokensType(
    val body: TypographyToken,
)

data class TokensShadow(
    val lift: List<ShadowToken>,
)

data class TokensOpacity(
    val muted: Float,
)

data class TokensColor(
    val bg: Long,
    val text: Long,
    val accent: Long,
)

data class TokensLibrary(
    val colorBrandPrimary: Long,
)

object TokensThemes {
    val brandAModeLight: Tokens = Tokens(
        space = TokensSpace(
            base = 4.0f,
        ),
        radius = TokensRadius(
            card = 8.0f,
        ),
        font = TokensFont(
            weightStrong = FontWeightToken(weight = 700, italic = true),
        ),
        type = TokensType(
            body = TypographyToken(fontFamily = listOf("Inter", "Segoe UI"), fontSize = 16.0f, fontWeight = FontWeightToken(weight = 400, italic = false), lineHeight = 1.5f, letterSpacing = null, textCase = null, textDecoration = null),
        ),
        shadow = TokensShadow(
            lift = listOf(ShadowToken(offsetX = 0.0f, offsetY = 2.0f, blur = 4.0f, spread = 0.0f, color = 0x40000000L, inset = false)),
        ),
        opacity = TokensOpacity(
            muted = 0.5f,
        ),
        color = TokensColor(
            bg = 0xFFFFFFFF,
            text = 0xFF111111,
            accent = 0xFF3366FF,
        ),
        library = TokensLibrary(
            colorBrandPrimary = 0xFF3366FF,
        ),
    )

    val brandAModeDark: Tokens = Tokens(
        space = TokensSpace(
            base = 4.0f,
        ),
        radius = TokensRadius(
            card = 8.0f,
        ),
        font = TokensFont(
            weightStrong = FontWeightToken(weight = 700, italic = true),
        ),
        type = TokensType(
            body = TypographyToken(fontFamily = listOf("Inter", "Segoe UI"), fontSize = 16.0f, fontWeight = FontWeightToken(weight = 400, italic = false), lineHeight = 1.5f, letterSpacing = null, textCase = null, textDecoration = null),
        ),
        shadow = TokensShadow(
            lift = listOf(ShadowToken(offsetX = 0.0f, offsetY = 2.0f, blur = 4.0f, spread = 0.0f, color = 0x40000000L, inset = false)),
        ),
        opacity = TokensOpacity(
            muted = 0.5f,
        ),
        color = TokensColor(
            bg = 0xFF111111,
            text = 0xFF111111,
            accent = 0xFF3366FF,
        ),
        library = TokensLibrary(
            colorBrandPrimary = 0xFF3366FF,
        ),
    )

    val brandBModeLight: Tokens = Tokens(
        space = TokensSpace(
            base = 4.0f,
        ),
        radius = TokensRadius(
            card = 8.0f,
        ),
        font = TokensFont(
            weightStrong = FontWeightToken(weight = 700, italic = true),
        ),
        type = TokensType(
            body = TypographyToken(fontFamily = listOf("Inter", "Segoe UI"), fontSize = 16.0f, fontWeight = FontWeightToken(weight = 400, italic = false), lineHeight = 1.5f, letterSpacing = null, textCase = null, textDecoration = null),
        ),
        shadow = TokensShadow(
            lift = listOf(ShadowToken(offsetX = 0.0f, offsetY = 2.0f, blur = 4.0f, spread = 0.0f, color = 0x40000000L, inset = false)),
        ),
        opacity = TokensOpacity(
            muted = 0.5f,
        ),
        color = TokensColor(
            bg = 0xFFFFFFFF,
            text = 0xFF111111,
            accent = 0xFFFF3366,
        ),
        library = TokensLibrary(
            colorBrandPrimary = 0xFF3366FF,
        ),
    )

    val brandBModeDark: Tokens = Tokens(
        space = TokensSpace(
            base = 4.0f,
        ),
        radius = TokensRadius(
            card = 8.0f,
        ),
        font = TokensFont(
            weightStrong = FontWeightToken(weight = 700, italic = true),
        ),
        type = TokensType(
            body = TypographyToken(fontFamily = listOf("Inter", "Segoe UI"), fontSize = 16.0f, fontWeight = FontWeightToken(weight = 400, italic = false), lineHeight = 1.5f, letterSpacing = null, textCase = null, textDecoration = null),
        ),
        shadow = TokensShadow(
            lift = listOf(ShadowToken(offsetX = 0.0f, offsetY = 2.0f, blur = 4.0f, spread = 0.0f, color = 0x40000000L, inset = false)),
        ),
        opacity = TokensOpacity(
            muted = 0.5f,
        ),
        color = TokensColor(
            bg = 0xFF111111,
            text = 0xFF111111,
            accent = 0xFFFF3366,
        ),
        library = TokensLibrary(
            colorBrandPrimary = 0xFF3366FF,
        ),
    )

    val default: Tokens = brandAModeLight

    fun select(brand: String, mode: String): Tokens = when {
        brand == "a" && mode == "light" -> brandAModeLight
        brand == "a" && mode == "dark" -> brandAModeDark
        brand == "b" && mode == "light" -> brandBModeLight
        brand == "b" && mode == "dark" -> brandBModeDark
        else -> brandAModeLight
    }
}
