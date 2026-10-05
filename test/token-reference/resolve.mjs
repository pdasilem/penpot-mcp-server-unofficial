import StyleDictionary from 'style-dictionary';
import { register, getTransforms } from '@tokens-studio/sd-transforms';

register(StyleDictionary);
StyleDictionary.registerTransformGroup({
  name: 'penpot',
  transforms: getTransforms().concat(['ts/color/css/hexrgba', 'ts/color/modifiers', 'color/css']),
});
StyleDictionary.registerFormat({ name: 'custom/json', format: (res) => res.dictionary.tokens });

const resolve = async (tree) => {
  const sd = new StyleDictionary({
    tokens: tree,
    platforms: { json: { transformGroup: 'penpot', files: [{ format: 'custom/json', destination: 'penpot' }] } },
    preprocessors: ['tokens-studio'],
    log: { verbosity: 'silent', warnings: 'silent', errors: { brokenReferences: 'console' } },
  });
  const built = await sd.buildAllPlatforms('json');
  const platform = await built.getPlatformTokens('json');
  const out = {};
  for (const t of platform.allTokens) if (t.original && t.original.name !== undefined) out[t.original.name] = t.value;
  return out;
};

let input = '';
for await (const chunk of process.stdin) input += chunk;
const { cases } = JSON.parse(input);
const results = [];
for (const c of cases) {
  try {
    results.push(await resolve(c.tree));
  } catch (e) {
    results.push({ __error: e.name });
  }
}
process.stdout.write(JSON.stringify({ results }));
