export const AUTH_ROLES = { ROLE_USER: '의뢰인', ROLE_LAWYER: '담당 변호사', ROLE_ADMIN: '사무소 관리자' };
export const isStaff = user => ['ROLE_ADMIN', 'ROLE_LAWYER'].includes(user?.role);
export function loginCredentials(username, password) {
  const fieldErrors = {};
  if (typeof username !== 'string' || !username.trim()) fieldErrors.username = '이메일 또는 아이디를 입력해 주세요.';
  if (typeof password !== 'string' || !password.trim()) fieldErrors.password = '비밀번호를 입력해 주세요.';
  if (Object.keys(fieldErrors).length) { const error = new Error(Object.values(fieldErrors)[0]); error.fieldErrors = fieldErrors; throw error; }
  return { username: username.trim(), password };
}
export async function authenticate(api, username, password, signal, onStage = () => {}) {
  const credentials = loginCredentials(username, password);
  let login;
  try { login = await api.login(credentials.username, credentials.password, signal); }
  catch (error) {
    if (error.status === 401) error.message = '아이디 또는 비밀번호가 올바르지 않습니다. 다시 확인해 주세요.';
    throw error;
  }
  signal?.throwIfAborted(); onStage('profile');
  const profile = await api.currentUser(login.accessToken, signal);
  signal?.throwIfAborted();
  if (profile.username !== login.username || profile.role !== login.role) throw new Error('로그인 응답과 계정 정보가 일치하지 않습니다. 다시 로그인해 주세요.');
  return { ...profile, accessToken: login.accessToken };
}
export const loginDestination = role => role === 'ROLE_ADMIN' ? '/account' : role === 'ROLE_LAWYER' ? '/lawyer/intakes' : '/intake/type';
