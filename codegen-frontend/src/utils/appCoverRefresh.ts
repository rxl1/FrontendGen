const PENDING_APP_COVERS_KEY = 'codegen:pending-app-covers'
// Vue 首次生成还需要执行 npm install 和构建，最长可能超过 5 分钟。
const PENDING_APP_COVER_TTL_MS = 10 * 60 * 1000

export interface PendingAppCover {
  appId: string
  previousCover: string
  expiresAt: number
}

type PendingAppCoverMap = Record<string, PendingAppCover>

const readPendingAppCovers = (): PendingAppCoverMap => {
  try {
    const storedValue = window.localStorage.getItem(PENDING_APP_COVERS_KEY)
    if (!storedValue) return {}

    const pendingAppCovers = JSON.parse(storedValue) as PendingAppCoverMap
    const now = Date.now()
    return Object.fromEntries(
      Object.entries(pendingAppCovers).filter(
        ([, pendingAppCover]) => pendingAppCover.expiresAt > now,
      ),
    )
  } catch (error) {
    console.warn('读取待刷新的应用封面失败：', error)
    return {}
  }
}

const writePendingAppCovers = (pendingAppCovers: PendingAppCoverMap) => {
  if (Object.keys(pendingAppCovers).length === 0) {
    window.localStorage.removeItem(PENDING_APP_COVERS_KEY)
    return
  }
  window.localStorage.setItem(PENDING_APP_COVERS_KEY, JSON.stringify(pendingAppCovers))
}

export const markAppCoverPending = (appId: string | number, previousCover?: string) => {
  const pendingAppCovers = readPendingAppCovers()
  const normalizedAppId = String(appId)
  pendingAppCovers[normalizedAppId] = {
    appId: normalizedAppId,
    previousCover: previousCover || '',
    expiresAt: Date.now() + PENDING_APP_COVER_TTL_MS,
  }
  writePendingAppCovers(pendingAppCovers)
}

export const getPendingAppCovers = (): PendingAppCover[] => {
  const pendingAppCovers = readPendingAppCovers()
  writePendingAppCovers(pendingAppCovers)
  return Object.values(pendingAppCovers)
}

export const clearPendingAppCover = (appId: string | number) => {
  const pendingAppCovers = readPendingAppCovers()
  delete pendingAppCovers[String(appId)]
  writePendingAppCovers(pendingAppCovers)
}

export const isPendingAppCoversStorageEvent = (event: StorageEvent) => {
  return event.key === PENDING_APP_COVERS_KEY
}
