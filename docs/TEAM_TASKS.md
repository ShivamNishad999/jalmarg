# Team Growforge — who does what

Core app is built and in this repo. Remaining work is **deploy, test with real photos, polish, video, submit**.
Internal deadline: **Sunday 4 PM** (hackathon ends Sunday evening; submissions open Sunday 8 AM).

## Shivam Nishad (lead) — backend + integration + video
- [ ] Run backend locally (`mvn spring-boot:run`) and frontend (`npm run dev`), click through every screen
- [ ] Review / merge PRs; keep `main` always deployable
- [ ] Test 10+ real waterlogging photos with `AI_ENABLED=true`, tune the prompt in `AiDepthService` if depths look off
- [ ] Record + edit the 3-minute demo video (`docs/DEMO_VIDEO_SCRIPT.md`)
- [ ] Fill the submission form

## Vijendra Pratap Singh — AWS
- [ ] Follow `docs/DEPLOY_AWS.md`: Bedrock access → S3 → SNS → IAM role → EC2 (Corretto) → CloudFront → Amplify → EventBridge
- [ ] Share the live frontend URL and API URL in the team group
- [ ] Screenshot each AWS console page used (for video + blog)
- [ ] Architecture diagram image for the video (README Mermaid → export)

## Tanya Shukla — frontend polish + Hindi
- [ ] Test on 2–3 real phones: install as app (Add to Home screen), camera, GPS
- [ ] Proof-read every Hindi string in `frontend/src/i18n.jsx`
- [ ] Fix any layout issues on small screens
- [ ] Make the "report → result" moment look great for the video

## Manas Pandey — routes, data, docs
- [ ] Test Safe Route with real NCR trips (e.g. NIET → Pari Chowk, Sector 62 → Connaught Place) while demo floods are live
- [ ] Check seeded hotspot coordinates in `SeedService` sit on the right road; fix if off
- [ ] Download the complaint PDF for each area; check it reads well
- [ ] Write and publish the AWS Builder Center blog (`docs/BLOG_DRAFT.md`) and add the link to the submission
- [ ] Everyone: verify student status on AWS Builder Center (required)

## Sync
- 15-min call at 1 PM and 10 PM. Say: done / doing / blocked.
- Branches: `feat/<name>-<thing>` → PR → Shivam merges.
