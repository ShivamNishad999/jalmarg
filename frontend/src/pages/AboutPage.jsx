import { Link } from 'react-router-dom'
import { useLang } from '../i18n'
import ServerSettings from '../components/ServerSettings'

const STEPS = {
  en: [
    ['📷', 'Snap', 'Anyone stuck at a flooded road takes a photo. GPS is added automatically.'],
    ['🤖', 'AI reads the depth', 'A vision model on Amazon Bedrock compares the water with tyres, kerbs and legs in the photo and says how deep it is — and who can cross.'],
    ['🗺️', 'Everyone is warned', 'The spot goes live on the map, people within 3 km get an alert, and routes are re-planned around it.'],
    ['🏛️', 'The city fixes it', 'Spots that flood again and again become hotspots, with a one-click evidence PDF for MCD, Noida Authority or GMDA.']
  ],
  hi: [
    ['📷', 'फोटो लें', 'डूबी सड़क पर फँसा कोई भी फोटो ले। लोकेशन अपने आप जुड़ जाती है।'],
    ['🤖', 'AI गहराई बताता है', 'Amazon Bedrock पर AI फोटो में टायर, फुटपाथ और पैरों से पानी की गहराई का अनुमान लगाता है — और बताता है कौन निकल सकता है।'],
    ['🗺️', 'सबको चेतावनी', 'जगह मैप पर लाइव होती है, 3 km के लोगों को अलर्ट जाता है, और रास्ते उससे बचकर बनते हैं।'],
    ['🏛️', 'शहर ठीक करे', 'बार-बार डूबने वाली जगहें हॉटस्पॉट बनती हैं, और MCD / नोएडा प्राधिकरण / GMDA के लिए एक क्लिक में सबूत PDF।']
  ]
}

const AWS = [
  ['Amazon Bedrock (Nova)', 'Reads water depth from the photo'],
  ['Amazon Corretto 21', 'Runs the Spring Boot backend (AWS open-source JDK)'],
  ['AWS App Runner / EC2', 'Hosts the backend API'],
  ['Amazon S3', 'Stores report photos (private bucket)'],
  ['Amazon RDS (MySQL)', 'Reports, hotspots, subscriptions'],
  ['Amazon SNS', 'Email alerts with per-user filter policies'],
  ['Amazon EventBridge Scheduler', 'Hourly rain + flood check'],
  ['AWS Amplify Hosting', 'Serves this installable web app (PWA)'],
  ['Amazon CloudWatch', 'Logs and health of the service']
]

export default function AboutPage() {
  const { lang } = useLang()
  return (
    <div className="page narrow about">
      <p className="eyebrow">Track 02 · Heat and Water · Team Growforge</p>
      <h1 className="hero-title">
        {lang === 'hi' ? 'हर मानसून सड़कें डूबती हैं। अब किसी को अंदाज़ा नहीं लगाना पड़ेगा।' : 'Every monsoon, the same roads drown. Nobody should have to guess.'}
      </h1>
      <p className="lead">
        {lang === 'hi'
          ? 'अंडरपास में पानी कितना है, यह तब पता चलता है जब बाइक बंद हो जाती है। JalMarg लोगों की फोटो और AI से हर डूबी सड़क को लाइव मैप पर लाता है, सुरक्षित रास्ता बताता है, और नगर निगम को स्थायी समाधान के लिए सबूत देता है।'
          : 'In Delhi NCR you find out how deep an underpass is when your scooter dies in it. JalMarg turns citizen photos into live, AI-measured flood reports, routes you around them, and hands the city the evidence to fix the drain for good.'}
      </p>
      <div className="row gap wrap">
        <Link to="/report" className="btn primary">📷 {lang === 'hi' ? 'रिपोर्ट करें' : 'Report a flooded road'}</Link>
        <Link to="/" className="btn ghost">🗺️ {lang === 'hi' ? 'लाइव मैप' : 'See the live map'}</Link>
      </div>

      <h2>{lang === 'hi' ? 'कैसे काम करता है' : 'How it works'}</h2>
      <ol className="how">
        {STEPS[lang].map(([icon, title, body]) => (
          <li key={title}>
            <span className="how-ico" aria-hidden>{icon}</span>
            <div><b>{title}</b><p>{body}</p></div>
          </li>
        ))}
      </ol>

      <h2>{lang === 'hi' ? 'AWS पर बना' : 'Built on AWS'}</h2>
      <div className="aws-grid">
        {AWS.map(([name, what]) => (
          <div key={name} className="aws-item"><b>{name}</b><span>{what}</span></div>
        ))}
      </div>

      <h2>{lang === 'hi' ? 'सुरक्षा नियम' : 'Safety rules we use'}</h2>
      <ul className="rules">
        <li>🛵 {lang === 'hi' ? '0.5 फीट से ज़्यादा: बाइक/स्कूटर के लिए ज़्यादा खतरा' : 'Over 0.5 ft: high risk for two-wheelers'}</li>
        <li>🚗 {lang === 'hi' ? '0.8 फीट से ज़्यादा: कार के लिए ज़्यादा खतरा' : 'Over 0.8 ft: high risk for cars'}</li>
        <li>🚶 {lang === 'hi' ? '1.5 फीट से ज़्यादा: पैदल ज़्यादा खतरा (खुले नाले छिप जाते हैं)' : 'Over 1.5 ft: high risk on foot (open drains are hidden)'}</li>
      </ul>
      <p className="small muted">
        {lang === 'hi'
          ? 'नोट: टेस्टिंग के दौरान मैप पर "डेमो डेटा" वाली रिपोर्ट उदाहरण के लिए हैं।'
          : 'Note: reports tagged "demo data" are illustrative examples at known waterlogging spots, used while testing.'}
      </p>
      <ServerSettings />
    </div>
  )
}
