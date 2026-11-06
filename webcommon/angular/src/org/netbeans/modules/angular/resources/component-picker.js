/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
(function (endpoint) {
  'use strict';
  if (window.alpComponentPicker) window.alpComponentPicker.dispose();
  const ng = window.ng;
  if (!ng || !ng.getComponent || !ng.getOwningComponent || !ng.getHostElement) {
    throw new Error('Angular development inspection APIs are unavailable. Use an Angular development build.');
  }
  const target = new URL(endpoint);
  if (!['http:', 'https:'].includes(target.protocol) || !['localhost', '127.0.0.1', '[::1]'].includes(target.hostname)) throw new Error('Use a local IDE receiver');
  let disposed = false, busy = false, failures = 0, overlay, hideTimer;
  const identities = new WeakMap(); let nextIdentity = 0;
  function componentAt(element) {
    if (!(element instanceof Element)) return null;
    try {
      const component = ng.getComponent(element) || ng.getOwningComponent(element);
      if (!component) return null;
      return { component, host: ng.getHostElement(component) };
    } catch (_) { return null; }
  }
  function outline(host) {
    if (!host || !host.isConnected) return;
    if (!overlay) {
      overlay = document.createElement('div');
      overlay.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483647;border:3px solid #00a6b4;background:rgba(0,166,180,.12);box-sizing:border-box;';
      document.documentElement.appendChild(overlay);
    }
    const rect = host.getBoundingClientRect();
    Object.assign(overlay.style, { left: rect.left + 'px', top: rect.top + 'px', width: rect.width + 'px', height: rect.height + 'px' });
    clearTimeout(hideTimer); hideTimer = setTimeout(removeOutline, 2500);
  }
  function removeOutline() { if (overlay) overlay.remove(); overlay = undefined; }
  function cancel() {
    removeOutline();
    document.removeEventListener('pointermove', hover, true);
    document.removeEventListener('click', select, true);
    document.removeEventListener('keydown', escape, true);
  }
  function hover(event) { const match = componentAt(event.composedPath()[0]); if (match) outline(match.host); else removeOutline(); }
  function escape(event) { if (event.key === 'Escape') { event.preventDefault(); event.stopImmediatePropagation(); cancel(); } }
  async function select(event) {
    const match = componentAt(event.composedPath()[0]);
    event.preventDefault(); event.stopImmediatePropagation();
    if (!match) return;
    cancel(); outline(match.host);
    if (!identities.has(match.component)) identities.set(match.component, String(++nextIdentity));
    const data = { version: 1, events: [{ time: Date.now(), instance: identities.get(match.component),
      component: match.component.constructor.name || '(anonymous)', source: '', kind: 'pick',
      detail: 'Selected ' + match.host.localName, changes: { selector: match.host.localName } }] };
    try {
      const response = await fetch(endpoint, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data), signal: AbortSignal.timeout(3000) });
      if (!response.ok) throw new Error('IDE rejected component selection');
    } catch (error) { console.warn('AlpLibre picker could not send selection', error); }
  }
  function pick() {
    cancel();
    document.addEventListener('pointermove', hover, true);
    document.addEventListener('click', select, true);
    document.addEventListener('keydown', escape, true);
    console.info('AlpLibre: click a component to open its source; Escape cancels.');
  }
  function highlight(command) {
    cancel();
    let candidates;
    try { candidates = document.querySelectorAll(command.selector || '*'); }
    catch (_) { console.warn('AlpLibre: unsupported component selector'); return; }
    let found = 0;
    for (const element of candidates) {
      let component;
      try { component = ng.getComponent(element); } catch (_) { continue; }
      if (component && component.constructor.name === command.component) { element.scrollIntoView({ block: 'center', behavior: 'auto' }); outline(element); found++; break; }
    }
    if (!found) console.info('AlpLibre: component is not mounted on this page.');
  }
  async function poll() {
    if (disposed || busy) return;
    busy = true;
    try {
      const response = await fetch(endpoint + '/commands', { signal: AbortSignal.timeout(3000) });
      if (!response.ok) throw new Error('Receiver is unavailable');
      failures = 0;
      const commands = await response.json();
      if (disposed) return;
      for (const command of commands) {
        if (command.type === 'pick') pick();
        else if (command.type === 'highlight') highlight(command);
        else if (command.type === 'cancel') cancel();
      }
    } catch (_) {
      if (++failures >= 3) {
        window.alpComponentPicker.dispose();
        console.info('AlpLibre component picker disconnected: IDE receiver unavailable.');
      }
    }
    finally { busy = false; }
  }
  const timer = setInterval(poll, 700);
  window.alpComponentPicker = { pick, cancel, dispose() { disposed = true; clearInterval(timer); clearTimeout(hideTimer); cancel(); } };
  pick(); void poll();
  console.info('AlpLibre component picker connected. Call window.alpComponentPicker.dispose() to disconnect.');
})(__ALP_ENDPOINT__);
