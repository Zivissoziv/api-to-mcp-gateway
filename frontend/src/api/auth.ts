export interface TokenPair {
  accessToken: string
  refreshToken: string
  expiresInSeconds: number
}

export async function login(username: string, password: string): Promise<TokenPair> {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  if (!response.ok) {
    throw new Error(response.status === 401 ? '用户名或密码错误' : '登录失败，请稍后重试')
  }
  return response.json()
}

