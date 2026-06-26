const BASE = ''

function headers(): Record<string, string> {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${sessionStorage.getItem('accessToken')}`,
  }
}

export async function fetchJson<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(BASE + url, { ...init, headers: { ...headers(), ...init?.headers as any } })
  if (!res.ok) {
    if (res.status === 401) {
      sessionStorage.clear()
      // Use location.href instead of router to work outside Vue components
      window.location.href = '/#/login'
      throw new Error('登录已过期，请重新登录')
    }
    const body = await res.text()
    try {
      const parsed = JSON.parse(body)
      throw new Error(parsed.message || `Request failed (${res.status})`)
    } catch {
      throw new Error(`Request failed (${res.status})`)
    }
  }
  return (await res.json()) as T
}
