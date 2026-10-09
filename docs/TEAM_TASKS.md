# Team Growforge — who does what

Internal deadline: **Sunday 4 PM** (submissions open Sunday 8 AM). Work on `feat/<name>-<thing>` branches; Shivam merges.
Sync calls: 1 PM and 10 PM, 15 minutes.

## Shivam Nishad (lead) — Android app + video
- [ ] Android app with Capacitor from `frontend/` (branch `feat/android-app`): camera, GPS, app icon, Server URL setting
- [ ] APK is built by GitHub Actions (`.github/workflows/android.yml`) and published on the `app-latest` release
- [ ] Test on phone: photo report, live map, safe route
- [ ] Record the 3-minute demo video (`docs/DEMO_VIDEO_SCRIPT.md`) and submit

## Vijendra Pratap Singh — Backend
- [ ] Run backend locally, test every API in `docs/API.md`
- [ ] Once AWS keys exist: `AI_ENABLED=true`, test 10 real flood photos, tune the prompt in `AiDepthService`
- [ ] Fix backend bugs

## Tanya Shukla — Web frontend
- [ ] Check every screen on desktop and phone browser; fix layout issues
- [ ] Proof-read Hindi in `frontend/src/i18n.jsx`
- [ ] Polish authority dashboard and complaint PDF for the demo

## Manas Pandey — Deploy + PPT
- [ ] Deploy with `docs/DEPLOY_AWS.md`: EC2 backend, CloudFront (HTTPS), Amplify web, S3, SNS
- [ ] Share the backend HTTPS URL with the team first (app + web both need it)
- [ ] PPT: problem → solution → how it works → AWS services → impact → team
- [ ] If time: AWS Builder Center blog (`docs/BLOG_DRAFT.md`)

## Everyone
- [ ] Verify student status on AWS Builder Center (required)
