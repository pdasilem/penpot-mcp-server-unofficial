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
    let surfaceVariant: Color
    let onBandMuted: Color
    let wordExtra: Color
    let wordMissed: Color
    let bandVariant: Color
    let onAccent: Color
    let error: Color
    let outline: Color
    let band: Color
    let wordMatched: Color
    let surface: Color
    let textSecondary: Color
    let wordPartial: Color
    let accent: Color
    let textPrimary: Color
    let background: Color
    let onBand: Color
    let bandOutline: Color
    let outlineVariant: Color
    let page: CGFloat
    let gap: CGFloat
    let corner: CGFloat
    let shadow: [ShadowToken]
    let measure: CGFloat
    let type: TokensType
    let bodySmallRegular: TypographyToken
    let space: TokensSpace
    let bodySmallLooseRegular: TypographyToken
    let size: TokensSize
    let displayMedium: TypographyToken
    let bodySmallTrackedRegular: TypographyToken
    let bodyLargeMedium: TypographyToken
    let bodyLargeSemiBold: TypographyToken
    let displaySemiBold: TypographyToken
    let bodyLargeRegular: TypographyToken
    let regular: FontWeightToken
    let share: TokensShare
    let strokeWide: CGFloat
    let spaceTiny: CGFloat
    let spaceSmall: CGFloat
    let mono: [String]
    let glyphSmall: CGFloat
    let displayRegular: TypographyToken
    let semiBold: FontWeightToken
    let tracking: CGFloat
    let shadowEdgeUp: [ShadowToken]
    let spaceMicro: CGFloat
    let bodySemiBold: TypographyToken
    let titleRegular: TypographyToken
    let bodySmallLooseSemiBold: TypographyToken
    let shadowLift: [ShadowToken]
    let scrim: Color
    let controlEdge: CGFloat
    let glyph: CGFloat
    let wordState: CGFloat
    let stroke: CGFloat
    let text: CGFloat
    let heroSemiBold: TypographyToken
    let glyphHuge: CGFloat
    let labelTrackedRegular: TypographyToken
    let labelLooseSemiBold: TypographyToken
    let bodySmallSemiBold: TypographyToken
    let titleSemiBold: TypographyToken
    let posterSemiBold: TypographyToken
    let veil: CGFloat
    let bodyMedium: TypographyToken
    let labelLooseRegular: TypographyToken
    let headlineRegular: TypographyToken
    let iconLayer: CGFloat
    let family: [String]
    let bodyRegular: TypographyToken
    let headlineSemiBold: TypographyToken
    let radius: TokensRadius
    let displayLargeSemiBold: TypographyToken
    let spaceMedium: CGFloat
    let spaceBig: CGFloat
    let giantSemiBold: TypographyToken
    let highlight: CGFloat
    let labelRegular: TypographyToken
    let titleMedium: TypographyToken
    let glyphBig: CGFloat
    let strokeBold: CGFloat
    let labelSemiBold: TypographyToken
    let bodySmallMedium: TypographyToken
    let connector: CGFloat
    let headlineMedium: TypographyToken
    let ghost: CGFloat
    let radiusRound: CGFloat
    let spaceSuperBig: CGFloat
    let medium: FontWeightToken
    let labelMedium: TypographyToken
    let link: TokensLink
    let library: TokensLibrary
}

struct TokensType {
    let _96: CGFloat
    let _200: CGFloat
    let _18: CGFloat
    let _24: CGFloat
    let _22: CGFloat
    let _14: CGFloat
    let _12: CGFloat
    let _16: CGFloat
    let _32: CGFloat
    let _64: CGFloat
    let _40: CGFloat
}

struct TokensSpace {
    let _9: CGFloat
    let _11: CGFloat
    let _40: CGFloat
    let _4: CGFloat
    let _5: CGFloat
    let _20: CGFloat
    let _15: CGFloat
    let _8: CGFloat
    let _13: CGFloat
    let _2: CGFloat
    let _12: CGFloat
}

struct TokensSize {
    let _72: CGFloat
    let _4: CGFloat
    let _150: CGFloat
    let _45: CGFloat
    let _20: CGFloat
    let _56: CGFloat
    let _48: CGFloat
    let _600: CGFloat
    let _12: CGFloat
    let _110: CGFloat
    let _64: CGFloat
    let _88: CGFloat
    let _70: CGFloat
    let _6: CGFloat
    let _60: CGFloat
    let _8: CGFloat
    let _160: CGFloat
    let _96: CGFloat
    let _80: CGFloat
    let _27: CGFloat
    let _42: CGFloat
    let _40: CGFloat
    let _16: CGFloat
    let _32: CGFloat
    let _200: CGFloat
    let _2: CGFloat
    let _340: CGFloat
    let _1: CGFloat
    let _24: CGFloat
    let _34: CGFloat
    let _28: CGFloat
}

struct TokensShare {
    let _100: CGFloat
    let _75: CGFloat
    let _66: CGFloat
    let _25: CGFloat
    let _33: CGFloat
    let _50: CGFloat
}

struct TokensRadius {
    let _12: CGFloat
    let full: CGFloat
    let _4: CGFloat
}

struct TokensLink {
    let colour: Color
}

struct TokensLibrary {
    let colorLink: Color
}

