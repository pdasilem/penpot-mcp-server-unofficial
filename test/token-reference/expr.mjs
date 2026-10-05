import { Parser } from 'expr-eval-fork';

const encode = (value) => {
  if (typeof value === 'number') return ['number', String(value)];
  if (typeof value === 'string') return ['string', value];
  if (typeof value === 'boolean') return ['boolean', value];
  if (value === undefined) return ['undefined', null];
  if (value === null) return ['null', null];
  if (typeof value === 'function') return ['function', null];
  if (Array.isArray(value)) return ['array', value.map(encode)];
  return ['object', null];
};

const run = (expression) => {
  try {
    return ['ok', encode(new Parser().evaluate(expression))];
  } catch (e) {
    return ['error', String(e && e.message)];
  }
};

const chunks = [];
process.stdin.on('data', (chunk) => chunks.push(chunk));
process.stdin.on('end', () => {
  const expressions = JSON.parse(Buffer.concat(chunks).toString('utf8'));
  process.stdout.write(JSON.stringify(expressions.map(run)));
});
