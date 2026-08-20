/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_AUTH_ENABLED?: string
  readonly VITE_DEV_USER?: string
  readonly VITE_CASDOOR_AUTHORITY?: string
  readonly VITE_CASDOOR_CLIENT_ID?: string
  readonly VITE_OIDC_SCOPE?: string
  readonly VITE_WS_PATH?: string
  // 仅 dev proxy 用，不进产物
  readonly VITE_API_TARGET?: string
  readonly VITE_NOTIFY_TARGET?: string
  readonly VITE_FILE_TARGET?: string
  readonly VITE_JOB_TARGET?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
