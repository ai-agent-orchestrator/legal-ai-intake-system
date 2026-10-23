export function readOperatorRoute(hash = '') {
  const [path, query = ''] = hash.replace(/^#\/?/, '').split('?');
  const redirect = path === 'observability-guide';
  const route = redirect ? 'observability' : path;
  const value = new URLSearchParams(query).get('riskId');
  const riskId = route === 'audit' && /^[1-9]\d*$/.test(value || '') && Number.isSafeInteger(Number(value)) ? Number(value) : null;
  return { route, riskId, redirect };
}
