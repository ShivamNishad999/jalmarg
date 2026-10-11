#!/bin/bash
# JalMarg one-step deploy. Run inside AWS CloudShell (it is already signed in as you; no keys needed):
#   curl -fsSL https://raw.githubusercontent.com/ShivamNishad999/jalmarg/feat/android-app/infra/deploy-cloudshell.sh -o deploy.sh && bash deploy.sh
# Passwords are typed here (hidden) and go straight to CloudFormation as NoEcho parameters.
set -euo pipefail

REGION=ap-south-1
STACK=jalmarg
RAW=https://raw.githubusercontent.com/ShivamNishad999/jalmarg/feat/android-app/infra/jalmarg-aws.yaml

say() { printf '\n\033[1;36m%s\033[0m\n' "$*"; }

say "JalMarg deploy to AWS ($REGION)"
aws sts get-caller-identity --query Account --output text >/dev/null || { echo "CloudShell is not signed in."; exit 1; }

curl -fsSL "$RAW" -o jalmarg-aws.yaml

# Admin account
read -rp "Admin email (you sign in with this): " ADMIN_EMAIL
while true; do
  read -rsp "Admin password (10+ chars, letters+digits, no quotes/\\/\$/\`): " ADMIN_PASSWORD; echo
  [[ ${#ADMIN_PASSWORD} -ge 10 && ! "$ADMIN_PASSWORD" =~ [\"\\\$\`] ]] && break
  echo "  Too short or has a forbidden character, try again."
done

# Email for sign-up codes
read -rp "Gmail address for sending sign-up codes (Enter to skip): " MAIL_USER
MAIL_PASS=""
if [[ -n "$MAIL_USER" ]]; then
  read -rsp "Gmail APP password (16 letters, spaces are removed automatically): " MAIL_PASS; echo
  MAIL_PASS="${MAIL_PASS// /}"
fi

ALERT_KEY=$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 32)

# Nova Lite: use the in-region model if Mumbai offers it on-demand, else the APAC inference profile
MODEL=apac.amazon.nova-lite-v1:0
if aws bedrock list-foundation-models --region "$REGION" --by-provider amazon \
     --query "modelSummaries[?modelId=='amazon.nova-lite-v1:0'].inferenceTypesSupported[]" --output text 2>/dev/null | grep -q ON_DEMAND; then
  MODEL=amazon.nova-lite-v1:0
fi
say "Bedrock model: $MODEL"

say "Creating everything (EC2, CloudFront, S3, SNS, EventBridge). This takes 15-20 minutes..."
aws cloudformation deploy --region "$REGION" --stack-name "$STACK" \
  --template-file jalmarg-aws.yaml \
  --capabilities CAPABILITY_IAM \
  --no-fail-on-empty-changeset \
  --parameter-overrides \
    "AdminEmail=$ADMIN_EMAIL" "AdminPassword=$ADMIN_PASSWORD" \
    "MailUsername=$MAIL_USER" "MailPassword=$MAIL_PASS" "MailFrom=$MAIL_USER" \
    "AlertRunKey=$ALERT_KEY" "BedrockModelId=$MODEL" \
  || {
    echo
    echo "Deploy failed. The reason:"
    aws cloudformation describe-stack-events --region "$REGION" --stack-name "$STACK" \
      --query "StackEvents[?contains(ResourceStatus,'FAILED')].[LogicalResourceId,ResourceStatusReason]" --output table | head -30
    exit 1
  }

say "Done. Your links:"
aws cloudformation describe-stacks --region "$REGION" --stack-name "$STACK" \
  --query "Stacks[0].Outputs[].[OutputKey,OutputValue]" --output table
URL=$(aws cloudformation describe-stacks --region "$REGION" --stack-name "$STACK" --query "Stacks[0].Outputs[?OutputKey=='AppUrl'].OutputValue" --output text)
echo
echo "Health check:"; curl -s "$URL/api/health"; echo
echo
echo "Open $URL in a browser, and put the same address in the Android app (Profile > Server address)."
