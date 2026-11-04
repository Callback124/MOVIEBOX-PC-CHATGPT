// Turns the Capacitor Android project into an Android TV (Leanback) app.
const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, 'android', 'app', 'src', 'main');
const mf = path.join(root, 'AndroidManifest.xml');
let x = fs.readFileSync(mf, 'utf8');
function must(cond, msg) { if (!cond) { console.error('PATCH FAILED: ' + msg); process.exit(1); } }

// 1) TV launcher entry + TV features (touchscreen / leanback not required so it installs on phones too)
must(/android\.intent\.category\.LAUNCHER/.test(x), 'LAUNCHER category not found');
x = x.replace(/(<category android:name="android\.intent\.category\.LAUNCHER"\s*\/>)/,
  '$1\n                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />');
must(/<application/.test(x), '<application not found');
x = x.replace(/<application/,
  '<uses-feature android:name="android.software.leanback" android:required="false" />\n    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />\n\n    <application\n        android:banner="@drawable/banner"');
// 2) landscape only
must(/android:name="\.MainActivity"/.test(x), 'MainActivity not found');
x = x.replace(/android:name="\.MainActivity"/, 'android:name=".MainActivity"\n            android:screenOrientation="landscape"');
fs.writeFileSync(mf, x);

// 3) banner + icons
const res = path.join(root, 'res');
fs.mkdirSync(path.join(res, 'drawable'), { recursive: true });
fs.copyFileSync(path.join(__dirname, 'assets', 'banner.png'), path.join(res, 'drawable', 'banner.png'));
for (const d of fs.readdirSync(res)) {
  if (d === 'mipmap-anydpi-v26') fs.rmSync(path.join(res, d), { recursive: true, force: true });
  else if (d.startsWith('mipmap-')) {
    for (const n of ['ic_launcher.png', 'ic_launcher_round.png', 'ic_launcher_foreground.png']) {
      if (fs.existsSync(path.join(res, d, n))) fs.copyFileSync(path.join(__dirname, 'assets', 'icon.png'), path.join(res, d, n));
    }
  }
}
console.log('Android TV patch applied');
