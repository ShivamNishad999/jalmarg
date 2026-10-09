# Demo video — 3 minutes

Judges see only this video. Show: **what it does, who it is for, where AWS fits.** Record phone screens with a screen recorder; desktop with OBS. Voice-over in English (Hindi words where natural). Run `/api/admin/demo-refresh` right before recording.

| Time | Screen | Voice-over |
|---|---|---|
| 0:00–0:15 | News clips/photos of a flooded underpass (Minto Bridge style), a scooter stalled in water | "Every monsoon in Delhi NCR, the same underpasses fill with water. You only find out how deep it is when your scooter dies halfway through." |
| 0:15–0:25 | JalMarg logo, phone home screen with the installed app | "JalMarg turns one photo into a live warning for everyone heading that way." |
| 0:25–1:05 | **Phone:** tap *Report* → GPS pin → take photo of water with a bike in frame → *Send* → spinner → result card (1.4 ft, bike unsafe, car unsafe, AI sentence in Hindi) | "I'm at a flooded road. Location is automatic. I take a photo — the bike in the frame matters. Amazon Bedrock's Nova vision model compares the water with the tyre and estimates 1.4 feet. Plain advice: not safe on a two-wheeler, not safe in a car." |
| 1:05–1:20 | Tap *Share on WhatsApp*, then *View on map*: red pulsing marker, other live spots, "Still flooded" button | "One tap to warn my family group. The spot is now live on the map. People passing by keep it alive — or mark it cleared." |
| 1:20–1:50 | **Safe route:** NIET → Sector 62, mode 🛵. Fastest route crosses the NH-9 underpass (red), recommended route goes around (blue). Tap *Navigate in Google Maps* | "Riya rides a scooter to college. The fastest route goes through a flooded underpass. JalMarg checks every route against live reports for *her* vehicle and sends her around it." |
| 1:50–2:05 | **Alerts:** subscribe Home, 3 km, Hindi. Show the SNS email in Gmail | "Every hour, EventBridge triggers a check of rain forecasts and nearby reports. Amazon SNS emails only the people near the trouble." |
| 2:05–2:35 | **Authority dashboard** on laptop: KPIs, hotspot table, click *Download complaint PDF* → PDF addressed to Noida Authority | "Flooding that repeats is a drainage problem. JalMarg ranks the spots that flood again and again and generates a complaint PDF for MCD, Noida Authority or GMDA — so residents stop complaining one by one and hand the city evidence." |
| 2:35–2:52 | Architecture diagram (README) with AWS icons highlighted | "Spring Boot on Amazon Corretto, running on EC2 behind CloudFront. Photos in S3, data in RDS, AI on Bedrock, alerts with SNS and EventBridge, the PWA on Amplify." |
| 2:52–3:00 | Live map in Hindi, team names | "JalMarg — बारिश में सुरक्षित रास्ता. Team Growforge." |

**Tips:** keep each screen ≥ 3 s, zoom into the result card, no dead air, add captions, export 1080p, stay under 3:00.
