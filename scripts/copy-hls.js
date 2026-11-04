// usage: node copy-hls.js <src hls.min.js> <dst>
const fs = require('fs');
const [src, dst] = process.argv.slice(2);
if (!src || !dst || !fs.existsSync(src)) { console.error('hls.min.js not found at ' + src); process.exit(1); }
fs.copyFileSync(src, dst);
console.log('copied hls.js ->', dst);
