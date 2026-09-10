#!/usr/bin/env bash
set -Eeuo pipefail

base_url="${SUWAIRO_VERIFY_URL:-http://127.0.0.1:4567}"
read -r -p 'Owner username: ' owner_username
read -r -s -p 'Owner password: ' owner_password
printf '\n'
read -r -p 'Test username [suwairo-test]: ' test_username
test_username="${test_username:-suwairo-test}"
read -r -s -p 'Test password: ' test_password
printf '\n'

json_escape() {
    jq -Rn --arg value "$1" '$value'
}

gql() {
    local token="$1"
    local query="$2"
    local variables="${3:-{}}"
    local response
    local -a curl_args=(
        --fail-with-body
        --silent
        --show-error
        -H 'content-type: application/json'
    )
    if [[ -n "$token" ]]; then
        curl_args+=( -H "authorization: Bearer $token" )
    fi
    response="$(curl "${curl_args[@]}" \
        --data "$(jq -cn --arg query "$query" --argjson variables "$variables" '{query: $query, variables: $variables}')" \
        "$base_url/api/graphql")"
    if jq -e '.errors and (.errors | length > 0)' >/dev/null <<<"$response"; then
        jq -c '.errors' <<<"$response" >&2
        return 1
    fi
    jq -c '.data' <<<"$response"
}

login() {
    local username_json password_json response
    username_json="$(json_escape "$1")"
    password_json="$(json_escape "$2")"
    response="$(gql '' 'mutation($input: LoginInput!) { login(input: $input) { accessToken refreshToken } }' "$(jq -cn --argjson username "$username_json" --argjson password "$password_json" '{input: {username: $username, password: $password}}')")"
    jq -r '.login.accessToken' <<<"$response"
}

owner_token="$(login "$owner_username" "$owner_password")"
test_token="$(login "$test_username" "$test_password")" 2>/dev/null || true

if [[ "$test_token" == "null" || -z "$test_token" ]]; then
    non_owner_role_id="$(gql "$owner_token" '{ roles { id name } }' | jq -r '.roles[] | select(.name != "owner") | .id' | head -n 1)"
    if [[ -z "$non_owner_role_id" ]]; then
        printf 'Could not find a non-owner role for the test user.\n' >&2
        exit 1
    fi
    user_id="$(gql "$owner_token" 'mutation($input: CreateUserInput!) { createUser(input: $input) { id } }' "$(jq -cn --arg username "$test_username" --arg password "$test_password" --argjson roleId "$non_owner_role_id" '{input: {username: $username, password: $password, displayName: $username, roleId: $roleId}}')" | jq -r '.createUser.id')"
    [[ "$user_id" != "null" ]] || { printf 'Could not create test user.\n' >&2; exit 1; }
    test_token="$(login "$test_username" "$test_password")"
fi

printf 'Checking user-settings isolation...\n'
gql "$owner_token" 'mutation($input: SetUserSettingsInput!) { setUserSettings(input: $input) { updated } }' "$(jq -cn '{input: {settings: [{key: "appTheme", value: "owner-test"}]}}')" >/dev/null
owner_value="$(gql "$owner_token" '{ userSettings { key value } }' | jq -r '.userSettings[] | select(.key == "appTheme") | .value')"
test_value="$(gql "$test_token" '{ userSettings { key value } }' | jq -r '.userSettings[] | select(.key == "appTheme") | .value')"
[[ "$owner_value" == 'owner-test' && "$test_value" != 'owner-test' ]] || { printf 'User-settings isolation failed.\n' >&2; exit 1; }

printf 'Checking directory and messaging...\n'
gql "$test_token" '{ userDirectory { id username } }' >/dev/null
owner_id="$(gql "$owner_token" '{ currentUserProfile { id } }' | jq -r '.currentUserProfile.id')"
gql "$test_token" 'mutation($input: SendMessageInput!) { sendMessage(input: $input) { messageId } }' "$(jq -cn --argjson receiverId "$owner_id" '{input: {receiverId: $receiverId, content: "suwairo verification"}}')" >/dev/null

printf 'Checking owner-only backup denial...\n'
if gql "$test_token" 'mutation { createBackup { url } }' >/dev/null 2>&1; then
    printf 'Non-owner backup authorization failed.\n' >&2
    exit 1
fi

printf 'Multi-user verification passed.\n'
