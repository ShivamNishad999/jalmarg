#!/bin/bash
# Switch the live JalMarg server between the team web version and the app version.
# Run in AWS CloudShell (same region as the stack):
#   curl -fsSL https://raw.githubusercontent.com/ShivamNishad999/jalmarg/web-deploy/infra/switch-version.sh -o switch.sh && bash switch.sh team
#   bash switch.sh app      # go back to the Android-app version
# Each version keeps its own database file, so switching never mixes or loses data.
set -euo pipefail
MODE="${1:-team}"
REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-ap-south-1}}"
STACK=jalmarg

case "$MODE" in
  team) TAG=web-latest;    DB='jdbc:h2:file:./data/teamweb;MODE=MySQL;AUTO_SERVER=TRUE' ;;
  app)  TAG=server-latest; DB='' ;;
  *) echo "Usage: bash switch.sh team|app"; exit 1 ;;
esac

ID=$(aws cloudformation describe-stacks --region "$REGION" --stack-name "$STACK" \
      --query "Stacks[0].Outputs[?OutputKey=='ServerInstanceId'].OutputValue" --output text)
URL=$(aws cloudformation describe-stacks --region "$REGION" --stack-name "$STACK" \
      --query "Stacks[0].Outputs[?OutputKey=='AppUrl'].OutputValue" --output text)
echo "Server $ID -> $MODE version ($TAG)"

# Script that runs ON the server (as root through Systems Manager; no SSH, no keys).
REMOTE=$(cat <<EOF
set -e
ENV=/etc/jalmarg.env
touch \$ENV; chmod 600 \$ENV
# one-time random secrets for the team version (kept across switches, never printed except the staff password)
grep -q '^ADMIN_KEY=' \$ENV || echo "ADMIN_KEY=\"\$(head -c 48 /dev/urandom | base64 | tr -dc A-Za-z0-9 | head -c 40)\"" >> \$ENV
grep -q '^MUNICIPAL_PASSWORD=' \$ENV || echo "MUNICIPAL_PASSWORD=\"Jm\$(head -c 24 /dev/urandom | base64 | tr -dc A-Za-z0-9 | head -c 14)#26\"" >> \$ENV
grep -q '^VISION_ENABLED=' \$ENV || echo 'VISION_ENABLED="false"' >> \$ENV
sed -i '/^DB_URL=/d' \$ENV
if [ -n "$DB" ]; then echo 'DB_URL="$DB"' >> \$ENV; fi
sed -i 's#/releases/download/[a-z-]*-latest#/releases/download/$TAG#' /opt/jalmarg/update.sh
/opt/jalmarg/update.sh
for i in \$(seq 1 60); do curl -fs http://localhost:8091/api/health >/dev/null && break; sleep 3; done
curl -s http://localhost:8091/api/health; echo
if [ "$MODE" = team ]; then
  echo "STAFF_LOGIN usernames: admin_ndmc (admin), operator_pwd (operator), viewer_traffic (viewer)"
  echo "STAFF_LOGIN password: \$(grep '^MUNICIPAL_PASSWORD=' \$ENV | cut -d'"' -f2)"
fi
EOF
)
B64=$(printf '%s' "$REMOTE" | base64 -w0)

CMD=$(aws ssm send-command --region "$REGION" --instance-ids "$ID" \
  --document-name AWS-RunShellScript --comment "JalMarg switch to $MODE" \
  --parameters "commands=[\"echo $B64 | base64 -d | bash\"]" \
  --query Command.CommandId --output text)
echo "Switching... (about 1-2 minutes)"
for i in $(seq 1 60); do
  sleep 5
  ST=$(aws ssm get-command-invocation --region "$REGION" --command-id "$CMD" --instance-id "$ID" --query Status --output text 2>/dev/null || echo Pending)
  case "$ST" in Success|Failed|Cancelled|TimedOut) break ;; esac
done
echo "Status: $ST"
aws ssm get-command-invocation --region "$REGION" --command-id "$CMD" --instance-id "$ID" \
  --query StandardOutputContent --output text | grep -E '^\{|STAFF_LOGIN|updated' || true
[ "$ST" = Success ] || aws ssm get-command-invocation --region "$REGION" --command-id "$CMD" --instance-id "$ID" --query StandardErrorContent --output text | tail -20
echo
echo "Open: $URL"
