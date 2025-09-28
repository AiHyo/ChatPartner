import router from '@/router'

function handleResp<T>(data: any, method: string, url: string): T {
  const code = data?.code
  if (code === 0) return data.data as T
  // 未登录：后端使用 ErrorCode.NOT_LOGIN_ERROR = 40100
  if (code === 40100) {
    const route = router.currentRoute.value
    const isAuthPage = route.name === 'login' || route.name === 'register' || String(route.path || '').startsWith('/login')
    // 若已在登录/注册页，尽量复用已有 redirect，避免把“/login?redirect=...”再作为 redirect 造成死循环
    const existingRedirect = typeof route.query?.redirect === 'string' ? route.query.redirect : undefined
    const target = isAuthPage ? (existingRedirect || '/') : route.fullPath
    const redirect = encodeURIComponent(target)
    // 尝试路由跳转；若失败则回退到 location.href
    try { router.push({ name: 'login', query: { redirect } }) } catch { window.location.href = `/login?redirect=${redirect}` }
    throw new Error('未登录')
  }
  throw new Error(data?.message || `${method} ${url} failed`)
}

export async function httpGet<T>(url: string, params?: Record<string, any>): Promise<T> {
  const u = new URL(url, window.location.origin)
  if (params) {
    for (const k in params) {
      const v = (params as any)[k]
      if (v !== undefined) u.searchParams.append(k, String(v))
    }
  }
  const res = await fetch(u.toString(), { credentials: 'include' })
  if (!res.ok) {
    if (res.status === 401) {
      const route = router.currentRoute.value
      const isAuthPage = route.name === 'login' || route.name === 'register' || String(route.path || '').startsWith('/login')
      const existingRedirect = typeof route.query?.redirect === 'string' ? route.query.redirect : undefined
      const target = isAuthPage ? (existingRedirect || '/') : route.fullPath
      const redirect = encodeURIComponent(target)
      try { router.push({ name: 'login', query: { redirect } }) } catch { window.location.href = `/login?redirect=${redirect}` }
    }
    throw new Error(`GET ${url} failed: ${res.status}`)
  }
  const data = await res.json()
  return handleResp<T>(data, 'GET', url)
}

export async function httpPost<T>(url: string, body?: any): Promise<T> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) {
    if (res.status === 401) {
      const route = router.currentRoute.value
      const isAuthPage = route.name === 'login' || route.name === 'register' || String(route.path || '').startsWith('/login')
      const existingRedirect = typeof route.query?.redirect === 'string' ? route.query.redirect : undefined
      const target = isAuthPage ? (existingRedirect || '/') : route.fullPath
      const redirect = encodeURIComponent(target)
      try { router.push({ name: 'login', query: { redirect } }) } catch { window.location.href = `/login?redirect=${redirect}` }
    }
    throw new Error(`POST ${url} failed: ${res.status}`)
  }
  const data = await res.json()
  return handleResp<T>(data, 'POST', url)
}

export async function httpDelete<T>(url: string): Promise<T> {
  const res = await fetch(url, { method: 'DELETE', credentials: 'include' })
  if (!res.ok) {
    if (res.status === 401) {
      const route = router.currentRoute.value
      const isAuthPage = route.name === 'login' || route.name === 'register' || String(route.path || '').startsWith('/login')
      const existingRedirect = typeof route.query?.redirect === 'string' ? route.query.redirect : undefined
      const target = isAuthPage ? (existingRedirect || '/') : route.fullPath
      const redirect = encodeURIComponent(target)
      try { router.push({ name: 'login', query: { redirect } }) } catch { window.location.href = `/login?redirect=${redirect}` }
    }
    throw new Error(`DELETE ${url} failed: ${res.status}`)
  }
  const data = await res.json()
  return handleResp<T>(data, 'DELETE', url)
}

export async function httpPatch<T>(url: string, body?: any): Promise<T> {
  const res = await fetch(url, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) {
    if (res.status === 401) {
      const route = router.currentRoute.value
      const isAuthPage = route.name === 'login' || route.name === 'register' || String(route.path || '').startsWith('/login')
      const existingRedirect = typeof route.query?.redirect === 'string' ? route.query.redirect : undefined
      const target = isAuthPage ? (existingRedirect || '/') : route.fullPath
      const redirect = encodeURIComponent(target)
      try { router.push({ name: 'login', query: { redirect } }) } catch { window.location.href = `/login?redirect=${redirect}` }
    }
    throw new Error(`PATCH ${url} failed: ${res.status}`)
  }
  const data = await res.json()
  return handleResp<T>(data, 'PATCH', url)
}
