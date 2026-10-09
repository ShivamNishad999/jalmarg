import { createContext, useContext, useEffect, useState } from 'react'

// English + Hindi strings. Keep keys short and grouped by screen.
const STRINGS = {
  en: {
    appTagline: 'Safe routes through the monsoon',
    nav_map: 'Live map', nav_report: 'Report', nav_route: 'Safe route', nav_alerts: 'Alerts',
    nav_authority: 'Authority', nav_about: 'About',
    // map
    liveNow: 'flooded spots right now',
    nearYou: 'Near you', details: 'Details',
    noAlerts: 'No waterlogging reported within 3 km. Roads look clear.',
    reportCta: 'Report waterlogging',
    stillFlooded: 'Still flooded', cleared: 'Water cleared',
    thanks: 'Thanks! Updated.',
    depth: 'Depth', ft: 'ft',
    walk: 'Walk', bike: 'Bike', car: 'Car',
    safe: 'OK', unsafe: 'Unsafe',
    confirmedBy: 'confirmed by', people: 'people',
    reported: 'Reported', ago: 'ago',
    demoTag: 'demo data',
    aiEstimate: 'AI estimate', citizenEstimate: 'Reporter estimate', bothEstimate: 'AI + reporter',
    rain_HEAVY: 'Heavy rain expected in the next 6 hours',
    rain_VERY_HEAVY: 'Very heavy rain expected in the next 6 hours',
    locating: 'Finding your location…',
    // report
    reportTitle: 'Report a flooded road',
    reportSub: 'Takes 20 seconds. Your report warns everyone heading this way.',
    step1: 'Where is it?', step2: 'Take a photo', step3: 'How deep is it?',
    useMyLocation: 'Use my location', dragPin: 'Drag the pin or tap the map to adjust',
    placeName: 'Place name (e.g. Sector 62 underpass)',
    takePhoto: 'Open camera', changePhoto: 'Retake photo',
    photoHint: 'Include a bike, car tyre or kerb in the frame so AI can judge depth.',
    lvl_PATCHY: 'Puddles', lvl_PATCHY_d: 'Road visible',
    lvl_ANKLE: 'Ankle', lvl_ANKLE_d: 'Below bike silencer',
    lvl_SHIN: 'Mid-shin', lvl_SHIN_d: 'Half a bike tyre',
    lvl_KNEE: 'Knee', lvl_KNEE_d: 'Bike engine under water',
    lvl_WAIST: 'Waist+', lvl_WAIST_d: 'Cars stalling',
    depthOptional: 'Optional if you add a photo — AI will estimate.',
    note: 'Anything else? (open drain, stuck vehicles…)',
    submit: 'Send report', sending: 'Analysing photo…',
    needPhotoOrDepth: 'Add a photo or pick a depth.',
    needLocation: 'We need the location first.',
    resultTitle: 'Report live on the map',
    resultSub: 'People nearby and on routes through here will now be warned.',
    shareWhatsapp: 'Share on WhatsApp', viewOnMap: 'View on map', reportAnother: 'Report another',
    // route
    routeTitle: 'Find a route that avoids flooding',
    from: 'From', to: 'To', myLocation: 'My location', searchPlace: 'Search a place…',
    travelBy: 'Travelling by', findRoute: 'Find safe route', finding: 'Checking routes…',
    recommended: 'Recommended', fastest: 'Fastest', avoids: 'Avoids all reported waterlogging',
    passes: 'Passes', floodedSpots: 'flooded spot(s)', min: 'min', km: 'km',
    noSafe: 'Every route we found crosses waterlogging. Least risky one shown — go slow or wait.',
    openInMaps: 'Navigate in Google Maps',
    // alerts
    alertsTitle: 'Get flood alerts by email',
    alertsSub: 'We check rain forecasts and live reports every hour and email you when a spot near your place floods.',
    yourName: 'Your name', email: 'Email', label: 'Place label', labelHome: 'Home', labelCollege: 'College', labelOffice: 'Office',
    radius: 'Alert radius', language: 'Alert language',
    setFromGps: 'Use my current location', tapMapToSet: 'or tap the map to set the place',
    subscribe: 'Turn on alerts', subscribed: 'Alerts are on!',
    confirmEmail: 'Check your inbox and click the AWS confirmation link to start receiving emails.',
    mySubs: 'My alerts', lookup: 'Show my alerts', remove: 'Remove',
    // authority
    authTitle: 'Authority dashboard',
    authSub: 'Where does the city flood again and again? Evidence for a permanent fix.',
    allAreas: 'All of NCR', totalReports: 'Total reports', activeNow: 'Flooded now', hotspots: 'Hotspots', last7: 'Last 7 days',
    perDay: 'Reports per day (last 30 days)', bySeverity: 'By severity', topHotspots: 'Worst hotspots',
    downloadPdf: 'Download complaint PDF', pdf: 'PDF',
    location: 'Location', area: 'Area', reports: 'Reports', last30: '30 days', maxDepth: 'Max depth', lastSeen: 'Last flooded',
    sev_LOW: 'Low', sev_MODERATE: 'Moderate', sev_HIGH: 'High', sev_SEVERE: 'Severe',
    loading: 'Loading…', error: 'Something went wrong'
  },
  hi: {
    appTagline: 'बारिश में सुरक्षित रास्ता',
    nav_map: 'लाइव मैप', nav_report: 'रिपोर्ट', nav_route: 'सुरक्षित रास्ता', nav_alerts: 'अलर्ट',
    nav_authority: 'प्राधिकरण', nav_about: 'जानकारी',
    liveNow: 'जगह अभी पानी में डूबी हैं',
    nearYou: 'आपके पास', details: 'पूरी जानकारी',
    noAlerts: '3 km के अंदर कोई जलभराव नहीं। रास्ते साफ़ हैं।',
    reportCta: 'जलभराव रिपोर्ट करें',
    stillFlooded: 'अभी भी पानी है', cleared: 'पानी हट गया',
    thanks: 'धन्यवाद! अपडेट हो गया।',
    depth: 'गहराई', ft: 'फीट',
    walk: 'पैदल', bike: 'बाइक', car: 'कार',
    safe: 'ठीक', unsafe: 'खतरा',
    confirmedBy: 'पुष्टि की', people: 'लोगों ने',
    reported: 'रिपोर्ट', ago: 'पहले',
    demoTag: 'डेमो डेटा',
    aiEstimate: 'AI अनुमान', citizenEstimate: 'रिपोर्टर का अनुमान', bothEstimate: 'AI + रिपोर्टर',
    rain_HEAVY: 'अगले 6 घंटे में भारी बारिश की संभावना',
    rain_VERY_HEAVY: 'अगले 6 घंटे में बहुत भारी बारिश की संभावना',
    locating: 'आपकी लोकेशन ढूंढ रहे हैं…',
    reportTitle: 'डूबी सड़क की रिपोर्ट करें',
    reportSub: 'सिर्फ 20 सेकंड। आपकी रिपोर्ट इस रास्ते पर आने वाले सबको सावधान करेगी।',
    step1: 'जगह कहाँ है?', step2: 'फोटो लें', step3: 'पानी कितना गहरा है?',
    useMyLocation: 'मेरी लोकेशन', dragPin: 'पिन खिसकाएँ या मैप पर टैप करें',
    placeName: 'जगह का नाम (जैसे सेक्टर 62 अंडरपास)',
    takePhoto: 'कैमरा खोलें', changePhoto: 'फिर से फोटो लें',
    photoHint: 'फोटो में बाइक, कार का टायर या फुटपाथ दिखे तो AI गहराई सही बताएगा।',
    lvl_PATCHY: 'छोटे गड्ढे', lvl_PATCHY_d: 'सड़क दिख रही है',
    lvl_ANKLE: 'टखने तक', lvl_ANKLE_d: 'साइलेंसर से नीचे',
    lvl_SHIN: 'पिंडली तक', lvl_SHIN_d: 'आधा बाइक टायर',
    lvl_KNEE: 'घुटने तक', lvl_KNEE_d: 'बाइक का इंजन डूबा',
    lvl_WAIST: 'कमर तक+', lvl_WAIST_d: 'कारें बंद हो रही हैं',
    depthOptional: 'फोटो डालें तो ज़रूरी नहीं — AI अनुमान लगाएगा।',
    note: 'कुछ और? (खुला नाला, फँसी गाड़ियाँ…)',
    submit: 'रिपोर्ट भेजें', sending: 'फोटो जाँच रहे हैं…',
    needPhotoOrDepth: 'फोटो डालें या गहराई चुनें।',
    needLocation: 'पहले लोकेशन चाहिए।',
    resultTitle: 'रिपोर्ट मैप पर लाइव है',
    resultSub: 'आस-पास और इस रास्ते से जाने वालों को अब चेतावनी मिलेगी।',
    shareWhatsapp: 'WhatsApp पर भेजें', viewOnMap: 'मैप पर देखें', reportAnother: 'एक और रिपोर्ट',
    routeTitle: 'जलभराव से बचने वाला रास्ता',
    from: 'कहाँ से', to: 'कहाँ तक', myLocation: 'मेरी लोकेशन', searchPlace: 'जगह खोजें…',
    travelBy: 'कैसे जा रहे हैं', findRoute: 'सुरक्षित रास्ता खोजें', finding: 'रास्ते जाँच रहे हैं…',
    recommended: 'सुझाया गया', fastest: 'सबसे तेज़', avoids: 'सभी रिपोर्ट किए जलभराव से बचता है',
    passes: 'रास्ते में', floodedSpots: 'डूबी जगह', min: 'मिनट', km: 'km',
    noSafe: 'हर रास्ते में जलभराव है। सबसे कम खतरे वाला दिखाया है — धीरे चलें या रुकें।',
    openInMaps: 'Google Maps में नेविगेट करें',
    alertsTitle: 'ईमेल पर बाढ़ अलर्ट पाएँ',
    alertsSub: 'हम हर घंटे बारिश का अनुमान और लाइव रिपोर्ट देखते हैं और आपकी जगह के पास पानी भरने पर ईमेल करते हैं।',
    yourName: 'आपका नाम', email: 'ईमेल', label: 'जगह', labelHome: 'घर', labelCollege: 'कॉलेज', labelOffice: 'ऑफिस',
    radius: 'अलर्ट दूरी', language: 'अलर्ट की भाषा',
    setFromGps: 'मेरी अभी की लोकेशन', tapMapToSet: 'या मैप पर टैप करके जगह चुनें',
    subscribe: 'अलर्ट चालू करें', subscribed: 'अलर्ट चालू हैं!',
    confirmEmail: 'अपना इनबॉक्स देखें और AWS की पुष्टि वाले लिंक पर क्लिक करें।',
    mySubs: 'मेरे अलर्ट', lookup: 'मेरे अलर्ट दिखाएँ', remove: 'हटाएँ',
    authTitle: 'प्राधिकरण डैशबोर्ड',
    authSub: 'शहर में बार-बार पानी कहाँ भरता है? स्थायी समाधान के लिए सबूत।',
    allAreas: 'पूरा NCR', totalReports: 'कुल रिपोर्ट', activeNow: 'अभी डूबे', hotspots: 'हॉटस्पॉट', last7: 'पिछले 7 दिन',
    perDay: 'रोज़ की रिपोर्ट (पिछले 30 दिन)', bySeverity: 'गंभीरता', topHotspots: 'सबसे खराब जगहें',
    downloadPdf: 'शिकायत PDF डाउनलोड करें', pdf: 'PDF',
    location: 'जगह', area: 'क्षेत्र', reports: 'रिपोर्ट', last30: '30 दिन', maxDepth: 'अधिकतम गहराई', lastSeen: 'आखिरी बार',
    sev_LOW: 'कम', sev_MODERATE: 'मध्यम', sev_HIGH: 'ज़्यादा', sev_SEVERE: 'गंभीर',
    loading: 'लोड हो रहा है…', error: 'कुछ गड़बड़ हुई'
  }
}

const LangContext = createContext({ lang: 'en', setLang: () => {}, t: (k) => k })

export function LangProvider({ children }) {
  const [lang, setLang] = useState(() => {
    try {
      return localStorage.getItem('jalmarg-lang') || (navigator.language?.startsWith('hi') ? 'hi' : 'en')
    } catch {
      return 'en'
    }
  })
  useEffect(() => {
    try { localStorage.setItem('jalmarg-lang', lang) } catch { /* private mode */ }
    document.documentElement.lang = lang
  }, [lang])
  const t = (key) => STRINGS[lang][key] ?? STRINGS.en[key] ?? key
  return <LangContext.Provider value={{ lang, setLang, t }}>{children}</LangContext.Provider>
}

export const useLang = () => useContext(LangContext)
