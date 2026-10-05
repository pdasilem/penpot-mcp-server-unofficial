import SwiftUI

struct FontWeightToken {
    let weight: Int
    let italic: Bool
}

struct TypographyToken {
    let fontFamily: [String]?
    let fontSize: CGFloat?
    let fontWeight: FontWeightToken?
    let lineHeight: CGFloat?
    let letterSpacing: CGFloat?
    let textCase: String?
    let textDecoration: String?
}

struct ShadowToken {
    let offsetX: CGFloat
    let offsetY: CGFloat
    let blur: CGFloat
    let spread: CGFloat
    let color: Color
    let inset: Bool
}

struct Tokens {
    let space: TokensSpace
    let radius: TokensRadius
    let font: TokensFont
    let type: TokensType
    let shadow: TokensShadow
    let opacity: TokensOpacity
    let color: TokensColor
    let library: TokensLibrary
}

struct TokensSpace {
    let base: CGFloat
}

struct TokensRadius {
    let card: CGFloat
}

struct TokensFont {
    let weightStrong: FontWeightToken
}

struct TokensType {
    let body: TypographyToken
}

struct TokensShadow {
    let lift: [ShadowToken]
}

struct TokensOpacity {
    let muted: CGFloat
}

struct TokensColor {
    let bg: Color
    let text: Color
    let accent: Color
}

struct TokensLibrary {
    let colorBrandPrimary: Color
}

extension Tokens {
    static let brandAModeLight = Tokens(
        space: TokensSpace(
            base: 4.0
        ),
        radius: TokensRadius(
            card: 8.0
        ),
        font: TokensFont(
            weightStrong: FontWeightToken(weight: 700, italic: true)
        ),
        type: TokensType(
            body: TypographyToken(fontFamily: ["Inter", "Segoe UI"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil)
        ),
        shadow: TokensShadow(
            lift: [ShadowToken(offsetX: 0.0, offsetY: 2.0, blur: 4.0, spread: 0.0, color: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 0.25), inset: false)]
        ),
        opacity: TokensOpacity(
            muted: 0.5
        ),
        color: TokensColor(
            bg: Color(.sRGB, red: 1.0, green: 1.0, blue: 1.0, opacity: 1.0),
            text: Color(.sRGB, red: 0.0667, green: 0.0667, blue: 0.0667, opacity: 1.0),
            accent: Color(.sRGB, red: 0.2, green: 0.4, blue: 1.0, opacity: 1.0)
        ),
        library: TokensLibrary(
            colorBrandPrimary: Color(.sRGB, red: 0.2, green: 0.4, blue: 1.0, opacity: 1.0)
        )
    )

    static let brandAModeDark = Tokens(
        space: TokensSpace(
            base: 4.0
        ),
        radius: TokensRadius(
            card: 8.0
        ),
        font: TokensFont(
            weightStrong: FontWeightToken(weight: 700, italic: true)
        ),
        type: TokensType(
            body: TypographyToken(fontFamily: ["Inter", "Segoe UI"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil)
        ),
        shadow: TokensShadow(
            lift: [ShadowToken(offsetX: 0.0, offsetY: 2.0, blur: 4.0, spread: 0.0, color: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 0.25), inset: false)]
        ),
        opacity: TokensOpacity(
            muted: 0.5
        ),
        color: TokensColor(
            bg: Color(.sRGB, red: 0.0667, green: 0.0667, blue: 0.0667, opacity: 1.0),
            text: Color(.sRGB, red: 0.0667, green: 0.0667, blue: 0.0667, opacity: 1.0),
            accent: Color(.sRGB, red: 0.2, green: 0.4, blue: 1.0, opacity: 1.0)
        ),
        library: TokensLibrary(
            colorBrandPrimary: Color(.sRGB, red: 0.2, green: 0.4, blue: 1.0, opacity: 1.0)
        )
    )

    static let brandBModeLight = Tokens(
        space: TokensSpace(
            base: 4.0
        ),
        radius: TokensRadius(
            card: 8.0
        ),
        font: TokensFont(
            weightStrong: FontWeightToken(weight: 700, italic: true)
        ),
        type: TokensType(
            body: TypographyToken(fontFamily: ["Inter", "Segoe UI"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil)
        ),
        shadow: TokensShadow(
            lift: [ShadowToken(offsetX: 0.0, offsetY: 2.0, blur: 4.0, spread: 0.0, color: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 0.25), inset: false)]
        ),
        opacity: TokensOpacity(
            muted: 0.5
        ),
        color: TokensColor(
            bg: Color(.sRGB, red: 1.0, green: 1.0, blue: 1.0, opacity: 1.0),
            text: Color(.sRGB, red: 0.0667, green: 0.0667, blue: 0.0667, opacity: 1.0),
            accent: Color(.sRGB, red: 1.0, green: 0.2, blue: 0.4, opacity: 1.0)
        ),
        library: TokensLibrary(
            colorBrandPrimary: Color(.sRGB, red: 0.2, green: 0.4, blue: 1.0, opacity: 1.0)
        )
    )

    static let brandBModeDark = Tokens(
        space: TokensSpace(
            base: 4.0
        ),
        radius: TokensRadius(
            card: 8.0
        ),
        font: TokensFont(
            weightStrong: FontWeightToken(weight: 700, italic: true)
        ),
        type: TokensType(
            body: TypographyToken(fontFamily: ["Inter", "Segoe UI"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil)
        ),
        shadow: TokensShadow(
            lift: [ShadowToken(offsetX: 0.0, offsetY: 2.0, blur: 4.0, spread: 0.0, color: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 0.25), inset: false)]
        ),
        opacity: TokensOpacity(
            muted: 0.5
        ),
        color: TokensColor(
            bg: Color(.sRGB, red: 0.0667, green: 0.0667, blue: 0.0667, opacity: 1.0),
            text: Color(.sRGB, red: 0.0667, green: 0.0667, blue: 0.0667, opacity: 1.0),
            accent: Color(.sRGB, red: 1.0, green: 0.2, blue: 0.4, opacity: 1.0)
        ),
        library: TokensLibrary(
            colorBrandPrimary: Color(.sRGB, red: 0.2, green: 0.4, blue: 1.0, opacity: 1.0)
        )
    )

    static let standard = brandAModeLight
}

struct TokensKey: EnvironmentKey {
    static let defaultValue = Tokens.standard
}

extension EnvironmentValues {
    var tokens: Tokens {
        get { self[TokensKey.self] }
        set { self[TokensKey.self] = newValue }
    }
}
