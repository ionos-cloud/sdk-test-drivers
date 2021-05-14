exports.getFuncArgs = function(func) {
  return (func + '')
    .replace(/[/][/].*$/mg,'') // strip single-line comments
    .replace(/\s+/g, '') // strip white space
    .replace(/[/][*][^/*]*[*][/]/g, '') // strip multi-line comments
    .split('){', 1)[0].replace(/^[^(]*[(]/, '') // extract the parameters
    .replace(/=[^,]+/g, '') // strip any ES6 defaults
    .split(',').filter(Boolean); // split & filter [""]
}

exports.getStdin = async () => {

  let result = '';

  process.stdin.setEncoding('utf8');

  for await (const chunk of process.stdin) {
    result += chunk;
  }

  return result;

}
