"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const http = require("node:http");
const path = require("node:path");
const { chromium } = require("playwright");

async function main() {
  const htmlPath = path.join(__dirname, "pulse_nova_android_offline.html");
  const html = fs.readFileSync(htmlPath);
  const sw = fs.readFileSync(path.join(__dirname, "sw.js"));
  const server = http.createServer((req, res) => {
    if (req.url === "/sw.js") { res.writeHead(200, { "content-type": "text/javascript" }); return res.end(sw); }
    if (req.url !== "/" && req.url !== "/pulse_nova_android_offline.html") { res.writeHead(404); return res.end(); }
    res.writeHead(200, { "content-type": "text/html; charset=utf-8",
                         "cache-control": "no-store" });
    res.end(html);
  });
  let browser;
  try {
    await new Promise((resolve, reject) => {
      server.once("error", reject);
      server.listen(0, "127.0.0.1", resolve);
    });
    browser = await chromium.launch({ headless: true });
    const page = await browser.newPage({ acceptDownloads: true });
    const errors = [];
    page.on("pageerror", e => errors.push(e.message));
    await page.goto("http://127.0.0.1:" + server.address().port + "/pulse_nova_android_offline.html", { waitUntil: "load" });
    await page.evaluate(async () => { await navigator.serviceWorker.ready; });
    await page.reload();
    await page.waitForFunction(() => !!navigator.serviceWorker.controller);
    assert.equal(await page.locator("#play").isDisabled(), true);
    assert.equal(await page.locator("#save").isDisabled(), true);
    await page.locator("#generate").click();
    await page.waitForFunction(() => document.querySelector("#status").textContent.includes("WAV ready"), null, { timeout: 15000 });
    assert.equal(await page.locator("#play").isEnabled(), true);
    assert.equal(await page.locator("#save").isEnabled(), true);
    const details = await page.locator("#player").evaluate(async audio => {
      const url = audio.src;
      if (!url.startsWith("blob:")) throw Error("Expected blob URL");
      const response = await fetch(url);
      const data = await response.arrayBuffer();
      const view = new DataView(data);
      const ascii = offset => String.fromCharCode(...Array.from({ length: 4 }, (_, i) => view.getUint8(offset + i)));
      return { urlValid: true, bytes: data.byteLength, riff: ascii(0), wave: ascii(8),
               fmt: ascii(12), data: ascii(36), rate: view.getUint32(24, true),
               bits: view.getUint16(34, true), payload: view.getUint32(40, true) };
    });
    assert.equal(details.riff, "RIFF");
    assert.equal(details.wave, "WAVE");
    assert.equal(details.fmt, "fmt ");
    assert.equal(details.data, "data");
    assert.equal(details.rate, 22050);
    assert.equal(details.bits, 16);
    assert.equal(details.bytes, details.payload + 44);
    const media = await page.locator("#player").evaluate(async audio => {
      await new Promise((resolve, reject) => {
        if (audio.readyState >= 1) return resolve();
        audio.addEventListener("loadedmetadata", resolve, { once: true });
        audio.addEventListener("error", () => reject(Error("audio decoding error")), { once: true });
      });
      return { duration: audio.duration, readyState: audio.readyState, error: audio.error?.code ?? null };
    });
    assert.ok(media.duration > 0 && Number.isFinite(media.duration));
    assert.ok(media.readyState >= 1);
    assert.equal(media.error, null);
    const downloadPromise = page.waitForEvent("download");
    await page.locator("#save").click();
    const download = await downloadPromise;
    assert.match(download.suggestedFilename(), /^pulse-nova-offline-.*\.wav$/);
    const downloadPath = await download.path();
    const saved = fs.readFileSync(downloadPath);
    assert.equal(saved.subarray(0, 4).toString("ascii"), "RIFF");
    assert.equal(saved.length, details.bytes);
    // Simulate Android Chrome viewport and touch interaction in Chromium.
    // This is NOT a physical Android WebView or device test.
    const mobile = await browser.newContext({
      viewport: { width: 393, height: 851 },
      deviceScaleFactor: 2.75,
      isMobile: true, hasTouch: true, acceptDownloads: true
    });
    try {
      const phone = await mobile.newPage();
      phone.on("pageerror", e => errors.push("mobile: " + e.message));
      await phone.goto("http://127.0.0.1:" + server.address().port + "/");
      await phone.locator("#bars").fill("2");
      await phone.locator("#bpm").fill("120");
      await phone.locator("#generate").tap();
      await phone.waitForFunction(() => document.querySelector("#status").textContent.includes("WAV ready"), null, { timeout: 15000 });
      await phone.waitForFunction(() => document.querySelector("#player").readyState >= 1, null, { timeout: 15000 });
      await phone.locator("#play").tap();
      await phone.waitForFunction(() => {
        const audio = document.querySelector("#player");
        return !audio.paused && audio.error === null;
      }, null, { timeout: 10000 });
      const playback = await phone.locator("#player").evaluate(audio => ({
        paused: audio.paused, error: audio.error?.code ?? null
      }));
      assert.equal(playback.paused, false);
      assert.equal(playback.error, null);
      const mobileDownload = phone.waitForEvent("download", { timeout: 15000 });
      await phone.locator("#save").tap();
      const mobileSaved = await mobileDownload;
      assert.match(mobileSaved.suggestedFilename(), /\\.wav$/);
      console.log("PASS: Chromium mobile viewport, touch controls, playback and WAV download");
    } finally {
      await mobile.close();
    }
    assert.deepEqual(errors, []);
    await page.context().setOffline(true);
    await page.reload({ waitUntil: "load" });
    await page.locator("#generate").click();
    await page.waitForFunction(() => document.querySelector("#status").textContent.includes("WAV ready"), null, { timeout: 15000 });
    assert.equal(await page.locator("#play").isEnabled(), true);
    await page.context().setOffline(false);
    console.log("PASS: real Chromium UI, Blob WAV, audio metadata, browser download and offline reload");
    console.log("NOTE: Headless Chromium does not verify physical Android speaker output or WebView export.");
  } finally {
    if (browser) await browser.close();
    await new Promise(resolve => server.close(resolve));
  }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