extension Tokens {
    static let schemeStudioMonitorLight = Tokens(
        surfaceVariant: Color(.sRGB, red: 0.8941, green: 0.9059, blue: 0.9176, opacity: 1.0),
        onBandMuted: Color(.sRGB, red: 0.4902, green: 0.5216, blue: 0.5412, opacity: 1.0),
        wordExtra: Color(.sRGB, red: 0.9176, green: 0.0, blue: 0.0627, opacity: 1.0),
        wordMissed: Color(.sRGB, red: 0.451, green: 0.451, blue: 0.451, opacity: 1.0),
        bandVariant: Color(.sRGB, red: 0.098, green: 0.1098, blue: 0.1216, opacity: 1.0),
        onAccent: Color(.sRGB, red: 1.0, green: 1.0, blue: 1.0, opacity: 1.0),
        error: Color(.sRGB, red: 0.6392, green: 0.2275, blue: 0.149, opacity: 1.0),
        outline: Color(.sRGB, red: 0.5412, green: 0.5569, blue: 0.5686, opacity: 1.0),
        band: Color(.sRGB, red: 0.0549, green: 0.0627, blue: 0.0706, opacity: 1.0),
        wordMatched: Color(.sRGB, red: 0.0, green: 0.5255, blue: 0.2353, opacity: 1.0),
        surface: Color(.sRGB, red: 1.0, green: 1.0, blue: 1.0, opacity: 1.0),
        textSecondary: Color(.sRGB, red: 0.4196, green: 0.4471, blue: 0.4627, opacity: 1.0),
        wordPartial: Color(.sRGB, red: 0.6196, green: 0.4157, blue: 0.0, opacity: 1.0),
        accent: Color(.sRGB, red: 0.6039, green: 0.3961, blue: 0.0588, opacity: 1.0),
        textPrimary: Color(.sRGB, red: 0.102, green: 0.1137, blue: 0.1255, opacity: 1.0),
        background: Color(.sRGB, red: 0.9569, green: 0.9608, blue: 0.9647, opacity: 1.0),
        onBand: Color(.sRGB, red: 0.9098, green: 0.9176, blue: 0.9255, opacity: 1.0),
        bandOutline: Color(.sRGB, red: 0.2902, green: 0.3137, blue: 0.3294, opacity: 1.0),
        outlineVariant: Color(.sRGB, red: 0.7882, green: 0.8039, blue: 0.8157, opacity: 1.0),
        page: 1120.0,
        gap: 24.0,
        corner: 12.0,
        shadow: [ShadowToken(offsetX: 0.0, offsetY: 1.0, blur: 2.0, spread: 0.0, color: Color(.sRGB, red: 0.0784, green: 0.0902, blue: 0.102, opacity: 0.05), inset: false), ShadowToken(offsetX: 0.0, offsetY: 12.0, blur: 24.0, spread: 0.0, color: Color(.sRGB, red: 0.0784, green: 0.0902, blue: 0.102, opacity: 0.12), inset: false)],
        measure: 66.0,
        type: TokensType(
            _96: 96.0,
            _200: 200.0,
            _18: 18.0,
            _24: 24.0,
            _22: 22.0,
            _14: 14.0,
            _12: 12.0,
            _16: 16.0,
            _32: 32.0,
            _64: 64.0,
            _40: 40.0
        ),
        bodySmallRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.4286, letterSpacing: nil, textCase: nil, textDecoration: nil),
        space: TokensSpace(
            _9: 9.0,
            _11: 11.0,
            _40: 40.0,
            _4: 4.0,
            _5: 5.0,
            _20: 20.0,
            _15: 15.0,
            _8: 8.0,
            _13: 13.0,
            _2: 2.0,
            _12: 12.0
        ),
        bodySmallLooseRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.7143, letterSpacing: nil, textCase: nil, textDecoration: nil),
        size: TokensSize(
            _72: 72.0,
            _4: 4.0,
            _150: 150.0,
            _45: 45.0,
            _20: 20.0,
            _56: 56.0,
            _48: 48.0,
            _600: 600.0,
            _12: 12.0,
            _110: 110.0,
            _64: 64.0,
            _88: 88.0,
            _70: 70.0,
            _6: 6.0,
            _60: 60.0,
            _8: 8.0,
            _160: 160.0,
            _96: 96.0,
            _80: 80.0,
            _27: 27.0,
            _42: 42.0,
            _40: 40.0,
            _16: 16.0,
            _32: 32.0,
            _200: 200.0,
            _2: 2.0,
            _340: 340.0,
            _1: 1.0,
            _24: 24.0,
            _34: 34.0,
            _28: 28.0
        ),
        displayMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 32.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.25, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallTrackedRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.4286, letterSpacing: 1.1, textCase: nil, textDecoration: nil),
        bodyLargeMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 18.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodyLargeSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 18.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        displaySemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 32.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.25, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodyLargeRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 18.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        regular: FontWeightToken(weight: 400, italic: false),
        share: TokensShare(
            _100: 1.0,
            _75: 0.75,
            _66: 0.66,
            _25: 0.25,
            _33: 0.33,
            _50: 0.5
        ),
        strokeWide: 2.0,
        spaceTiny: 4.0,
        spaceSmall: 8.0,
        mono: ["IBM Plex Mono"],
        glyphSmall: 20.0,
        displayRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 32.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.25, letterSpacing: nil, textCase: nil, textDecoration: nil),
        semiBold: FontWeightToken(weight: 600, italic: false),
        tracking: 1.1,
        shadowEdgeUp: [ShadowToken(offsetX: 0.0, offsetY: -1.0, blur: 4.0, spread: 0.0, color: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 0.12), inset: false)],
        spaceMicro: 2.0,
        bodySemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil),
        titleRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 22.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.2727, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallLooseSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.7143, letterSpacing: nil, textCase: nil, textDecoration: nil),
        shadowLift: [ShadowToken(offsetX: 0.0, offsetY: 4.0, blur: 8.0, spread: 0.0, color: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 0.12), inset: false)],
        scrim: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 1.0),
        controlEdge: 3.0,
        glyph: 24.0,
        wordState: 3.0,
        stroke: 1.0,
        text: 4.5,
        heroSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 96.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.1, letterSpacing: nil, textCase: nil, textDecoration: nil),
        glyphHuge: 48.0,
        labelTrackedRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: 1.1, textCase: nil, textDecoration: nil),
        labelLooseSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 2.0, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.4286, letterSpacing: nil, textCase: nil, textDecoration: nil),
        titleSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 22.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.2727, letterSpacing: nil, textCase: nil, textDecoration: nil),
        posterSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 64.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.1, letterSpacing: nil, textCase: nil, textDecoration: nil),
        veil: 0.35,
        bodyMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil),
        labelLooseRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 2.0, letterSpacing: nil, textCase: nil, textDecoration: nil),
        headlineRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 24.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        iconLayer: 3.0,
        family: ["IBM Plex Sans"],
        bodyRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil),
        headlineSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 24.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        radius: TokensRadius(
            _12: 12.0,
            full: 999.0,
            _4: 4.0
        ),
        displayLargeSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 40.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.2, letterSpacing: nil, textCase: nil, textDecoration: nil),
        spaceMedium: 12.0,
        spaceBig: 20.0,
        giantSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 200.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.0, letterSpacing: nil, textCase: nil, textDecoration: nil),
        highlight: 1.5,
        labelRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        titleMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 22.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.2727, letterSpacing: nil, textCase: nil, textDecoration: nil),
        glyphBig: 32.0,
        strokeBold: 4.0,
        labelSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.4286, letterSpacing: nil, textCase: nil, textDecoration: nil),
        connector: 0.55,
        headlineMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 24.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        ghost: 0.18,
        radiusRound: 999.0,
        spaceSuperBig: 40.0,
        medium: FontWeightToken(weight: 500, italic: false),
        labelMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        link: TokensLink(
            colour: Color(.sRGB, red: 0.6039, green: 0.3961, blue: 0.0588, opacity: 1.0)
        ),
        library: TokensLibrary(
            colorLink: Color(.sRGB, red: 0.6039, green: 0.3961, blue: 0.0588, opacity: 1.0)
        )
    )

    static let schemeStudioMonitorDark = Tokens(
        surfaceVariant: Color(.sRGB, red: 0.1333, green: 0.149, blue: 0.1647, opacity: 1.0),
        onBandMuted: Color(.sRGB, red: 0.4902, green: 0.5216, blue: 0.5412, opacity: 1.0),
        wordExtra: Color(.sRGB, red: 0.9176, green: 0.0, blue: 0.0627, opacity: 1.0),
        wordMissed: Color(.sRGB, red: 0.451, green: 0.451, blue: 0.451, opacity: 1.0),
        bandVariant: Color(.sRGB, red: 0.098, green: 0.1098, blue: 0.1216, opacity: 1.0),
        onAccent: Color(.sRGB, red: 0.0784, green: 0.0902, blue: 0.102, opacity: 1.0),
        error: Color(.sRGB, red: 0.8902, green: 0.4392, blue: 0.3569, opacity: 1.0),
        outline: Color(.sRGB, red: 0.3882, green: 0.4118, blue: 0.4235, opacity: 1.0),
        band: Color(.sRGB, red: 0.1333, green: 0.149, blue: 0.1647, opacity: 1.0),
        wordMatched: Color(.sRGB, red: 0.0, green: 0.5255, blue: 0.2353, opacity: 1.0),
        surface: Color(.sRGB, red: 0.098, green: 0.1098, blue: 0.1216, opacity: 1.0),
        textSecondary: Color(.sRGB, red: 0.6275, green: 0.6549, blue: 0.6784, opacity: 1.0),
        wordPartial: Color(.sRGB, red: 0.6196, green: 0.4157, blue: 0.0, opacity: 1.0),
        accent: Color(.sRGB, red: 0.949, green: 0.6627, blue: 0.2314, opacity: 1.0),
        textPrimary: Color(.sRGB, red: 0.9098, green: 0.9176, blue: 0.9255, opacity: 1.0),
        background: Color(.sRGB, red: 0.0549, green: 0.0627, blue: 0.0706, opacity: 1.0),
        onBand: Color(.sRGB, red: 0.9098, green: 0.9176, blue: 0.9255, opacity: 1.0),
        bandOutline: Color(.sRGB, red: 0.2902, green: 0.3137, blue: 0.3294, opacity: 1.0),
        outlineVariant: Color(.sRGB, red: 0.2902, green: 0.3137, blue: 0.3294, opacity: 1.0),
        page: 1120.0,
        gap: 24.0,
        corner: 12.0,
        shadow: [ShadowToken(offsetX: 0.0, offsetY: 1.0, blur: 2.0, spread: 0.0, color: Color(.sRGB, red: 0.0784, green: 0.0902, blue: 0.102, opacity: 0.05), inset: false), ShadowToken(offsetX: 0.0, offsetY: 12.0, blur: 24.0, spread: 0.0, color: Color(.sRGB, red: 0.0784, green: 0.0902, blue: 0.102, opacity: 0.12), inset: false)],
        measure: 66.0,
        type: TokensType(
            _96: 96.0,
            _200: 200.0,
            _18: 18.0,
            _24: 24.0,
            _22: 22.0,
            _14: 14.0,
            _12: 12.0,
            _16: 16.0,
            _32: 32.0,
            _64: 64.0,
            _40: 40.0
        ),
        bodySmallRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.4286, letterSpacing: nil, textCase: nil, textDecoration: nil),
        space: TokensSpace(
            _9: 9.0,
            _11: 11.0,
            _40: 40.0,
            _4: 4.0,
            _5: 5.0,
            _20: 20.0,
            _15: 15.0,
            _8: 8.0,
            _13: 13.0,
            _2: 2.0,
            _12: 12.0
        ),
        bodySmallLooseRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.7143, letterSpacing: nil, textCase: nil, textDecoration: nil),
        size: TokensSize(
            _72: 72.0,
            _4: 4.0,
            _150: 150.0,
            _45: 45.0,
            _20: 20.0,
            _56: 56.0,
            _48: 48.0,
            _600: 600.0,
            _12: 12.0,
            _110: 110.0,
            _64: 64.0,
            _88: 88.0,
            _70: 70.0,
            _6: 6.0,
            _60: 60.0,
            _8: 8.0,
            _160: 160.0,
            _96: 96.0,
            _80: 80.0,
            _27: 27.0,
            _42: 42.0,
            _40: 40.0,
            _16: 16.0,
            _32: 32.0,
            _200: 200.0,
            _2: 2.0,
            _340: 340.0,
            _1: 1.0,
            _24: 24.0,
            _34: 34.0,
            _28: 28.0
        ),
        displayMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 32.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.25, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallTrackedRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.4286, letterSpacing: 1.1, textCase: nil, textDecoration: nil),
        bodyLargeMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 18.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodyLargeSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 18.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        displaySemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 32.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.25, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodyLargeRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 18.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        regular: FontWeightToken(weight: 400, italic: false),
        share: TokensShare(
            _100: 1.0,
            _75: 0.75,
            _66: 0.66,
            _25: 0.25,
            _33: 0.33,
            _50: 0.5
        ),
        strokeWide: 2.0,
        spaceTiny: 4.0,
        spaceSmall: 8.0,
        mono: ["IBM Plex Mono"],
        glyphSmall: 20.0,
        displayRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 32.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.25, letterSpacing: nil, textCase: nil, textDecoration: nil),
        semiBold: FontWeightToken(weight: 600, italic: false),
        tracking: 1.1,
        shadowEdgeUp: [ShadowToken(offsetX: 0.0, offsetY: -1.0, blur: 4.0, spread: 0.0, color: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 0.12), inset: false)],
        spaceMicro: 2.0,
        bodySemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil),
        titleRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 22.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.2727, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallLooseSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.7143, letterSpacing: nil, textCase: nil, textDecoration: nil),
        shadowLift: [ShadowToken(offsetX: 0.0, offsetY: 4.0, blur: 8.0, spread: 0.0, color: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 0.12), inset: false)],
        scrim: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 1.0),
        controlEdge: 3.0,
        glyph: 24.0,
        wordState: 3.0,
        stroke: 1.0,
        text: 4.5,
        heroSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 96.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.1, letterSpacing: nil, textCase: nil, textDecoration: nil),
        glyphHuge: 48.0,
        labelTrackedRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: 1.1, textCase: nil, textDecoration: nil),
        labelLooseSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 2.0, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.4286, letterSpacing: nil, textCase: nil, textDecoration: nil),
        titleSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 22.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.2727, letterSpacing: nil, textCase: nil, textDecoration: nil),
        posterSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 64.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.1, letterSpacing: nil, textCase: nil, textDecoration: nil),
        veil: 0.35,
        bodyMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil),
        labelLooseRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 2.0, letterSpacing: nil, textCase: nil, textDecoration: nil),
        headlineRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 24.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        iconLayer: 3.0,
        family: ["IBM Plex Sans"],
        bodyRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil),
        headlineSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 24.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        radius: TokensRadius(
            _12: 12.0,
            full: 999.0,
            _4: 4.0
        ),
        displayLargeSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 40.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.2, letterSpacing: nil, textCase: nil, textDecoration: nil),
        spaceMedium: 12.0,
        spaceBig: 20.0,
        giantSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 200.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.0, letterSpacing: nil, textCase: nil, textDecoration: nil),
        highlight: 1.5,
        labelRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        titleMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 22.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.2727, letterSpacing: nil, textCase: nil, textDecoration: nil),
        glyphBig: 32.0,
        strokeBold: 4.0,
        labelSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.4286, letterSpacing: nil, textCase: nil, textDecoration: nil),
        connector: 0.55,
        headlineMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 24.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        ghost: 0.18,
        radiusRound: 999.0,
        spaceSuperBig: 40.0,
        medium: FontWeightToken(weight: 500, italic: false),
        labelMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        link: TokensLink(
            colour: Color(.sRGB, red: 0.949, green: 0.6627, blue: 0.2314, opacity: 1.0)
        ),
        library: TokensLibrary(
            colorLink: Color(.sRGB, red: 0.6039, green: 0.3961, blue: 0.0588, opacity: 1.0)
        )
    )

    static let schemePaperAndInkLight = Tokens(
        surfaceVariant: Color(.sRGB, red: 0.9216, green: 0.8941, blue: 0.8471, opacity: 1.0),
        onBandMuted: Color(.sRGB, red: 0.5765, green: 0.5216, blue: 0.4627, opacity: 1.0),
        wordExtra: Color(.sRGB, red: 0.651, green: 0.2627, blue: 0.1686, opacity: 1.0),
        wordMissed: Color(.sRGB, red: 0.4353, green: 0.4588, blue: 0.4863, opacity: 1.0),
        bandVariant: Color(.sRGB, red: 0.1255, green: 0.1059, blue: 0.0863, opacity: 1.0),
        onAccent: Color(.sRGB, red: 1.0, green: 0.9922, blue: 0.9765, opacity: 1.0),
        error: Color(.sRGB, red: 0.651, green: 0.2627, blue: 0.1686, opacity: 1.0),
        outline: Color(.sRGB, red: 0.5765, green: 0.5451, blue: 0.498, opacity: 1.0),
        band: Color(.sRGB, red: 0.0902, green: 0.0745, blue: 0.0588, opacity: 1.0),
        wordMatched: Color(.sRGB, red: 0.1843, green: 0.4196, blue: 0.2667, opacity: 1.0),
        surface: Color(.sRGB, red: 1.0, green: 0.9922, blue: 0.9765, opacity: 1.0),
        textSecondary: Color(.sRGB, red: 0.4196, green: 0.3725, blue: 0.3176, opacity: 1.0),
        wordPartial: Color(.sRGB, red: 0.702, green: 0.4627, blue: 0.098, opacity: 1.0),
        accent: Color(.sRGB, red: 0.1804, green: 0.2941, blue: 0.4784, opacity: 1.0),
        textPrimary: Color(.sRGB, red: 0.1333, green: 0.1098, blue: 0.0863, opacity: 1.0),
        background: Color(.sRGB, red: 0.9686, green: 0.9529, blue: 0.9255, opacity: 1.0),
        onBand: Color(.sRGB, red: 0.9529, green: 0.9216, blue: 0.8824, opacity: 1.0),
        bandOutline: Color(.sRGB, red: 0.3529, green: 0.3059, blue: 0.2549, opacity: 1.0),
        outlineVariant: Color(.sRGB, red: 0.8392, green: 0.8, blue: 0.7373, opacity: 1.0),
        page: 1120.0,
        gap: 24.0,
        corner: 12.0,
        shadow: [ShadowToken(offsetX: 0.0, offsetY: 1.0, blur: 2.0, spread: 0.0, color: Color(.sRGB, red: 0.0784, green: 0.0902, blue: 0.102, opacity: 0.05), inset: false), ShadowToken(offsetX: 0.0, offsetY: 12.0, blur: 24.0, spread: 0.0, color: Color(.sRGB, red: 0.0784, green: 0.0902, blue: 0.102, opacity: 0.12), inset: false)],
        measure: 66.0,
        type: TokensType(
            _96: 96.0,
            _200: 200.0,
            _18: 18.0,
            _24: 24.0,
            _22: 22.0,
            _14: 14.0,
            _12: 12.0,
            _16: 16.0,
            _32: 32.0,
            _64: 64.0,
            _40: 40.0
        ),
        bodySmallRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.4286, letterSpacing: nil, textCase: nil, textDecoration: nil),
        space: TokensSpace(
            _9: 9.0,
            _11: 11.0,
            _40: 40.0,
            _4: 4.0,
            _5: 5.0,
            _20: 20.0,
            _15: 15.0,
            _8: 8.0,
            _13: 13.0,
            _2: 2.0,
            _12: 12.0
        ),
        bodySmallLooseRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.7143, letterSpacing: nil, textCase: nil, textDecoration: nil),
        size: TokensSize(
            _72: 72.0,
            _4: 4.0,
            _150: 150.0,
            _45: 45.0,
            _20: 20.0,
            _56: 56.0,
            _48: 48.0,
            _600: 600.0,
            _12: 12.0,
            _110: 110.0,
            _64: 64.0,
            _88: 88.0,
            _70: 70.0,
            _6: 6.0,
            _60: 60.0,
            _8: 8.0,
            _160: 160.0,
            _96: 96.0,
            _80: 80.0,
            _27: 27.0,
            _42: 42.0,
            _40: 40.0,
            _16: 16.0,
            _32: 32.0,
            _200: 200.0,
            _2: 2.0,
            _340: 340.0,
            _1: 1.0,
            _24: 24.0,
            _34: 34.0,
            _28: 28.0
        ),
        displayMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 32.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.25, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallTrackedRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.4286, letterSpacing: 1.1, textCase: nil, textDecoration: nil),
        bodyLargeMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 18.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodyLargeSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 18.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        displaySemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 32.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.25, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodyLargeRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 18.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        regular: FontWeightToken(weight: 400, italic: false),
        share: TokensShare(
            _100: 1.0,
            _75: 0.75,
            _66: 0.66,
            _25: 0.25,
            _33: 0.33,
            _50: 0.5
        ),
        strokeWide: 2.0,
        spaceTiny: 4.0,
        spaceSmall: 8.0,
        mono: ["IBM Plex Mono"],
        glyphSmall: 20.0,
        displayRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 32.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.25, letterSpacing: nil, textCase: nil, textDecoration: nil),
        semiBold: FontWeightToken(weight: 600, italic: false),
        tracking: 1.1,
        shadowEdgeUp: [ShadowToken(offsetX: 0.0, offsetY: -1.0, blur: 4.0, spread: 0.0, color: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 0.12), inset: false)],
        spaceMicro: 2.0,
        bodySemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil),
        titleRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 22.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.2727, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallLooseSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.7143, letterSpacing: nil, textCase: nil, textDecoration: nil),
        shadowLift: [ShadowToken(offsetX: 0.0, offsetY: 4.0, blur: 8.0, spread: 0.0, color: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 0.12), inset: false)],
        scrim: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 1.0),
        controlEdge: 3.0,
        glyph: 24.0,
        wordState: 3.0,
        stroke: 1.0,
        text: 4.5,
        heroSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 96.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.1, letterSpacing: nil, textCase: nil, textDecoration: nil),
        glyphHuge: 48.0,
        labelTrackedRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: 1.1, textCase: nil, textDecoration: nil),
        labelLooseSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 2.0, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.4286, letterSpacing: nil, textCase: nil, textDecoration: nil),
        titleSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 22.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.2727, letterSpacing: nil, textCase: nil, textDecoration: nil),
        posterSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 64.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.1, letterSpacing: nil, textCase: nil, textDecoration: nil),
        veil: 0.35,
        bodyMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil),
        labelLooseRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 2.0, letterSpacing: nil, textCase: nil, textDecoration: nil),
        headlineRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 24.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        iconLayer: 3.0,
        family: ["IBM Plex Sans"],
        bodyRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil),
        headlineSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 24.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        radius: TokensRadius(
            _12: 12.0,
            full: 999.0,
            _4: 4.0
        ),
        displayLargeSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 40.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.2, letterSpacing: nil, textCase: nil, textDecoration: nil),
        spaceMedium: 12.0,
        spaceBig: 20.0,
        giantSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 200.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.0, letterSpacing: nil, textCase: nil, textDecoration: nil),
        highlight: 1.5,
        labelRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        titleMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 22.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.2727, letterSpacing: nil, textCase: nil, textDecoration: nil),
        glyphBig: 32.0,
        strokeBold: 4.0,
        labelSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.4286, letterSpacing: nil, textCase: nil, textDecoration: nil),
        connector: 0.55,
        headlineMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 24.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        ghost: 0.18,
        radiusRound: 999.0,
        spaceSuperBig: 40.0,
        medium: FontWeightToken(weight: 500, italic: false),
        labelMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        link: TokensLink(
            colour: Color(.sRGB, red: 0.1804, green: 0.2941, blue: 0.4784, opacity: 1.0)
        ),
        library: TokensLibrary(
            colorLink: Color(.sRGB, red: 0.6039, green: 0.3961, blue: 0.0588, opacity: 1.0)
        )
    )

    static let schemePaperAndInkDark = Tokens(
        surfaceVariant: Color(.sRGB, red: 0.1725, green: 0.1451, blue: 0.1176, opacity: 1.0),
        onBandMuted: Color(.sRGB, red: 0.5765, green: 0.5216, blue: 0.4627, opacity: 1.0),
        wordExtra: Color(.sRGB, red: 0.8784, green: 0.5569, blue: 0.4627, opacity: 1.0),
        wordMissed: Color(.sRGB, red: 0.502, green: 0.5255, blue: 0.5529, opacity: 1.0),
        bandVariant: Color(.sRGB, red: 0.1255, green: 0.1059, blue: 0.0863, opacity: 1.0),
        onAccent: Color(.sRGB, red: 0.0863, green: 0.1059, blue: 0.1412, opacity: 1.0),
        error: Color(.sRGB, red: 0.8784, green: 0.5569, blue: 0.4627, opacity: 1.0),
        outline: Color(.sRGB, red: 0.4431, green: 0.4, blue: 0.349, opacity: 1.0),
        band: Color(.sRGB, red: 0.1725, green: 0.1451, blue: 0.1176, opacity: 1.0),
        wordMatched: Color(.sRGB, red: 0.498, green: 0.7804, blue: 0.6039, opacity: 1.0),
        surface: Color(.sRGB, red: 0.1255, green: 0.1059, blue: 0.0863, opacity: 1.0),
        textSecondary: Color(.sRGB, red: 0.7255, green: 0.6745, blue: 0.6118, opacity: 1.0),
        wordPartial: Color(.sRGB, red: 0.949, green: 0.6627, blue: 0.2314, opacity: 1.0),
        accent: Color(.sRGB, red: 0.6235, green: 0.7451, blue: 0.9098, opacity: 1.0),
        textPrimary: Color(.sRGB, red: 0.9529, green: 0.9216, blue: 0.8824, opacity: 1.0),
        background: Color(.sRGB, red: 0.0902, green: 0.0745, blue: 0.0588, opacity: 1.0),
        onBand: Color(.sRGB, red: 0.9529, green: 0.9216, blue: 0.8824, opacity: 1.0),
        bandOutline: Color(.sRGB, red: 0.3529, green: 0.3059, blue: 0.2549, opacity: 1.0),
        outlineVariant: Color(.sRGB, red: 0.3529, green: 0.3059, blue: 0.2549, opacity: 1.0),
        page: 1120.0,
        gap: 24.0,
        corner: 12.0,
        shadow: [ShadowToken(offsetX: 0.0, offsetY: 1.0, blur: 2.0, spread: 0.0, color: Color(.sRGB, red: 0.0784, green: 0.0902, blue: 0.102, opacity: 0.05), inset: false), ShadowToken(offsetX: 0.0, offsetY: 12.0, blur: 24.0, spread: 0.0, color: Color(.sRGB, red: 0.0784, green: 0.0902, blue: 0.102, opacity: 0.12), inset: false)],
        measure: 66.0,
        type: TokensType(
            _96: 96.0,
            _200: 200.0,
            _18: 18.0,
            _24: 24.0,
            _22: 22.0,
            _14: 14.0,
            _12: 12.0,
            _16: 16.0,
            _32: 32.0,
            _64: 64.0,
            _40: 40.0
        ),
        bodySmallRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.4286, letterSpacing: nil, textCase: nil, textDecoration: nil),
        space: TokensSpace(
            _9: 9.0,
            _11: 11.0,
            _40: 40.0,
            _4: 4.0,
            _5: 5.0,
            _20: 20.0,
            _15: 15.0,
            _8: 8.0,
            _13: 13.0,
            _2: 2.0,
            _12: 12.0
        ),
        bodySmallLooseRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.7143, letterSpacing: nil, textCase: nil, textDecoration: nil),
        size: TokensSize(
            _72: 72.0,
            _4: 4.0,
            _150: 150.0,
            _45: 45.0,
            _20: 20.0,
            _56: 56.0,
            _48: 48.0,
            _600: 600.0,
            _12: 12.0,
            _110: 110.0,
            _64: 64.0,
            _88: 88.0,
            _70: 70.0,
            _6: 6.0,
            _60: 60.0,
            _8: 8.0,
            _160: 160.0,
            _96: 96.0,
            _80: 80.0,
            _27: 27.0,
            _42: 42.0,
            _40: 40.0,
            _16: 16.0,
            _32: 32.0,
            _200: 200.0,
            _2: 2.0,
            _340: 340.0,
            _1: 1.0,
            _24: 24.0,
            _34: 34.0,
            _28: 28.0
        ),
        displayMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 32.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.25, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallTrackedRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.4286, letterSpacing: 1.1, textCase: nil, textDecoration: nil),
        bodyLargeMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 18.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodyLargeSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 18.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        displaySemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 32.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.25, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodyLargeRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 18.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        regular: FontWeightToken(weight: 400, italic: false),
        share: TokensShare(
            _100: 1.0,
            _75: 0.75,
            _66: 0.66,
            _25: 0.25,
            _33: 0.33,
            _50: 0.5
        ),
        strokeWide: 2.0,
        spaceTiny: 4.0,
        spaceSmall: 8.0,
        mono: ["IBM Plex Mono"],
        glyphSmall: 20.0,
        displayRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 32.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.25, letterSpacing: nil, textCase: nil, textDecoration: nil),
        semiBold: FontWeightToken(weight: 600, italic: false),
        tracking: 1.1,
        shadowEdgeUp: [ShadowToken(offsetX: 0.0, offsetY: -1.0, blur: 4.0, spread: 0.0, color: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 0.12), inset: false)],
        spaceMicro: 2.0,
        bodySemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil),
        titleRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 22.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.2727, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallLooseSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.7143, letterSpacing: nil, textCase: nil, textDecoration: nil),
        shadowLift: [ShadowToken(offsetX: 0.0, offsetY: 4.0, blur: 8.0, spread: 0.0, color: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 0.12), inset: false)],
        scrim: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 1.0),
        controlEdge: 3.0,
        glyph: 24.0,
        wordState: 3.0,
        stroke: 1.0,
        text: 4.5,
        heroSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 96.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.1, letterSpacing: nil, textCase: nil, textDecoration: nil),
        glyphHuge: 48.0,
        labelTrackedRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: 1.1, textCase: nil, textDecoration: nil),
        labelLooseSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 2.0, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.4286, letterSpacing: nil, textCase: nil, textDecoration: nil),
        titleSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 22.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.2727, letterSpacing: nil, textCase: nil, textDecoration: nil),
        posterSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 64.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.1, letterSpacing: nil, textCase: nil, textDecoration: nil),
        veil: 0.35,
        bodyMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil),
        labelLooseRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 2.0, letterSpacing: nil, textCase: nil, textDecoration: nil),
        headlineRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 24.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        iconLayer: 3.0,
        family: ["IBM Plex Sans"],
        bodyRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil),
        headlineSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 24.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        radius: TokensRadius(
            _12: 12.0,
            full: 999.0,
            _4: 4.0
        ),
        displayLargeSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 40.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.2, letterSpacing: nil, textCase: nil, textDecoration: nil),
        spaceMedium: 12.0,
        spaceBig: 20.0,
        giantSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 200.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.0, letterSpacing: nil, textCase: nil, textDecoration: nil),
        highlight: 1.5,
        labelRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        titleMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 22.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.2727, letterSpacing: nil, textCase: nil, textDecoration: nil),
        glyphBig: 32.0,
        strokeBold: 4.0,
        labelSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.4286, letterSpacing: nil, textCase: nil, textDecoration: nil),
        connector: 0.55,
        headlineMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 24.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        ghost: 0.18,
        radiusRound: 999.0,
        spaceSuperBig: 40.0,
        medium: FontWeightToken(weight: 500, italic: false),
        labelMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        link: TokensLink(
            colour: Color(.sRGB, red: 0.6235, green: 0.7451, blue: 0.9098, opacity: 1.0)
        ),
        library: TokensLibrary(
            colorLink: Color(.sRGB, red: 0.6039, green: 0.3961, blue: 0.0588, opacity: 1.0)
        )
    )

    static let schemeSignalLight = Tokens(
        surfaceVariant: Color(.sRGB, red: 0.8863, green: 0.9176, blue: 0.9373, opacity: 1.0),
        onBandMuted: Color(.sRGB, red: 0.4353, green: 0.5176, blue: 0.5804, opacity: 1.0),
        wordExtra: Color(.sRGB, red: 0.698, green: 0.2275, blue: 0.1686, opacity: 1.0),
        wordMissed: Color(.sRGB, red: 0.4353, green: 0.4588, blue: 0.4863, opacity: 1.0),
        bandVariant: Color(.sRGB, red: 0.0627, green: 0.0941, blue: 0.1255, opacity: 1.0),
        onAccent: Color(.sRGB, red: 1.0, green: 1.0, blue: 1.0, opacity: 1.0),
        error: Color(.sRGB, red: 0.698, green: 0.2275, blue: 0.1686, opacity: 1.0),
        outline: Color(.sRGB, red: 0.498, green: 0.5647, blue: 0.6157, opacity: 1.0),
        band: Color(.sRGB, red: 0.0392, green: 0.0588, blue: 0.0784, opacity: 1.0),
        wordMatched: Color(.sRGB, red: 0.1843, green: 0.5608, blue: 0.3569, opacity: 1.0),
        surface: Color(.sRGB, red: 1.0, green: 1.0, blue: 1.0, opacity: 1.0),
        textSecondary: Color(.sRGB, red: 0.2902, green: 0.3647, blue: 0.4196, opacity: 1.0),
        wordPartial: Color(.sRGB, red: 0.5412, green: 0.3529, blue: 0.0, opacity: 1.0),
        accent: Color(.sRGB, red: 0.0, green: 0.4118, blue: 0.3608, opacity: 1.0),
        textPrimary: Color(.sRGB, red: 0.0392, green: 0.0706, blue: 0.098, opacity: 1.0),
        background: Color(.sRGB, red: 0.949, green: 0.9647, blue: 0.9725, opacity: 1.0),
        onBand: Color(.sRGB, red: 0.902, green: 0.9333, blue: 0.9608, opacity: 1.0),
        bandOutline: Color(.sRGB, red: 0.2235, green: 0.3137, blue: 0.3725, opacity: 1.0),
        outlineVariant: Color(.sRGB, red: 0.6588, green: 0.7373, blue: 0.7961, opacity: 1.0),
        page: 1120.0,
        gap: 24.0,
        corner: 12.0,
        shadow: [ShadowToken(offsetX: 0.0, offsetY: 1.0, blur: 2.0, spread: 0.0, color: Color(.sRGB, red: 0.0784, green: 0.0902, blue: 0.102, opacity: 0.05), inset: false), ShadowToken(offsetX: 0.0, offsetY: 12.0, blur: 24.0, spread: 0.0, color: Color(.sRGB, red: 0.0784, green: 0.0902, blue: 0.102, opacity: 0.12), inset: false)],
        measure: 66.0,
        type: TokensType(
            _96: 96.0,
            _200: 200.0,
            _18: 18.0,
            _24: 24.0,
            _22: 22.0,
            _14: 14.0,
            _12: 12.0,
            _16: 16.0,
            _32: 32.0,
            _64: 64.0,
            _40: 40.0
        ),
        bodySmallRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.4286, letterSpacing: nil, textCase: nil, textDecoration: nil),
        space: TokensSpace(
            _9: 9.0,
            _11: 11.0,
            _40: 40.0,
            _4: 4.0,
            _5: 5.0,
            _20: 20.0,
            _15: 15.0,
            _8: 8.0,
            _13: 13.0,
            _2: 2.0,
            _12: 12.0
        ),
        bodySmallLooseRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.7143, letterSpacing: nil, textCase: nil, textDecoration: nil),
        size: TokensSize(
            _72: 72.0,
            _4: 4.0,
            _150: 150.0,
            _45: 45.0,
            _20: 20.0,
            _56: 56.0,
            _48: 48.0,
            _600: 600.0,
            _12: 12.0,
            _110: 110.0,
            _64: 64.0,
            _88: 88.0,
            _70: 70.0,
            _6: 6.0,
            _60: 60.0,
            _8: 8.0,
            _160: 160.0,
            _96: 96.0,
            _80: 80.0,
            _27: 27.0,
            _42: 42.0,
            _40: 40.0,
            _16: 16.0,
            _32: 32.0,
            _200: 200.0,
            _2: 2.0,
            _340: 340.0,
            _1: 1.0,
            _24: 24.0,
            _34: 34.0,
            _28: 28.0
        ),
        displayMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 32.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.25, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallTrackedRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.4286, letterSpacing: 1.1, textCase: nil, textDecoration: nil),
        bodyLargeMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 18.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodyLargeSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 18.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        displaySemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 32.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.25, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodyLargeRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 18.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        regular: FontWeightToken(weight: 400, italic: false),
        share: TokensShare(
            _100: 1.0,
            _75: 0.75,
            _66: 0.66,
            _25: 0.25,
            _33: 0.33,
            _50: 0.5
        ),
        strokeWide: 2.0,
        spaceTiny: 4.0,
        spaceSmall: 8.0,
        mono: ["IBM Plex Mono"],
        glyphSmall: 20.0,
        displayRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 32.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.25, letterSpacing: nil, textCase: nil, textDecoration: nil),
        semiBold: FontWeightToken(weight: 600, italic: false),
        tracking: 1.1,
        shadowEdgeUp: [ShadowToken(offsetX: 0.0, offsetY: -1.0, blur: 4.0, spread: 0.0, color: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 0.12), inset: false)],
        spaceMicro: 2.0,
        bodySemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil),
        titleRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 22.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.2727, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallLooseSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.7143, letterSpacing: nil, textCase: nil, textDecoration: nil),
        shadowLift: [ShadowToken(offsetX: 0.0, offsetY: 4.0, blur: 8.0, spread: 0.0, color: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 0.12), inset: false)],
        scrim: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 1.0),
        controlEdge: 3.0,
        glyph: 24.0,
        wordState: 3.0,
        stroke: 1.0,
        text: 4.5,
        heroSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 96.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.1, letterSpacing: nil, textCase: nil, textDecoration: nil),
        glyphHuge: 48.0,
        labelTrackedRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: 1.1, textCase: nil, textDecoration: nil),
        labelLooseSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 2.0, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.4286, letterSpacing: nil, textCase: nil, textDecoration: nil),
        titleSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 22.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.2727, letterSpacing: nil, textCase: nil, textDecoration: nil),
        posterSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 64.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.1, letterSpacing: nil, textCase: nil, textDecoration: nil),
        veil: 0.35,
        bodyMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil),
        labelLooseRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 2.0, letterSpacing: nil, textCase: nil, textDecoration: nil),
        headlineRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 24.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        iconLayer: 3.0,
        family: ["IBM Plex Sans"],
        bodyRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil),
        headlineSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 24.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        radius: TokensRadius(
            _12: 12.0,
            full: 999.0,
            _4: 4.0
        ),
        displayLargeSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 40.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.2, letterSpacing: nil, textCase: nil, textDecoration: nil),
        spaceMedium: 12.0,
        spaceBig: 20.0,
        giantSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 200.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.0, letterSpacing: nil, textCase: nil, textDecoration: nil),
        highlight: 1.5,
        labelRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        titleMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 22.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.2727, letterSpacing: nil, textCase: nil, textDecoration: nil),
        glyphBig: 32.0,
        strokeBold: 4.0,
        labelSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.4286, letterSpacing: nil, textCase: nil, textDecoration: nil),
        connector: 0.55,
        headlineMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 24.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        ghost: 0.18,
        radiusRound: 999.0,
        spaceSuperBig: 40.0,
        medium: FontWeightToken(weight: 500, italic: false),
        labelMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        link: TokensLink(
            colour: Color(.sRGB, red: 0.0, green: 0.4118, blue: 0.3608, opacity: 1.0)
        ),
        library: TokensLibrary(
            colorLink: Color(.sRGB, red: 0.6039, green: 0.3961, blue: 0.0588, opacity: 1.0)
        )
    )

    static let schemeSignalDark = Tokens(
        surfaceVariant: Color(.sRGB, red: 0.0902, green: 0.1333, blue: 0.1725, opacity: 1.0),
        onBandMuted: Color(.sRGB, red: 0.4353, green: 0.5176, blue: 0.5804, opacity: 1.0),
        wordExtra: Color(.sRGB, red: 1.0, green: 0.5412, blue: 0.4784, opacity: 1.0),
        wordMissed: Color(.sRGB, red: 0.502, green: 0.5255, blue: 0.5529, opacity: 1.0),
        bandVariant: Color(.sRGB, red: 0.0627, green: 0.0941, blue: 0.1255, opacity: 1.0),
        onAccent: Color(.sRGB, red: 0.0235, green: 0.1373, blue: 0.1216, opacity: 1.0),
        error: Color(.sRGB, red: 1.0, green: 0.5412, blue: 0.4784, opacity: 1.0),
        outline: Color(.sRGB, red: 0.3255, green: 0.4078, blue: 0.4627, opacity: 1.0),
        band: Color(.sRGB, red: 0.0902, green: 0.1333, blue: 0.1725, opacity: 1.0),
        wordMatched: Color(.sRGB, red: 0.3569, green: 0.7882, blue: 0.549, opacity: 1.0),
        surface: Color(.sRGB, red: 0.0627, green: 0.0941, blue: 0.1255, opacity: 1.0),
        textSecondary: Color(.sRGB, red: 0.5843, green: 0.6549, blue: 0.7059, opacity: 1.0),
        wordPartial: Color(.sRGB, red: 0.949, green: 0.7725, blue: 0.4196, opacity: 1.0),
        accent: Color(.sRGB, red: 0.3098, green: 0.8471, blue: 0.7529, opacity: 1.0),
        textPrimary: Color(.sRGB, red: 0.902, green: 0.9333, blue: 0.9608, opacity: 1.0),
        background: Color(.sRGB, red: 0.0392, green: 0.0588, blue: 0.0784, opacity: 1.0),
        onBand: Color(.sRGB, red: 0.902, green: 0.9333, blue: 0.9608, opacity: 1.0),
        bandOutline: Color(.sRGB, red: 0.2235, green: 0.3137, blue: 0.3725, opacity: 1.0),
        outlineVariant: Color(.sRGB, red: 0.2235, green: 0.3137, blue: 0.3725, opacity: 1.0),
        page: 1120.0,
        gap: 24.0,
        corner: 12.0,
        shadow: [ShadowToken(offsetX: 0.0, offsetY: 1.0, blur: 2.0, spread: 0.0, color: Color(.sRGB, red: 0.0784, green: 0.0902, blue: 0.102, opacity: 0.05), inset: false), ShadowToken(offsetX: 0.0, offsetY: 12.0, blur: 24.0, spread: 0.0, color: Color(.sRGB, red: 0.0784, green: 0.0902, blue: 0.102, opacity: 0.12), inset: false)],
        measure: 66.0,
        type: TokensType(
            _96: 96.0,
            _200: 200.0,
            _18: 18.0,
            _24: 24.0,
            _22: 22.0,
            _14: 14.0,
            _12: 12.0,
            _16: 16.0,
            _32: 32.0,
            _64: 64.0,
            _40: 40.0
        ),
        bodySmallRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.4286, letterSpacing: nil, textCase: nil, textDecoration: nil),
        space: TokensSpace(
            _9: 9.0,
            _11: 11.0,
            _40: 40.0,
            _4: 4.0,
            _5: 5.0,
            _20: 20.0,
            _15: 15.0,
            _8: 8.0,
            _13: 13.0,
            _2: 2.0,
            _12: 12.0
        ),
        bodySmallLooseRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.7143, letterSpacing: nil, textCase: nil, textDecoration: nil),
        size: TokensSize(
            _72: 72.0,
            _4: 4.0,
            _150: 150.0,
            _45: 45.0,
            _20: 20.0,
            _56: 56.0,
            _48: 48.0,
            _600: 600.0,
            _12: 12.0,
            _110: 110.0,
            _64: 64.0,
            _88: 88.0,
            _70: 70.0,
            _6: 6.0,
            _60: 60.0,
            _8: 8.0,
            _160: 160.0,
            _96: 96.0,
            _80: 80.0,
            _27: 27.0,
            _42: 42.0,
            _40: 40.0,
            _16: 16.0,
            _32: 32.0,
            _200: 200.0,
            _2: 2.0,
            _340: 340.0,
            _1: 1.0,
            _24: 24.0,
            _34: 34.0,
            _28: 28.0
        ),
        displayMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 32.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.25, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallTrackedRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.4286, letterSpacing: 1.1, textCase: nil, textDecoration: nil),
        bodyLargeMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 18.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodyLargeSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 18.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        displaySemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 32.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.25, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodyLargeRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 18.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        regular: FontWeightToken(weight: 400, italic: false),
        share: TokensShare(
            _100: 1.0,
            _75: 0.75,
            _66: 0.66,
            _25: 0.25,
            _33: 0.33,
            _50: 0.5
        ),
        strokeWide: 2.0,
        spaceTiny: 4.0,
        spaceSmall: 8.0,
        mono: ["IBM Plex Mono"],
        glyphSmall: 20.0,
        displayRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 32.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.25, letterSpacing: nil, textCase: nil, textDecoration: nil),
        semiBold: FontWeightToken(weight: 600, italic: false),
        tracking: 1.1,
        shadowEdgeUp: [ShadowToken(offsetX: 0.0, offsetY: -1.0, blur: 4.0, spread: 0.0, color: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 0.12), inset: false)],
        spaceMicro: 2.0,
        bodySemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil),
        titleRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 22.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.2727, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallLooseSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.7143, letterSpacing: nil, textCase: nil, textDecoration: nil),
        shadowLift: [ShadowToken(offsetX: 0.0, offsetY: 4.0, blur: 8.0, spread: 0.0, color: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 0.12), inset: false)],
        scrim: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 1.0),
        controlEdge: 3.0,
        glyph: 24.0,
        wordState: 3.0,
        stroke: 1.0,
        text: 4.5,
        heroSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 96.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.1, letterSpacing: nil, textCase: nil, textDecoration: nil),
        glyphHuge: 48.0,
        labelTrackedRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: 1.1, textCase: nil, textDecoration: nil),
        labelLooseSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 2.0, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.4286, letterSpacing: nil, textCase: nil, textDecoration: nil),
        titleSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 22.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.2727, letterSpacing: nil, textCase: nil, textDecoration: nil),
        posterSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 64.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.1, letterSpacing: nil, textCase: nil, textDecoration: nil),
        veil: 0.35,
        bodyMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil),
        labelLooseRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 2.0, letterSpacing: nil, textCase: nil, textDecoration: nil),
        headlineRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 24.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        iconLayer: 3.0,
        family: ["IBM Plex Sans"],
        bodyRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil),
        headlineSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 24.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        radius: TokensRadius(
            _12: 12.0,
            full: 999.0,
            _4: 4.0
        ),
        displayLargeSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 40.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.2, letterSpacing: nil, textCase: nil, textDecoration: nil),
        spaceMedium: 12.0,
        spaceBig: 20.0,
        giantSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 200.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.0, letterSpacing: nil, textCase: nil, textDecoration: nil),
        highlight: 1.5,
        labelRegular: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        titleMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 22.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.2727, letterSpacing: nil, textCase: nil, textDecoration: nil),
        glyphBig: 32.0,
        strokeBold: 4.0,
        labelSemiBold: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 600, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        bodySmallMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 14.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.4286, letterSpacing: nil, textCase: nil, textDecoration: nil),
        connector: 0.55,
        headlineMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 24.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        ghost: 0.18,
        radiusRound: 999.0,
        spaceSuperBig: 40.0,
        medium: FontWeightToken(weight: 500, italic: false),
        labelMedium: TypographyToken(fontFamily: ["IBM Plex Sans"], fontSize: 12.0, fontWeight: FontWeightToken(weight: 500, italic: false), lineHeight: 1.3333, letterSpacing: nil, textCase: nil, textDecoration: nil),
        link: TokensLink(
            colour: Color(.sRGB, red: 0.3098, green: 0.8471, blue: 0.7529, opacity: 1.0)
        ),
        library: TokensLibrary(
            colorLink: Color(.sRGB, red: 0.6039, green: 0.3961, blue: 0.0588, opacity: 1.0)
        )
    )

    static let standard = schemeStudioMonitorLight
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
