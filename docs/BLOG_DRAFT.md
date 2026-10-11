# Blog draft (AWS Builder Center) — edit in your own words before publishing

**Title:** How we built JalMarg: turning one photo of a flooded road into a live warning, with Amazon Bedrock

**Tags:** bedrock, corretto, java, spring-boot, hackathon, sustainability

## The problem
Every monsoon, the same underpasses in Delhi NCR flood. There is no live, trustworthy answer to a simple question: *how deep is it right now, and can I get through on a scooter?* People find out by stalling in it. Complaints to the city are scattered, one-off, and easy to ignore.

## What we built
JalMarg is an installable web app (PWA) with four parts:
1. **Report** — a photo + GPS from someone at the spot.
2. **Measure** — Amazon Bedrock (Amazon Nova Lite, a vision model) estimates water depth using reference objects in the photo (tyres, kerbs, legs) and returns strict JSON: depth, confidence, and a one-line warning in English and Hindi. We blend it with the reporter's own estimate when the model is unsure.
3. **Route** — we take up to three alternative routes from OSRM, check them against live reports, and if all are blocked we generate detour waypoints around the worst spot. The ranking depends on the vehicle: 0.5 ft blocks a scooter, 0.8 ft a car.
4. **Fix** — reports cluster into hotspots (250 m). The authority dashboard ranks them and produces a complaint PDF addressed to the right civic body.

## Architecture
Spring Boot 3 on **Amazon Corretto 21** (EC2 + CloudFront), **S3** for photos, **RDS MySQL**, **Bedrock** Converse API, **SNS** for alerts (one topic, a per-user filter policy so one publish reaches one person), **EventBridge** to trigger the hourly check, **Amplify Hosting** for the PWA.

## What fought back
- *Getting a number, not an essay, from a vision model.* Asking for strict JSON with named reference objects and clamping the result made depth estimates stable.
- *SNS email subjects must be ASCII,* so Hindi lives in the body only.
- *Mixed content:* an HTTPS frontend can't call an HTTP EC2 API — CloudFront in front fixed it in minutes.
- *Stale reports:* floods drain. Reports expire after 6 hours unless people confirm them.

## What's next
WhatsApp bot reporting, SMS alerts (needs DLT registration in India), and sharing hotspot data with civic bodies before the next monsoon.

Repo: <link> · Demo: <link> · Team Growforge
