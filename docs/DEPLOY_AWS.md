# Deploying JalMarg on AWS

## Fast path: one CloudFormation stack (about 20 minutes, mostly waiting)

Everything is created by `infra/jalmarg-aws.yaml`. No AWS access keys are needed anywhere:
the server gets permissions from an IAM role, and secrets are typed only into the CloudFormation form.

**What it creates (Mumbai, ap-south-1):** EC2 t3.micro (Amazon Linux 2023 + Amazon Corretto 21) running the
API and the web app, Elastic IP, CloudFront (HTTPS), private S3 bucket for photos, SNS topic for alerts,
EventBridge hourly alert run, IAM role for Bedrock/S3/SNS, and the Copernicus DEM tile from AWS Open Data.

1. **Bedrock model access** — Console → region **Asia Pacific (Mumbai)** → Amazon Bedrock → *Model access* →
   enable **Amazon Nova Lite**. (Skip and set *Use Amazon Bedrock* to `false` if you cannot; the app then uses the citizen's depth.)
2. **Email for sign-up codes** — Gmail: Google Account → Security → 2-Step Verification ON →
   <https://myaccount.google.com/apppasswords> → create "JalMarg" → copy the 16 letters (no spaces).
   Never use your normal Google password.
3. **Create the stack** — Console → CloudFormation (Mumbai) → *Create stack* → *With new resources* →
   *Upload a template file* → `infra/jalmarg-aws.yaml` → stack name `jalmarg` → fill in:
   - Admin email / password (this account can sign in at once and make authority users)
   - SMTP: host `smtp.gmail.com`, port `587`, username = your Gmail, password = the app password, From = your Gmail
   - Alert run key: any 16+ random letters/digits
   → Next → Next → tick *I acknowledge that AWS CloudFormation might create IAM resources* → **Submit**.
4. Wait for **CREATE_COMPLETE** (15–20 min; CloudFront is the slow part). Open the **Outputs** tab:
   - **AppUrl** — the website. Put the same URL in the Android app (Profile → Server address → Save & test).
   - **HealthCheck** — should show `"database":"UP"`.
5. **Optional, APK with the address built in:** GitHub repo → Settings → Secrets and variables → Actions →
   *Variables* → `API_BASE` = the AppUrl. The next APK build starts with that server.

**Update after new code:** push to GitHub, wait for the *Server build (AWS)* workflow, then EC2 → instance →
*Connect* → *Session Manager* → `sudo /opt/jalmarg/update.sh`.

**Logs:** Session Manager → `sudo journalctl -u jalmarg -n 200` (setup log: `/var/log/jalmarg-setup.log`).

**If the stack fails:** CloudFormation → *Events* shows the reason. Common ones: no default VPC in the account,
or the server could not start within 6 minutes (check the setup log by re-creating with *Preserve successfully provisioned resources*).

**Cost:** EC2 t3.micro, 16 GB disk and CloudFront are within the 12-month Free Tier; Bedrock Nova Lite costs a
fraction of a rupee per photo. Delete the stack after the event to stop all charges (the photo bucket is kept; empty and delete it by hand).

---

## Manual setup (reference)


Region: **Asia Pacific (Mumbai) `ap-south-1`** for everything unless noted.
Order: 1 Bedrock → 2 S3 → 3 SNS → 4 RDS (optional) → 5 IAM role → 6 EC2 backend → 7 CloudFront (HTTPS) → 8 Amplify frontend → 9 EventBridge.

> Fastest path if short on time: steps 1, 2, 5, 6, 7, 8. Keep H2 (skip RDS) and the in-app hourly scheduler (skip EventBridge). Everything still runs on AWS.

---

## 1. Amazon Bedrock — model access
1. Console → **Amazon Bedrock** → *Model access* → enable **Amazon Nova Lite**.
2. Model id to use: `apac.amazon.nova-lite-v1:0` (cross-region inference profile in ap-south-1).
   If Mumbai gives an access error, use `BEDROCK_REGION=us-east-1` and `BEDROCK_MODEL_ID=us.amazon.nova-lite-v1:0`.
3. Quick test in *Playgrounds → Chat* with a flood photo.

## 2. Amazon S3 — photos
Create bucket `jalmarg-photos-<yourname>` (Block all public access **ON** — photos are served through the API).

## 3. Amazon SNS — email alerts
Create a **Standard** topic `jalmarg-alerts`. Copy its ARN. (No manual subscriptions: the app subscribes each user's email with a filter policy; users click the AWS confirmation email once.)

## 3b. Amazon SES — sign-up / reset codes (email OTP)
1. SES console (ap-south-1) → **Identities → Create identity** → verify your sender email (or domain).
2. New SES accounts are in the *sandbox*: you can only send to verified addresses. For the demo, verify each
   teammate's / judge's email too, or request production access.
3. **SMTP settings → Create SMTP credentials** → this creates an IAM user and shows a username + password once.
4. On the server set:
   `MAIL_HOST=email-smtp.ap-south-1.amazonaws.com MAIL_PORT=587 MAIL_USERNAME=<smtp user> MAIL_PASSWORD=<smtp password> MAIL_FROM="JalMarg <you@verified-address>"`
5. Check `GET /api/health` → `"email": "smtp"`.
Quick alternative for testing: a Gmail **App Password** (`MAIL_HOST=smtp.gmail.com`, port 587, your Gmail + 16-char app password).
Never set `JALMARG_DEV_OTP=true` on a public server — it prints codes to the log.
To make someone an authority officer: set `ADMIN_EMAIL`/`ADMIN_PASSWORD` once, sign in as admin, then
`POST /api/admin/users/role {"email":"officer@...","role":"AUTHORITY"}`.

## 4. Amazon RDS — MySQL (optional)
*Easy create* → MySQL → **Free tier** → `db.t4g.micro`, DB name `jalmarg`. Put it in the same VPC as the EC2 instance and allow port 3306 from the EC2 security group.
`DB_URL=jdbc:mysql://<endpoint>:3306/jalmarg?createDatabaseIfNotExist=true`

## 5. IAM role for the backend
IAM → Roles → *Create* → trusted entity **EC2** → create an inline policy from [`infra/iam-policy.json`](../infra/iam-policy.json) (replace bucket name and account id). Name it `jalmarg-backend-role`.

## 6. EC2 — run the Spring Boot API on Amazon Corretto
1. Launch instance: **Amazon Linux 2023**, `t3.micro` (free tier), attach `jalmarg-backend-role`, security group inbound **TCP 8080** from anywhere (CloudFront needs it) + SSH from your IP.
2. SSH in and run:
```bash
sudo dnf install -y java-21-amazon-corretto-devel maven git
git clone https://github.com/<owner>/<repo>.git && cd <repo>/backend
mvn -q -DskipTests package
sudo mkdir -p /opt/jalmarg && sudo cp target/jalmarg.jar /opt/jalmarg/
```
3. Create `/etc/jalmarg.env` (values from steps 1–4):
```bash
AWS_REGION=ap-south-1
AI_ENABLED=true
BEDROCK_MODEL_ID=apac.amazon.nova-lite-v1:0
S3_BUCKET=jalmarg-photos-yourname
SNS_TOPIC_ARN=arn:aws:sns:ap-south-1:123456789012:jalmarg-alerts
CORS_ORIGINS=https://*.amplifyapp.com,https://*.cloudfront.net,http://localhost:5173,https://localhost,capacitor://localhost
ALERT_RUN_KEY=<long-random-string>
# DB_URL=...  DB_USER=...  DB_PASSWORD=...
```
4. Systemd service `/etc/systemd/system/jalmarg.service`:
```ini
[Unit]
Description=JalMarg API
After=network.target

[Service]
EnvironmentFile=/etc/jalmarg.env
WorkingDirectory=/opt/jalmarg
ExecStart=/usr/bin/java -XX:MaxRAMPercentage=70 -jar /opt/jalmarg/jalmarg.jar
Restart=always
User=ec2-user

[Install]
WantedBy=multi-user.target
```
```bash
sudo chown -R ec2-user /opt/jalmarg
sudo systemctl daemon-reload && sudo systemctl enable --now jalmarg
curl localhost:8080/api/meta      # {"aiEnabled":true,...}
journalctl -u jalmarg -f          # logs
```
> t3.micro has 1 GB RAM. If Maven runs out of memory while building, build the jar on a laptop and `scp` it to the instance.

## 7. CloudFront — HTTPS in front of EC2
The frontend is HTTPS, so the API must be too.
CloudFront → *Create distribution* → Origin domain: the EC2 **public DNS**, protocol **HTTP only**, port **8080** → Viewer protocol: *Redirect HTTP to HTTPS* → Allowed methods: **GET, HEAD, OPTIONS, PUT, POST, PATCH, DELETE** → Cache policy **CachingDisabled** → Origin request policy **AllViewerExceptHostHeader** → Create. The API URL is `https://dxxxx.cloudfront.net`.

## 8. AWS Amplify Hosting — the PWA
Amplify → *Create new app* → GitHub → this repo, branch `main`. Amplify reads [`amplify.yml`](../amplify.yml) (app root `frontend`).
Environment variable: `VITE_API_BASE=https://dxxxx.cloudfront.net`.
Add rewrite *Rewrites and redirects*: source `</^[^.]+$|\.(?!(css|gif|ico|jpg|js|png|txt|svg|woff|woff2|ttf|map|json|webmanifest)$)([^.]+$)/>` → target `/index.html` → type `200 (Rewrite)` (for page refresh on /report, /route…).

## 9. Amazon EventBridge — hourly alert run
1. EventBridge → *API destinations* → *Create connection*: auth **API Key**, key name `X-Jalmarg-Key`, value = `ALERT_RUN_KEY`.
2. *Create API destination*: `https://dxxxx.cloudfront.net/api/alerts/run`, method **POST**.
3. *Rules* → *Create rule* → **Schedule** `rate(1 hour)` → target = the API destination.
4. Set `ALERT_INTERNAL_SCHEDULER=false` in `/etc/jalmarg.env` and restart, so the check runs only from EventBridge.

## 10. CloudWatch (optional, 5 min)
Install the CloudWatch agent or simply show `journalctl` logs in the demo. Bedrock and SNS metrics appear in CloudWatch automatically.

## Before recording the demo
```bash
curl -X POST -H "X-Jalmarg-Key: $ALERT_RUN_KEY" https://dxxxx.cloudfront.net/api/admin/demo-refresh
```

## Cost
Everything above fits in the Free Tier / new-account credits. Bedrock Nova Lite costs fractions of a cent per photo.
