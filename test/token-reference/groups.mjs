const checkIfInsideGroup = (expr, fullExpr) => {
  const exprEscaped = expr.replace(/([.?*+^$[\]\\(){}|-])/g, '\\$1');
  const reg = new RegExp(`\\(.*?${exprEscaped}.*?\\)`, 'g');
  return !!fullExpr.match(reg) || !!expr.match(/\(/g);
};

let input = '';
for await (const chunk of process.stdin) input += chunk;
process.stdout.write(JSON.stringify(JSON.parse(input).map((full) => full.split(' ').map((piece) => checkIfInsideGroup(piece, full)))));
