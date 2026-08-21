import type { ReactNode } from 'react'
import { AuthProvider } from 'react-oidc-context'
import { oidcSettings } from './oidc'

export default function MobileAuthProvider({ children }: { children: ReactNode }) {
  return <AuthProvider {...oidcSettings} onSigninCallback={() => window.history.replaceState({}, document.title, window.location.pathname)}>{children}</AuthProvider>
}
