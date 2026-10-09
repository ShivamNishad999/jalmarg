import { Link } from 'react-router-dom'
import { useLang } from '../i18n'

const STEPS = {
  en: [
    ['📷', 'Citizen Hazard Reporting', 'Commuters encountering a flooded road capture a photo in seconds. GPS coordinates are automatically recorded with manual fine-tuning available.'],
    ['🤖', 'AI Vision Depth Estimation', 'A vision model on Amazon Bedrock analyzes tyre submergence, kerb heights, and pedestrian cues to estimate water depth and vehicle passability.'],
    ['🗺️', 'Alternative Route Intelligence', 'Hazard spots go live immediately on the map, trigger localized alerts, and evaluate alternative road routes to help commuters avoid deep waterlogging.'],
    ['🏛️', 'Civic Evidence for Municipalities', 'Recurrent flooding spots are consolidated into municipal hotspots with one-click official complaint PDFs for MCD, Noida Authority, GMDA, and NHAI.']
  ],
  hi: [
    ['📷', 'नागरिक जलभराव रिपोर्टिंग', 'सड़क पर जलभराव देखने पर नागरिक तुरंत फोटो लेते हैं। GPS लोकेशन स्वतः दर्ज होती है जिसे मैप पर ठीक भी किया जा सकता है।'],
    ['🤖', 'AI विज़न गहराई अनुमान', 'Amazon Bedrock पर विज़न मॉडल वाहन के टायरों, फुटपाथ और आसपास के संदर्भ से पानी की गहराई व वाहन सुरक्षा का अनुमान लगाता है।'],
    ['🗺️', 'वैकल्पिक मार्ग व सतर्कता', 'जलभराव की सूचना तुरंत लाइव मैप पर दिखाई देती है, आस-पास के लोगों को अलर्ट मिलता है, और वैकल्पिक सुरक्षित मार्ग सुझाए जाते हैं।'],
    ['🏛️', 'प्रशासन हेतु आधिकारिक साक्ष्य', 'बार-बार डूबने वाले स्थानों का डेटा एकत्रित कर MCD, नोएडा प्राधिकरण, GMDA आदि हेतु एक क्लिक में शिकायत PDF तैयार होती है।']
  ]
}

const AWS_SERVICES = [
  ['Amazon Bedrock (Nova Vision)', 'Multimodal AI vision model for estimating water depth from citizen photos'],
  ['Amazon Corretto 21', 'Production Java runtime for Spring Boot backend services'],
  ['AWS App Runner / EC2', 'Scalable containerized hosting for backend APIs and routing logic'],
  ['Amazon S3', 'Secure object storage for citizen-uploaded waterlogging photos'],
  ['Amazon RDS (MySQL)', 'Relational database for incident reports, recurrent hotspots, and subscriptions'],
  ['Amazon SNS', 'Automated email alerts with per-user geo-radius filter policies'],
  ['Amazon EventBridge Scheduler', 'Scheduled hourly precipitation checks and flood monitoring tasks'],
  ['AWS Amplify Hosting', 'Fast, secure hosting for the progressive web application (PWA)'],
  ['Amazon CloudWatch', 'Centralized monitoring, error tracking, and health metrics']
]

