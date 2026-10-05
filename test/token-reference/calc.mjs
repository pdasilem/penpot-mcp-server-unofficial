import { parse, reduceExpression } from '@bundled-es-modules/postcss-calc-ast-parser';

let input = '';
for await (const chunk of process.stdin) input += chunk;

const evaluate = (source) => {
  try {
    const reduced = reduceExpression(parse(source, { allowInlineCommnets: false }));
    if (!reduced) return ['falsy'];
    return ['ok', { value: String(reduced.value), unit: reduced.unit === undefined ? null : reduced.unit, type: reduced.type }];
  } catch (error) {
    return ['error', String(error && error.message)];
  }
};

process.stdout.write(JSON.stringify(JSON.parse(input).map(evaluate)));
