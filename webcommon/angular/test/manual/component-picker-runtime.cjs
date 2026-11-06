/* Licensed to the Apache Software Foundation (ASF) under the Apache License,
 * Version 2.0. See the repository LICENSE file. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const code = fs.readFileSync('webcommon/angular/src/org/netbeans/modules/angular/resources/component-picker.js', 'utf8')
  .replace('__ALP_ENDPOINT__', JSON.stringify('http://127.0.0.1:1234/events/test'));
function runtime(available = true) {
  const listeners = new Map(), posts = [], queued = [], overlays = [];
  let tick, failed = false, cleared = false;
  class Element {
    constructor(name) { this.localName = name; this.isConnected = true; this.style = {}; }
    getBoundingClientRect() { return {left: 1, top: 2, width: 100, height: 80}; }
    scrollIntoView() { this.scrolled = true; }
    remove() { this.removed = true; }
  }
  class Card {}
  const host = new Element('app-card'), inner = new Element('button'), component = new Card();
  const document = {
    documentElement: {appendChild: node => overlays.push(node)},
    createElement: name => new Element(name),
    addEventListener: (type, listener) => listeners.set(type, listener),
    removeEventListener: (type, listener) => { if (listeners.get(type) === listener) listeners.delete(type); },
    querySelectorAll: selector => selector === 'app-card' || selector === '*' ? [host] : []
  };
  const browser = {ng: available ? {
    getComponent: element => element === host ? component : null,
    getOwningComponent: element => element === inner ? component : null,
    getHostElement: () => host
  } : undefined};
  const context = {window: browser, document, Element, URL, AbortSignal,
    console: {info() {}, warn() {}},
    setInterval: callback => {tick = callback; return 1;}, clearInterval: () => {cleared = true;},
    setTimeout: () => 1, clearTimeout() {},
    fetch: async (url, options = {}) => {
      if (failed) throw new Error('Disconnected');
      if (options.method === 'POST') posts.push(JSON.parse(options.body));
      return {ok: true, json: async () => queued.splice(0)};
    }
  };
  vm.runInNewContext(code, context);
  return {browser, listeners, posts, queued, overlays, host, inner, tick: () => tick(), fail: () => {failed = true;}, cleared: () => cleared};
}
const settle = () => new Promise(resolve => setImmediate(resolve));
(async () => {
  assert.throws(() => runtime(false), /development inspection/);
  const app = runtime(); await settle();
  const click = {composedPath: () => [app.inner], preventDefault() {this.prevented = true;}, stopImmediatePropagation() {this.stopped = true;}};
  await app.listeners.get('click')(click);
  assert.equal(click.prevented, true); assert.equal(click.stopped, true);
  assert.equal(app.posts[0].events[0].component, 'Card');
  assert.equal(app.posts[0].events[0].changes.selector, 'app-card');
  assert.equal(app.posts[0].events[0].kind, 'pick');
  assert.equal(app.listeners.has('click'), false);
  app.browser.alpComponentPicker.pick(); await app.listeners.get('click')(click);
  assert.equal(app.posts[0].events[0].instance, app.posts[1].events[0].instance);
  app.queued.push({type: 'highlight', component: 'Card', selector: 'app-card'});
  app.tick(); await settle(); assert.equal(app.host.scrolled, true); assert.ok(app.overlays.length >= 1);
  app.browser.alpComponentPicker.pick();
  app.listeners.get('keydown')({key: 'Escape', preventDefault() {}, stopImmediatePropagation() {}});
  assert.equal(app.listeners.has('click'), false);
  app.fail();
  for (let i = 0; i < 3; i++) {app.tick(); await settle();}
  assert.equal(app.cleared(), true); assert.equal(app.listeners.size, 0);
  console.log('Component picker: dev guard, owning-component selection, instance identity, reverse highlight, Escape and disconnect cleanup passed.');
})().catch(error => {console.error(error); process.exitCode = 1;});
