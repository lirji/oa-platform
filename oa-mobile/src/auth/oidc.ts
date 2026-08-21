import { UserManager, WebStorageStateStore, type UserManagerSettings } from 'oidc-client-ts'
import { config } from '../config'

export const oidcSettings: UserManagerSettings = {
  authority: config.casdoorAuthority,
  client_id: config.casdoorClientId,
  redirect_uri: `${window.location.origin}/callback`,
  post_logout_redirect_uri: `${window.location.origin}/login`,
  response_type: 'code', scope: config.oidcScope, loadUserInfo: false, automaticSilentRenew: true,
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
}
export const userManager = new UserManager({ ...oidcSettings, automaticSilentRenew: false })
