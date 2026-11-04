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

// 4) native Media3 ExoPlayer screen + plugin
const cfg = JSON.parse(fs.readFileSync(path.join(__dirname, 'capacitor.config.json'), 'utf8'));
const javaDir = path.join(root, 'java', ...cfg.appId.split('.'));
must(fs.existsSync(javaDir), 'java package dir not found: ' + javaDir);
for (const f of ['MainActivity.java', 'ExoPlayerPlugin.java', 'PlayerActivity.java']) {
  fs.copyFileSync(path.join(__dirname, 'native', f), path.join(javaDir, f));
}
let m2 = fs.readFileSync(mf, 'utf8');
must(/<\/application>/.test(m2), '</application> not found');
m2 = m2.replace(/<\/application>/, '    <activity android:name=".PlayerActivity" android:exported="false" android:screenOrientation="landscape"\n            android:configChanges="orientation|screenSize|keyboardHidden|keyboard|smallestScreenSize|screenLayout|uiMode"\n            android:theme="@android:style/Theme.NoTitleBar.Fullscreen" />\n    </application>');
fs.writeFileSync(mf, m2);

const media3 = process.env.MEDIA3 || '1.4.1';
const gradle = path.join(__dirname, 'android', 'app', 'build.gradle');
let g = fs.readFileSync(gradle, 'utf8');
must(/^dependencies \{/m.test(g), 'dependencies block not found in app/build.gradle');
g = g.replace(/^dependencies \{/m, 'dependencies {\n    implementation "androidx.media3:media3-exoplayer:' + media3 + '"\n    implementation "androidx.media3:media3-exoplayer-hls:' + media3 + '"\n    implementation "androidx.media3:media3-exoplayer-dash:' + media3 + '"\n    implementation "androidx.media3:media3-ui:' + media3 + '"');
fs.writeFileSync(gradle, g);
const compile = process.env.COMPILE_SDK;
if (compile) {
  const vg = path.join(__dirname, 'android', 'variables.gradle');
  let v = fs.readFileSync(vg, 'utf8');
  must(/compileSdkVersion = \d+/.test(v) && /targetSdkVersion = \d+/.test(v), 'variables.gradle fields not found');
  v = v.replace(/compileSdkVersion = \d+/, 'compileSdkVersion = ' + compile).replace(/targetSdkVersion = \d+/, 'targetSdkVersion = ' + compile);
  fs.writeFileSync(vg, v);
  fs.appendFileSync(path.join(__dirname, 'android', 'gradle.properties'), '\nandroid.suppressUnsupportedCompileSdk=' + compile + '\n');
}
// newer Media3 needs a newer Android Gradle Plugin + Gradle wrapper (optional, via env)
if (process.env.AGP) {
  const bg = path.join(__dirname, 'android', 'build.gradle');
  let b = fs.readFileSync(bg, 'utf8');
  must(/com\.android\.tools\.build:gradle:[\d.]+/.test(b), 'AGP classpath not found');
  b = b.replace(/com\.android\.tools\.build:gradle:[\d.]+/, 'com.android.tools.build:gradle:' + process.env.AGP);
  fs.writeFileSync(bg, b);
}
if (process.env.GRADLE) {
  const wp = path.join(__dirname, 'android', 'gradle', 'wrapper', 'gradle-wrapper.properties');
  let w = fs.readFileSync(wp, 'utf8');
  must(/gradle-[\d.]+-(bin|all)\.zip/.test(w), 'gradle wrapper url not found');
  w = w.replace(/gradle-[\d.]+-(bin|all)\.zip/, 'gradle-' + process.env.GRADLE + '-bin.zip');
  fs.writeFileSync(wp, w);
}
console.log('Media3 ExoPlayer ' + media3 + ' added');
console.log('Android TV patch applied');
