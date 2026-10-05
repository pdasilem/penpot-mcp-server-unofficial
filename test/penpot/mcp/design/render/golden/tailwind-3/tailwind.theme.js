module.exports = {
  theme: {
    extend: {
      colors: {
        "bg": "var(--sv-color-bg)",
        "text": "var(--sv-color-text)",
        "accent": "var(--sv-color-accent)",
        "brand": {
          "primary": "var(--sv-library-color-brand-primary)"
        }
      },
      spacing: {
        "base": "var(--sv-space-base)"
      },
      borderRadius: {
        "card": "var(--sv-radius-card)"
      },
      fontFamily: {
        "type": {
          "body": "var(--sv-type-body-font-family)"
        }
      },
      fontSize: {
        "type": {
          "body": ["var(--sv-type-body-font-size)", { lineHeight: "var(--sv-type-body-line-height)", fontWeight: "var(--sv-type-body-font-weight)" }]
        }
      },
      fontWeight: {
        "strong": "var(--sv-font-weight-strong)"
      },
      boxShadow: {
        "lift": "var(--sv-shadow-lift)"
      },
      opacity: {
        "muted": "var(--sv-opacity-muted)"
      }
    }
  }
};