export default function AboutPage() {
  const { lang } = useLang()
  const isHi = lang === 'hi'

  return (
    <div className="page narrow about">
      <p className="eyebrow">AWS Environmental Hackathon · Heat and Water Track</p>
      <h1 className="hero-title">
        {isHi
          ? 'हर मानसून सड़कें डूबती हैं। अब किसी को अंदाज़ा नहीं लगाना पड़ेगा।'
          : 'Every monsoon, urban roads drown. Nobody should have to navigate in the dark.'}
      </h1>

      <p className="lead">
        {isHi
          ? 'अंडरपास में पानी कितना गहरा है, यह तब पता चलता है जब गाड़ी बंद हो जाती है। JalMarg नागरिक रिपोर्टिंग और AI विज़न से डूबी सड़कों को लाइव मैप पर लाता है, जलभराव-जागरूक वैकल्पिक मार्ग बताता है, और नगर निगमों को स्थायी समाधान के लिए आधिकारिक साक्ष्य प्रदान करता है।'
          : 'In Delhi NCR and major urban centers, drivers often discover underpass water depth only when their vehicle stalls. JalMarg turns citizen photos into live, AI-assisted flood reports, ranks routes by reported hazard exposure, and equips municipal authorities with the evidence needed for permanent drainage fixes.'}
      </p>

      <div className="row gap wrap" style={{ margin: '20px 0 30px' }}>
        <Link to="/report" className="btn primary">
          📷 {isHi ? 'जलभराव रिपोर्ट करें' : 'Report Waterlogging'}
        </Link>
        <Link to="/" className="btn ghost">
          🗺️ {isHi ? 'लाइव मैप देखें' : 'View Live Map'}
        </Link>
        <Link to="/route" className="btn ghost">
          🧭 {isHi ? 'वैकल्पिक मार्ग खोजें' : 'Alternative Routes'}
        </Link>
      </div>

      <h2>{isHi ? 'प्रणाली कैसे कार्य करती है' : 'How JalMarg Works'}</h2>
      <ol className="how">
        {STEPS[lang].map(([icon, title, body]) => (
          <li key={title}>
            <span className="how-ico" aria-hidden>{icon}</span>
            <div>
              <b>{title}</b>
              <p>{body}</p>
            </div>
          </li>
        ))}
      </ol>

      <h2>{isHi ? 'AWS क्लाउड आर्किटेक्चर' : 'Built on AWS Cloud Architecture'}</h2>
      <div className="aws-grid">
        {AWS_SERVICES.map(([name, what]) => (
          <div key={name} className="aws-item">
            <b>{name}</b>
            <span>{what}</span>
          </div>
        ))}
      </div>

      <h2>{isHi ? 'सुरक्षा एवं वाहन नियम' : 'Hazard Thresholds & Vehicle Guidance'}</h2>
      <ul className="rules">
        <li>
          🛵 <b>{isHi ? 'दोपहिया वाहन (> 0.5 फीट):' : 'Two-Wheelers (> 0.5 ft):'}</b> {isHi ? 'साइलेंसर/इंजन में पानी जाने का गंभीर खतरा। मार्ग से बचें।' : 'High risk of exhaust water intake & engine failure. Avoid flooded stretch.'}
        </li>
        <li>
          🚗 <b>{isHi ? 'चार पहिया वाहन (> 0.8 फीट):' : 'Four-Wheelers (> 0.8 ft):'}</b> {isHi ? 'इंजन में पानी जाने और बहाव का खतरा। गति धीमी रखें या वैकल्पिक मार्ग चुनें।' : 'Risk of hydroplaning and electronic failure. Choose alternative route.'}
        </li>
        <li>
          🚶 <b>{isHi ? 'पैदल यात्री (> 1.5 फीट):' : 'Pedestrians (> 1.5 ft):'}</b> {isHi ? 'खुले नाले और गड्ढे पानी में छिप जाते हैं। पानी में बिल्कुल प्रवेश न करें।' : 'Open manholes and road craters become invisible. Avoid wading into floodwaters.'}
        </li>
      </ul>

      <div className="banner info small" style={{ marginTop: 20 }}>
        ℹ️ {isHi
          ? 'महत्वपूर्ण सूचना: AI द्वारा दी गई गहराई केवल फोटो आधारित अनुमान है। टेस्टिंग के दौरान "डेमो नमूना" टैग वाली रिपोर्ट ज्ञात जलभराव स्थलों के उदाहरणात्मक डेटा हैं।'
          : 'Important Note: Depth estimates derived from images are visual approximations. Reports tagged "Demo Sample" represent illustrative records for validation during non-monsoon periods.'}
      </div>
    </div>
  )
}
