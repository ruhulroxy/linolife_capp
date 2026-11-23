import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
const source = readFileSync(new URL('../www/native-startup.js', import.meta.url), 'utf8');
function setup({ modern = true, rendered = true, entry = false, ready = false } = {}) {
    const events = {}, frames = []; let calls = 0;
    const html = { dataset: ready ? { appReady: 'true' } : {} };
    const document = { documentElement: html, readyState: 'loading',
        addEventListener: (name, fn) => (events[name] ||= []).push(fn),
        querySelector: selector => selector.includes('meta[') ? modern : selector.includes('data-capp') ? rendered : entry };
    const window = { Capacitor: { Plugins: { LinoLifeStartup: { ready: async () => { calls++; } } } },
        addEventListener: (name, fn) => (events[name] ||= []).push(fn) };
    runInNewContext(source, { window, document, requestAnimationFrame: fn => frames.push(fn) });
    return { html, get calls() { return calls; }, emit(name) { for (const fn of events[name] || []) fn(); },
        paint() { while (frames.length) frames.shift()(); } };
}
test('modern startup waits for the authenticated server document readiness signal', () => {
    const app = setup(); app.emit('DOMContentLoaded'); app.paint(); assert.equal(app.calls, 0);
    app.html.dataset.appReady = 'true'; app.emit('linolife:ready'); app.emit('linolife:ready');
    assert.equal(app.calls, 1);
});
test('older hosted pages hand off after paint but not during redirects or non-app/error documents', () => {
    const app = setup({ modern: false }); app.emit('DOMContentLoaded'); assert.equal(app.calls, 0);
    app.paint(); assert.equal(app.calls, 1);
    for (const options of [{ entry: true }, { rendered: false }]) {
        const blocked = setup({ modern: false, ...options }); blocked.emit('DOMContentLoaded'); blocked.paint();
        assert.equal(blocked.calls, 0);
    }
});
test('native config, secure persistence and platform assets stay in sync', () => {
    const config = JSON.parse(readFileSync(new URL('../capacitor.config.json', import.meta.url)));
    const android = JSON.parse(readFileSync(new URL('../android/app/src/main/assets/capacitor.config.json', import.meta.url)));
    const ios = JSON.parse(readFileSync(new URL('../ios/App/App/capacitor.config.json', import.meta.url)));
    assert.equal(config.server.errorPath, 'offline.html');
    assert.deepEqual(android.server, config.server); assert.deepEqual(ios.server, config.server);
    for (const path of ['android/app/src/main/assets/public/', 'ios/App/App/public/']) {
        assert.equal(readFileSync(new URL('../' + path + 'native-startup.js', import.meta.url), 'utf8'), source);
        assert.ok(readFileSync(new URL('../' + path + 'offline.html', import.meta.url), 'utf8').includes('LinoLifeStartup'));
    }
    const activity = readFileSync(new URL('../android/app/src/main/java/co/linolife/app/MainActivity.java', import.meta.url), 'utf8');
    assert.match(activity, /CookieManager.getInstance\(\).flush\(\)/);
    assert.match(activity, /loadUrl\(bridge.getServerUrl\(\)\)/);
    const swift = readFileSync(new URL('../ios/App/App/LinoLifeViewController.swift', import.meta.url), 'utf8');
    assert.match(swift, /WKWebsiteDataStore.default\(\)/);
    assert.doesNotMatch(swift, /nonPersistent\(|removeData\(/);
    assert.doesNotMatch(source, /localStorage|sessionStorage|document.cookie/);
});
