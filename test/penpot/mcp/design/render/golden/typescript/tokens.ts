export const themes = {
  "brand=a;mode=light": {
    space: {
      base: "4px",
    },
    radius: {
      card: "8px",
    },
    font: {
      weight: {
        strong: { weight: 700, italic: true },
      },
    },
    type: {
      body: { fontFamily: "Inter, \"Segoe UI\"", fontSize: "16px", fontWeight: { weight: 400, italic: false }, lineHeight: 1.5 },
    },
    shadow: {
      lift: "0px 2px 4px 0px rgba(0, 0, 0, 0.25)",
    },
    opacity: {
      muted: 0.5,
    },
    color: {
      bg: "#ffffff",
      text: "#111111",
      accent: "#3366ff",
    },
    library: {
      color: {
        brand: {
          primary: "#3366ff",
        },
      },
    },
  },
  "brand=a;mode=dark": {
    space: {
      base: "4px",
    },
    radius: {
      card: "8px",
    },
    font: {
      weight: {
        strong: { weight: 700, italic: true },
      },
    },
    type: {
      body: { fontFamily: "Inter, \"Segoe UI\"", fontSize: "16px", fontWeight: { weight: 400, italic: false }, lineHeight: 1.5 },
    },
    shadow: {
      lift: "0px 2px 4px 0px rgba(0, 0, 0, 0.25)",
    },
    opacity: {
      muted: 0.5,
    },
    color: {
      bg: "#111111",
      text: "#111111",
      accent: "#3366ff",
    },
    library: {
      color: {
        brand: {
          primary: "#3366ff",
        },
      },
    },
  },
  "brand=b;mode=light": {
    space: {
      base: "4px",
    },
    radius: {
      card: "8px",
    },
    font: {
      weight: {
        strong: { weight: 700, italic: true },
      },
    },
    type: {
      body: { fontFamily: "Inter, \"Segoe UI\"", fontSize: "16px", fontWeight: { weight: 400, italic: false }, lineHeight: 1.5 },
    },
    shadow: {
      lift: "0px 2px 4px 0px rgba(0, 0, 0, 0.25)",
    },
    opacity: {
      muted: 0.5,
    },
    color: {
      bg: "#ffffff",
      text: "#111111",
      accent: "#ff3366",
    },
    library: {
      color: {
        brand: {
          primary: "#3366ff",
        },
      },
    },
  },
  "brand=b;mode=dark": {
    space: {
      base: "4px",
    },
    radius: {
      card: "8px",
    },
    font: {
      weight: {
        strong: { weight: 700, italic: true },
      },
    },
    type: {
      body: { fontFamily: "Inter, \"Segoe UI\"", fontSize: "16px", fontWeight: { weight: 400, italic: false }, lineHeight: 1.5 },
    },
    shadow: {
      lift: "0px 2px 4px 0px rgba(0, 0, 0, 0.25)",
    },
    opacity: {
      muted: 0.5,
    },
    color: {
      bg: "#111111",
      text: "#111111",
      accent: "#ff3366",
    },
    library: {
      color: {
        brand: {
          primary: "#3366ff",
        },
      },
    },
  },
} as const;
export const defaultTheme = "brand=a;mode=light";
export type ThemeId = keyof typeof themes;
export type Tokens = (typeof themes)[ThemeId];
