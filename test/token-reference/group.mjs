import StyleDictionary from 'style-dictionary';
import { register, getTransforms } from '@tokens-studio/sd-transforms';

register(StyleDictionary);
const names = getTransforms().concat(['ts/color/css/hexrgba', 'ts/color/modifiers', 'color/css']);
const group = names.map((name) => ({ name, type: StyleDictionary.hooks.transforms[name].type }));
process.stdout.write(JSON.stringify(group));
