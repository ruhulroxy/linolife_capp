import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import test from 'node:test';

const source = readFileSync(new URL('../www/native-push.js', import.meta.url), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));
function boot(options = {}) {
    const listeners = new Map(), timers = new Map(), calls = [];
    let timerID = 0, receive = options.receive || 'granted', token = 'fcm-device-token';
    const push = {
        addListener: async (name, callback) => { listeners.set(name, callback); return { remove() {} }; },
        checkPermissions: async () => { calls.push('check'); return { receive }; },
        requestPermissions: async () => { calls.push('request'); return { receive }; },
        register: async () => {
            calls.push('register');
            if (options.registrationError) listeners.get('registrationError')({ error: options.registrationError });
            else if (!options.manual) listeners.get('registration')({ value: token });
        },
        unregister: async () => { calls.push('unregister'); },
        createChannel: async value => { calls.push(JSON.parse(JSON.stringify(value))); }
    };
    const window = {
        Capacitor: {
            isNativePlatform: () => options.native !== false,
            getPlatform: () => options.platform || 'android',
            Plugins: {
                PushNotifications: options.missingPlugin ? undefined : push,
                LinoLifeFirebase: { deleteToken: async () => { calls.push('firebase-delete'); } }
            }
        }
    };
    window.top = options.iframe ? {} : window;
    if (options.existing) window.median = { firebaseMessaging: options.existing };
    runInNewContext(source, {
        window, location: { origin: options.origin || 'https://linolife.co' }, console,
        setTimeout: callback => { timers.set(++timerID, callback); return timerID; },
        clearTimeout: id => timers.delete(id)
    });
    return {
        api: window.median?.firebaseMessaging, calls, timers, listeners,
        setPermission: value => { receive = value; }, setToken: value => { token = value; }
    };
}

test('initialization installs listeners without prompting, registering or contacting the server', async () => {
    const app = boot();
    await flush();
    assert.equal(app.listeners.size, 4);
    assert.deepEqual(app.calls, []);
});

test('only trusted native main frames receive the adapter', () => {
    for (const options of [{ native: false }, { origin: 'https://example.com' }, { iframe: true }, { missingPlugin: true }]) {
        assert.equal(boot(options).api, undefined);
    }
    const existing = {};
    assert.equal(boot({ existing }).api, existing);
});

test('permission callbacks follow the native permission result', async () => {
    const app = boot({ receive: 'prompt' });
    let checked;
    await app.api.checkPermission({ callback: value => { checked = value; } });
    assert.equal(checked.granted, false);
    assert.deepEqual(app.calls, ['check']);
    app.setPermission('granted');
    assert.equal((await app.api.requestPermission()).granted, true);
    assert.deepEqual(app.calls, ['check', 'request']);
});

test('register returns a fresh native token through the hosted website callback', async () => {
    const app = boot();
    let saved;
    await app.api.register({ callback: value => { saved = value.token; } });
    assert.equal(saved, 'fcm-device-token');
    app.setToken('rotated-fcm-token');
    assert.equal((await app.api.getToken()).token, 'rotated-fcm-token');
    assert.equal(app.calls.filter(call => call === 'register').length, 2);
    assert.equal(app.timers.size, 0);
});

test('getToken never registers when permission is denied', async () => {
    const app = boot({ receive: 'denied' });
    const result = await app.api.getToken();
    assert.match(result.error, /permission/);
    assert.equal(app.calls.includes('register'), false);
});

test('native registration errors reach the callback exactly once', async () => {
    const app = boot({ registrationError: 'APNs registration failed' });
    let count = 0;
    const result = await app.api.register({ callback: () => count++ });
    assert.equal(count, 1);
    assert.equal(result.error, 'APNs registration failed');
    assert.equal(app.timers.size, 0);
});

test('concurrent token callers share one native registration', async () => {
    const app = boot({ manual: true });
    const first = app.api.register();
    const second = app.api.getToken();
    await flush();
    assert.equal(app.calls.filter(call => call === 'register').length, 1);
    app.listeners.get('registration')({ value: 'shared-token' });
    assert.equal((await first).token, 'shared-token');
    assert.equal((await second).token, 'shared-token');
});

test('registration timeout clears pending state so a later attempt can succeed', async () => {
    const app = boot({ manual: true });
    const first = app.api.register();
    await flush();
    [...app.timers.values()][0]();
    assert.match((await first).error, /timed out/);
    const second = app.api.register();
    await flush();
    app.listeners.get('registration')({ value: 'retry-token' });
    assert.equal((await second).token, 'retry-token');
});

test('iOS deletes the Firebase token before unregistering APNs', async () => {
    const app = boot({ platform: 'ios' });
    await app.api.deleteToken();
    assert.deepEqual(app.calls, ['firebase-delete', 'unregister']);
});

test('Android deletion uses the Capacitor FCM unregister method', async () => {
    const app = boot();
    await app.api.deleteToken();
    assert.deepEqual(app.calls, ['unregister']);
});

test('channel options match the existing server channel and skip unsupported iOS calls', async () => {
    const app = boot();
    await app.api.createChannel({ channelId: 'linolife_updates', channelName: 'Lino Life updates', importance: 'default' });
    assert.deepEqual(app.calls, [{ id: 'linolife_updates', name: 'Lino Life updates', importance: 3, visibility: 0 }]);
    const ios = boot({ platform: 'ios' });
    await ios.api.createChannel({});
    assert.deepEqual(ios.calls, []);
});

test('cold-start taps wait for the website listener and dismissal does not navigate', async () => {
    const app = boot();
    await flush();
    app.listeners.get('pushNotificationActionPerformed')({ actionId: 'tap', notification: { id: 'booking' } });
    const actions = [];
    const handle = app.api.notificationClicked.addListener(action => actions.push(action));
    assert.equal(actions[0].notification.id, 'booking');
    app.listeners.get('pushNotificationActionPerformed')({ actionId: 'dismiss' });
    assert.equal(actions.length, 1);
    handle.remove();
});

test('foreground notifications use the shape expected by the hosted website', async () => {
    const app = boot();
    const received = [];
    app.api.notificationReceived.addListener(value => received.push(value));
    await flush();
    app.listeners.get('pushNotificationReceived')({ title: 'Booking confirmed' });
    assert.equal(received[0].notification.title, 'Booking confirmed');
});

test('disabling notifications cancels a pending registration', async () => {
    const app = boot({ manual: true });
    const pending = app.api.register();
    await flush();
    await app.api.deleteToken();
    assert.match((await pending).error, /cancelled/);
    assert.equal(app.timers.size, 0);
});
