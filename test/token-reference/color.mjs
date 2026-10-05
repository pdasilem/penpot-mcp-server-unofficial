import StyleDictionary from 'style-dictionary';
import { transformHEXRGBaForCSS } from '@tokens-studio/sd-transforms';
import tinycolor from 'tinycolor2';

const cssColor = StyleDictionary.hooks.transforms['color/css'];
const options = { usesDtcg: true };

const css = (s) => {
  const token = { $type: 'color', $value: s };
  return cssColor.filter(token, options) ? cssColor.transform(token, {}, options) : null;
};

const hexrgba = (s) => transformHEXRGBaForCSS({ $type: 'color', $value: s });

const wrapper = (s) => {
  const tc = tinycolor(s);
  const startsWithHash = String(tc.getOriginalInput()).startsWith('#');
  const hex = tc.getFormat() && String(tc.getFormat()).startsWith('hex');
  return tc.isValid() && tc.getFormat() && (hex ? startsWithHash : true) ? tc : null;
};

const describe = (s) => {
  const tc = tinycolor(s);
  const valid = wrapper(s);
  return {
    css: css(s),
    hexrgba: hexrgba(s),
    valid: valid !== null,
    format: valid === null ? null : valid.getFormat(),
    ok: tc.isValid(),
    hex: tc.toHexString(),
    rgb: tc.toRgbString(),
    alpha: String(tc.getAlpha()),
    rgba: valid === null ? null : (({ r, g, b, a }) => ({ r, g, b, a: String(a) }))(valid.toRgb()),
  };
};

let input = '';
for await (const chunk of process.stdin) input += chunk;
const originalWarn = console.warn;
console.warn = () => {};
process.stdout.write(JSON.stringify(JSON.parse(input).map(describe)));
console.warn = originalWarn;
