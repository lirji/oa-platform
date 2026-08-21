#!/usr/bin/env bash
# 幂等创建/更新本地 Casdoor 的 OA SPA 应用。仅用于 localhost 开发环境；不会打印 client_secret 或 token。
set -euo pipefail

CASDOOR="${CASDOOR_URL:-http://localhost:8000}"
APP_NAME="${OA_CASDOOR_APP_NAME:-oa-platform}"
CLIENT_ID="${OA_CASDOOR_CLIENT_ID:-oa-platform-local}"
CLIENT_SECRET="${OA_CASDOOR_CLIENT_SECRET:-oa-platform-local-secret-2026}"
ADMIN_USER="${CASDOOR_ADMIN:-admin}"
ADMIN_PASSWORD="${CASDOOR_ADMIN_PW:-123}"
BUILTIN_CLIENT_ID="${CASDOOR_BUILTIN_CLIENT_ID:-ea46d9a8033b0be2d8ed}"

command -v jq >/dev/null
command -v curl >/dev/null
docker container inspect authz-casdoor >/dev/null 2>&1 || { echo "Casdoor 容器 authz-casdoor 未运行" >&2; exit 1; }
docker container inspect authz-postgres >/dev/null 2>&1 || { echo "Casdoor 数据库容器 authz-postgres 未运行" >&2; exit 1; }

BUILTIN_SECRET=$(docker exec authz-postgres psql -U authz -d spicedb -tAc \
  "select client_secret from application where client_id='${BUILTIN_CLIENT_ID}'" | tr -d '[:space:]')
[ -n "$BUILTIN_SECRET" ] || { echo "读不到 Casdoor built-in client secret" >&2; exit 1; }

ADMIN_TOKEN=$(curl -fsS -X POST "$CASDOOR/api/login/oauth/access_token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=password' \
  --data-urlencode "username=$ADMIN_USER" \
  --data-urlencode "password=$ADMIN_PASSWORD" \
  --data-urlencode "client_id=$BUILTIN_CLIENT_ID" \
  --data-urlencode "client_secret=$BUILTIN_SECRET" \
  --data-urlencode 'scope=openid profile' | jq -r '.access_token // empty')
[ -n "$ADMIN_TOKEN" ] || { echo "无法取得 Casdoor 管理 token" >&2; exit 1; }

existing=$(curl -fsS "$CASDOOR/api/get-application?id=admin/$APP_NAME" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -c '.data // empty')
redirects='["http://localhost:5473/callback","http://127.0.0.1:5473/callback","http://localhost:8404/callback","http://127.0.0.1:8404/callback"]'

if [ -z "$existing" ]; then
  payload=$(jq -nc --arg name "$APP_NAME" --arg cid "$CLIENT_ID" --arg secret "$CLIENT_SECRET" \
    --argjson redirects "$redirects" '{owner:"admin",name:$name,displayName:"OA Platform",organization:"built-in",cert:"cert-built-in",tokenFormat:"JWT",expireInHours:0.25,refreshExpireInHours:168,enablePassword:true,enableSignUp:false,clientId:$cid,clientSecret:$secret,grantTypes:["authorization_code","refresh_token","password"],redirectUris:$redirects,signinMethods:[{name:"Password",displayName:"Password",rule:"All"}],providers:[]}')
  result=$(curl -fsS -X POST "$CASDOOR/api/add-application" -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H 'Content-Type: application/json' -d "$payload")
else
  payload=$(printf '%s' "$existing" | jq -c --arg cid "$CLIENT_ID" --arg secret "$CLIENT_SECRET" \
    --argjson redirects "$redirects" '.displayName="OA Platform" | .organization="built-in" | .cert="cert-built-in" | .tokenFormat="JWT" | .expireInHours=0.25 | .refreshExpireInHours=168 | .enablePassword=true | .enableSignUp=false | .clientId=$cid | .clientSecret=$secret | .grantTypes=["authorization_code","refresh_token","password"] | .redirectUris=$redirects | .signinMethods=[{name:"Password",displayName:"Password",rule:"All"}]')
  result=$(curl -fsS -X POST "$CASDOOR/api/update-application?id=admin/$APP_NAME" \
    -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' -d "$payload")
fi

status=$(printf '%s' "$result" | jq -r '.status // empty')
[ "$status" = "ok" ] || { printf '%s\n' "$result" | jq -c '{status,msg}' >&2; exit 1; }

verified=$(curl -fsS "$CASDOOR/api/get-application?id=admin/$APP_NAME" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r --arg cid "$CLIENT_ID" \
  '.data | (.clientId == $cid and (.grantTypes | index("authorization_code") != null) and (.grantTypes | index("refresh_token") != null))')
[ "$verified" = "true" ] || { echo "Casdoor OA 应用回读校验失败" >&2; exit 1; }

echo "Casdoor OA 应用已就绪：name=$APP_NAME client_id=$CLIENT_ID redirects=4"
