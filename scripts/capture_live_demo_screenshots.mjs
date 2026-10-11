import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const FRONTEND_BASE = 'http://127.0.0.1:5173'
const BACKEND_BASE = 'http://127.0.0.1:8080'
const SCREENSHOT_DIR = 'D:\\jalmarg\\jalmarg\\docs\\demo_screenshots'

if (!fs.existsSync(SCREENSHOT_DIR)) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true })
}

const viewports = [
  { name: 'desktop_1366x768', width: 1366, height: 768 },
  { name: 'tablet_768x1024', width: 768, height: 1024 },
  { name: 'mobile_390x844', width: 390, height: 844 },
  { name: 'mobile_360x800', width: 360, height: 800 }
]

const pages = [
  {
    route: '/',
    id: 'home_map',
    title: 'Live Digital Twin Map & Corridor Search',
    expectedMarkers: ['JalMarg', 'map-full', 'place-search']
  },
  {
    route: '/report',
    id: 'citizen_reporting',
    title: 'Citizen Waterlogging Report Form',
    expectedMarkers: ['Report Waterlogging', 'level-person', 'depth-option']
  },
  {
    route: '/route',
    id: 'route_planner',
    title: 'Hazard-Aware Route Planner',
    expectedMarkers: ['Hazard-Aware Route Planner', 'Travel Mode', 'origin-input']
  },
  {
    route: '/authority',
    id: 'authority_console',
    title: 'Municipal Authority Console & Review Queue',
    expectedMarkers: ['Municipal Authority Waterlogging Dashboard', 'Municipal Officer Authentication', 'Review Queue']
  }
]

console.log('================================================================')
console.log('JALMARG LIVE BROWSER DEMO AUTOMATION & SCREENSHOT CAPTURE')
console.log('Target Frontend:', FRONTEND_BASE)
console.log('Target Backend: ', BACKEND_BASE)
console.log('Chrome Binary:  ', CHROME_PATH)
console.log('================================================================')

const auditResults = []

for (const page of pages) {
  const pageUrl = `${FRONTEND_BASE}${page.route}`
  console.log(`\nTesting Page: ${page.title} (${pageUrl})`)

  // 1. Verify DOM Rendering via local Chrome
  try {
    const domOutput = execFileSync(
      CHROME_PATH,
      ['--headless=new', '--disable-gpu', '--no-sandbox', '--dump-dom', pageUrl],
      { encoding: 'utf-8', timeout: 15000, maxBuffer: 15 * 1024 * 1024 }
    )

    const markerChecks = {}
    let allMarkersPresent = true
    for (const marker of page.expectedMarkers) {
      const found = domOutput.includes(marker)
      markerChecks[marker] = found
      if (!found) allMarkersPresent = false
    }

    console.log(`  DOM Check: ${allMarkersPresent ? 'PASSED' : 'PARTIAL'}`)
    for (const [m, ok] of Object.entries(markerChecks)) {
      console.log(`    - Marker [${m}]: ${ok ? 'FOUND' : 'MISSING'}`)
    }

    // 2. Capture Screenshots across all required viewports
    for (const vp of viewports) {
      const filename = `${page.id}_${vp.name}.png`
      const outPath = path.join(SCREENSHOT_DIR, filename)

      try {
        execFileSync(
          CHROME_PATH,
          [
            '--headless=new',
            '--disable-gpu',
            '--no-sandbox',
            `--window-size=${vp.width},${vp.height}`,
            `--screenshot=${outPath}`,
            pageUrl
          ],
          { timeout: 15000 }
        )

        if (fs.existsSync(outPath) && fs.statSync(outPath).size > 0) {
          const sizeKb = (fs.statSync(outPath).size / 1024).toFixed(1)
          console.log(`  Screenshot [${vp.name}]: CAPTURED -> ${filename} (${sizeKb} KB)`)
          auditResults.push({
            page: page.id,
            route: page.route,
            viewport: vp.name,
            dimensions: `${vp.width}x${vp.height}`,
            screenshot: filename,
            fileSizeKb: sizeKb,
            domMarkersVerified: allMarkersPresent,
            status: 'SUCCESS'
          })
        } else {
          console.log(`  Screenshot [${vp.name}]: EMPTY/MISSING`)
        }
      } catch (err) {
        console.error(`  Screenshot [${vp.name}] error: ${err.message}`)
      }
    }

  } catch (domErr) {
    console.error(`  DOM Fetch error for ${pageUrl}: ${domErr.message}`)
  }
}

// 3. Output JSON verification manifest
const manifestPath = path.join(SCREENSHOT_DIR, 'demo_visual_validation_manifest.json')
fs.writeFileSync(manifestPath, JSON.stringify(auditResults, null, 2), 'utf-8')
console.log(`\nAudit manifest written to ${manifestPath}`)
console.log(`Total verified visual artifacts: ${auditResults.length}`)
