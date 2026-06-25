import { reactive } from 'vue'
import type { TokenPair } from '../api/auth'

const state = reactive({
  accessToken: sessionStorage.getItem('accessToken') ?? '',
  refreshToken: sessionStorage.getItem('refreshToken') ?? '',
})

export function useSession() {
  const save = (tokens: TokenPair) => {
    state.accessToken = tokens.accessToken
    state.refreshToken = tokens.refreshToken
    sessionStorage.setItem('accessToken', tokens.accessToken)
    sessionStorage.setItem('refreshToken', tokens.refreshToken)
  }
  const clear = () => {
    state.accessToken = ''
    state.refreshToken = ''
    sessionStorage.clear()
  }
  return { state, save, clear }
}

