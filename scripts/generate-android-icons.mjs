// Converts the existing Lucide icon system to native Android vectors.
import { writeFile, mkdir, copyFile } from 'node:fs/promises';
const directory = new URL('../android/app/src/main/res/drawable/', import.meta.url);
await mkdir(directory, { recursive: true });
const icons = ['coins', 'credit-card', 'piggy-bank', 'bookmark', 'info', 'calculator', 'trash-2', 'save', 'share-2', 'arrow-up', 'arrow-left', 'download', 'sun', 'moon', 'mail', 'globe', 'star', 'shield', 'sparkles', 'smartphone', 'x', 'check'];
for (const name of icons) {
  const { __iconNode: nodes } = await import(`../node_modules/lucide-react/dist/esm/icons/${name}.js`);
  const paths = nodes.map(([type, a]) => {
    let d = a.d;
    if (type === 'circle') d = `M${+a.cx - +a.r},${a.cy}a${a.r},${a.r} 0,1 0,${2 * +a.r},0a${a.r},${a.r} 0,1 0,${-2 * +a.r},0`;
    if (type === 'line') d = `M${a.x1},${a.y1}L${a.x2},${a.y2}`;
    if (type === 'polyline' || type === 'polygon') d = 'M' + a.points.trim().replace(/\s+/g, 'L') + (type === 'polygon' ? 'Z' : '');
    if (type === 'rect') {
      const x = +a.x, y = +a.y, w = +a.width, h = +a.height, r = +(a.rx || 0);
      d = `M${x+r},${y}H${x+w-r}Q${x+w},${y} ${x+w},${y+r}V${y+h-r}Q${x+w},${y+h} ${x+w-r},${y+h}H${x+r}Q${x},${y+h} ${x},${y+h-r}V${y+r}Q${x},${y} ${x+r},${y}Z`;
    }
    if (!d) throw new Error(`Unsupported ${name}: ${type}`);
    return `  <path android:pathData="${d}" android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="1.8" android:strokeLineCap="round" android:strokeLineJoin="round" />`;
  }).join('\n');
  if (name === 'coins') {
    for (const [file, size, viewport, offset] of [['ic_launcher_foreground', 108, 48, 12], ['ic_coins', 288, 36, 6]]) {
      await writeFile(new URL(`${file}.xml`, directory), `<!-- Lucide, ISC License. Same coins as the in-app splash; padded for Android masks. -->\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="${size}dp" android:height="${size}dp" android:viewportWidth="${viewport}" android:viewportHeight="${viewport}">\n  <group android:translateX="${offset}" android:translateY="${offset}">\n${paths}\n  </group>\n</vector>\n`);
    }
  }
  await writeFile(new URL(`lucide_${name.replaceAll('-', '_')}.xml`, directory), `<!-- Lucide, ISC License. See assets/Lucide-LICENSE.txt. -->\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n${paths}\n</vector>\n`);
}
await copyFile(new URL('../node_modules/lucide-react/LICENSE', import.meta.url), new URL('../android/app/src/main/assets/Lucide-LICENSE.txt', import.meta.url));
