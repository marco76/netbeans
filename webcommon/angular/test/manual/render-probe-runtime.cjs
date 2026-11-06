/* Licensed to the Apache Software Foundation (ASF) under the Apache License,
 * Version 2.0. See the repository LICENSE file. */
// Run from the repository root; uses the already bundled TypeScript compiler.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const ts = require(path.resolve(process.argv[2] || 'nbbuild/netbeans/webcommon/typescript-lsp/node_modules/typescript/lib/typescript.js'));
const source = fs.readFileSync('webcommon/angular/src/org/netbeans/modules/angular/resources/render-probe.ts.template', 'utf8');
const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS } }).outputText;
function runtime(development, angularZone = false) {
  const calls = [], exports = {}, browser = {};
  let tick;
  const context = { exports, window: browser, URL, AbortController, console, setTimeout, clearTimeout,
    setInterval: fn => { tick = fn; return 1; }, clearInterval: () => {},
    fetch: async (url, options) => { calls.push({url, body: JSON.parse(options.body)}); return {ok: true}; },
    require: name => { assert.equal(name, '@angular/core'); return {isDevMode: () => development, NgZone: {isInAngularZone: () => angularZone}}; }
  };
  vm.runInNewContext(code, context);
  return {exports, browser, calls, tick: () => tick()};
}
(async () => {
  const dev = runtime(true);
  const probe = new dev.exports.AlpRenderProbe('Card', 'src/card.ts');
  const object = {};
  Object.defineProperty(object, 'dangerous', {get() {throw new Error('Must never read object getters');}});
  probe.changes({title: {previousValue: 'a', currentValue: 'b', firstChange: false}, data: {previousValue: object, currentValue: object, firstChange: false}});
  probe.check(); probe.check(); probe.mark('Button clicked');
  let events = dev.browser.alpRenderProbe.dump().events;
  assert.equal(events.length, 4);
  assert.equal(events[0].changes.title.previous, 'a');
  assert.equal(events[0].changes.data.previous.reference, events[0].changes.data.current.reference);
  assert.match(events[1].detail, /following input changes/);
  assert.match(events[2].detail, /Trigger unknown/);
  assert.throws(() => dev.exports.connectRenderProbe('https://remote.example/events'));
  dev.exports.connectRenderProbe('http://127.0.0.1:12345/events/test');
  dev.tick(); await new Promise(resolve => setImmediate(resolve));
  assert.equal(dev.calls.length, 1); assert.equal(dev.calls[0].body.events.length, 4);
  for (let i = 0; i < 2100; i++) probe.check();
  assert.equal(dev.browser.alpRenderProbe.dump().events.length, 2000);
  const production = runtime(false);
  const inert = new production.exports.AlpRenderProbe('Card', 'src/card.ts');
  inert.changes({}); inert.check(); inert.mark('ignored');
  production.exports.connectRenderProbe('http://127.0.0.1:12345/events/test');
  assert.equal(production.browser.alpRenderProbe, undefined); assert.equal(production.calls.length, 0);
  assert.throws(() => runtime(true, true).exports.connectRenderProbe('http://localhost:12345/events/test'), /runOutsideAngular/);
  dev.exports.disconnectRenderProbe();
  console.log('Render companion: input snapshots, reference identity, marks, transport, bounded history, production guard and Angular-zone guard passed.');
})().catch(error => {console.error(error); process.exitCode = 1;});
