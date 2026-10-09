# Deploying JalMarg on AWS (Free Tier, ~45 minutes)

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

## 4. Amazon RDS — MySQL (optional)
*Easy create* → MySQL → **Free tier** → `db.t4g.micro`, DB name `jalmarg`. Put it in the same VPC as the EC2 instance and allow port 3306 from the EC2 security group.
`DB_URL=jdbc:mysql://<endpoint>:3306/jalmarg?createDatabaseIfNotExist=true`

## 5. IAM role for the backend
IAM → Roles → *Create* → trusted entity **EC2** → create an inline policy from [`infra/iam-policy.json`](../infra/iam-policy.json) (replace bucket name and account id). Name it `jalmarg-backend-role`.

## 6. EC2 — run the Spring Boot API on Amazon Corretto
1. Launch instance: **Amazon Linux 2023**, `t3.micro` (free tier), attach `jalmarg-backend-role`, security group inbound **TCP 8091** from anywhere (CloudFront needs it) + SSH from your IP.
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
CORS_ORIGINS=https://*.amplifyapp.com,https://*.cloudfront.net,http://localhost:5173
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
curl localhost:8091/api/meta      # {"aiEnabled":true,...}
journalctl -u jalmarg -f          # logs
```
> t3.micro has 1 GB RAM. If Maven runs out of memory while building, build the jar on a laptop and `scp` it to the instance.

## 7. CloudFront — HTTPS in front of EC2
The frontend is HTTPS, so the API must be too.
CloudFront → *Create distribution* → Origin domain: the EC2 **public DNS**, protocol **HTTP only**, port **8091** → Viewer protocol: *Redirect HTTP to HTTPS* → Allowed methods: **GET, HEAD, OPTIONS, PUT, POST, PATCH, DELETE** → Cache policy **CachingDisabled** → Origin request policy **AllViewerExceptHostHeader** → Create. The API URL is `https://dxxxx.cloudfront.net`.

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
