import sharp from 'sharp';
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const icon = resolve(root, 'assets/branding/linolifeicon.png');
const logo = resolve(root, 'assets/branding/linolife.png');
const res = 'android/app/src/main/res';
const lime = '#d5fb0a';
const white = '#ffffff';
const transparent = { r: 0, g: 0, b: 0, alpha: 0 };

async function save(path, buffer) {
    const output = resolve(root, path);
    await mkdir(dirname(output), { recursive: true });
    await writeFile(output, buffer);
}

async function canvas(source, width, height, artworkWidth, background) {
    const artwork = await sharp(source).resize({ width: artworkWidth }).png().toBuffer();
    return sharp({ create: { width, height, channels: 4, background } })
        .composite([{ input: artwork, gravity: 'centre' }]).png().toBuffer();
}

const iconMeta = await sharp(icon).metadata();
const logoMeta = await sharp(logo).metadata();
if (iconMeta.width !== iconMeta.height) throw Error('App icon source must be square.');
if (logoMeta.width <= logoMeta.height) throw Error('Splash source must be the wide wordmark.');
// The supplied icon has an off-white canvas outside its rounded lime corners.
// Clip only those corners so tiny pale flecks do not appear on adaptive icons.
const iconMask = Buffer.from(`<svg width="${iconMeta.width}" height="${iconMeta.height}"><rect width="100%" height="100%" rx="${Math.round(iconMeta.width * 0.035)}" fill="white"/></svg>`);
const androidIcon = await sharp(icon).ensureAlpha().composite([{ input: iconMask, blend: 'dest-in' }]).png().toBuffer();

for (const [density, scale] of [['mdpi', 1], ['hdpi', 1.5], ['xhdpi', 2], ['xxhdpi', 3], ['xxxhdpi', 4]]) {
    const size = Math.round(48 * scale);
    await save(`${res}/mipmap-${density}/ic_launcher.webp`,
        await sharp(androidIcon).resize(size, size).flatten({ background: lime }).webp({ lossless: true }).toBuffer());
    const round = await canvas(androidIcon, size, size, Math.round(size * 0.64), lime);
    await save(`${res}/mipmap-${density}/ic_launcher_round.webp`, await sharp(round).webp({ lossless: true }).toBuffer());
    // The complete 46dp square fits within the adaptive icon's central 66dp circle.
    await save(`${res}/mipmap-${density}/ic_launcher_foreground.png`,
        await canvas(androidIcon, Math.round(108 * scale), Math.round(108 * scale), Math.round(46 * scale), transparent));
}
await save('android/app/src/main/ic_launcher-playstore.png',
    await sharp(icon).resize(512, 512).flatten({ background: lime }).removeAlpha().png().toBuffer());
await save('ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-512@2x.png',
    await sharp(icon).resize(1024, 1024).flatten({ background: lime }).removeAlpha().png().toBuffer());

await save(`${res}/drawable-nodpi/splash_wordmark.png`, await sharp(logo).resize({ width: 1200 }).png().toBuffer());
// Android 12+ masks its system splash icon. Keep the wide wordmark inside the
// safe circle instead of enlarging and clipping its text.
await save(`${res}/drawable-nodpi/splash_icon.png`, await canvas(logo, 864, 864, 540, transparent));
await save(`${res}/drawable/splash.png`, await canvas(logo, 480, 800, 384, white));
const splashSizes = {
    mdpi: [[320, 480], [480, 320]],
    hdpi: [[480, 800], [800, 480]],
    xhdpi: [[720, 1280], [1280, 720]],
    xxhdpi: [[960, 1600], [1600, 960]],
    xxxhdpi: [[1280, 1920], [1920, 1280]]
};
for (const [density, sizes] of Object.entries(splashSizes)) {
    for (const [index, [width, height]] of sizes.entries()) {
        await save(`${res}/drawable-${index === 0 ? 'port' : 'land'}-${density}/splash.png`,
            await canvas(logo, width, height, Math.round(width * 0.8), white));
    }
}
for (const [filename, scale] of [['linolife.png', 1], ['linolife@2x.png', 2], ['linolife@3x.png', 3]]) {
    const width = 1024 * scale;
    const image = await canvas(logo, width, Math.round(width * logoMeta.height / logoMeta.width), Math.round(width * 0.8), white);
    await save(`ios/App/App/Assets.xcassets/Splash.imageset/${filename}`,
        await sharp(image).removeAlpha().png().toBuffer());
}

// Preview the circular launcher mask and the iPhone launch screen at 360px wide.
const circularIcon = await canvas(androidIcon, 216, 216, 138, lime);
const circle = Buffer.from('<svg width="216" height="216"><circle cx="108" cy="108" r="108" fill="white"/></svg>');
const masked = await sharp(circularIcon).composite([{ input: circle, blend: 'dest-in' }]).png().toBuffer();
const phone = await canvas(logo, 360, 720, 288, white);
const base = Buffer.from('<svg width="800" height="820"><rect width="800" height="820" fill="#f3f4f6"/><text x="80" y="65" font-size="24" font-family="Arial">App icon</text><text x="400" y="65" font-size="24" font-family="Arial">Splash screen</text></svg>');
await save('assets/branding/preview.png', await sharp(base).composite([
    { input: masked, left: 80, top: 290 }, { input: phone, left: 400, top: 85 }
]).png().toBuffer());
console.log('Generated Android launcher/splash assets, iOS app icon/launch images and branding preview.');
