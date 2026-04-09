package com.tenahub.bot.controller;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class MiniAppPageController {

    @GetMapping(value = "/pharmacy/{pharmacyId}/photos", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String pharmacyPhotosPage(@PathVariable Long pharmacyId) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>Pharmacy Photos</title>
                  <style>
                    :root {
                      --bg: #f0f4f8;
                      --surface: #ffffff;
                      --text: #0f172a;
                      --muted: #475569;
                      --accent: #0ea5a4;
                      --border: #d9e2ec;
                      --arrow-bg: rgba(255,255,255,0.85);
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                      min-height: 100dvh;
                      display: flex;
                      flex-direction: column;
                      background: var(--bg);
                      font-family: "Segoe UI", Tahoma, Geneva, Verdana, sans-serif;
                      color: var(--text);
                    }
                    /* ── header ── */
                    .head {
                      padding: 14px 16px 12px;
                      background: var(--surface);
                      border-bottom: 1px solid var(--border);
                    }
                    h1 { font-size: 18px; font-weight: 700; }
                    .sub { margin-top: 4px; font-size: 13px; color: var(--muted); }
                    /* ── carousel wrapper ── */
                    .carousel {
                      flex: 1;
                      display: flex;
                      flex-direction: column;
                      align-items: center;
                      justify-content: center;
                      padding: 16px;
                      gap: 12px;
                    }
                    .slide-wrap {
                      position: relative;
                      width: 100%%;
                      max-width: 540px;
                      background: var(--surface);
                      border-radius: 18px;
                      overflow: hidden;
                      box-shadow: 0 8px 32px rgba(15,23,42,0.10);
                    }
                    .slide-img {
                      display: block;
                      width: 100%%;
                      height: 360px;
                      object-fit: cover;
                      background: #e2e8f0;
                      transition: opacity .25s ease;
                    }
                    /* main badge */
                    .main-badge {
                      position: absolute;
                      top: 12px; left: 12px;
                      padding: 4px 10px;
                      border-radius: 999px;
                      font-size: 12px; font-weight: 700;
                      color: #065f46; background: #d1fae5;
                      pointer-events: none;
                    }
                    /* prev / next arrows */
                    .arrow {
                      position: absolute;
                      top: 50%%; transform: translateY(-50%%);
                      width: 40px; height: 40px;
                      border-radius: 50%%;
                      border: none;
                      background: var(--arrow-bg);
                      box-shadow: 0 2px 8px rgba(0,0,0,.18);
                      cursor: pointer;
                      display: flex; align-items: center; justify-content: center;
                      font-size: 18px; color: var(--text);
                      transition: background .15s;
                      -webkit-tap-highlight-color: transparent;
                    }
                    .arrow:active { background: #e2e8f0; }
                    .arrow.prev { left: 10px; }
                    .arrow.next { right: 10px; }
                    .arrow:disabled { opacity: .3; cursor: default; }
                    /* caption + counter */
                    .slide-footer {
                      padding: 10px 14px;
                      display: flex;
                      align-items: center;
                      justify-content: space-between;
                      background: var(--surface);
                      font-size: 13px;
                    }
                    .caption { color: var(--muted); flex: 1; }
                    .counter { color: var(--muted); white-space: nowrap; margin-left: 10px; }
                    /* dot strip */
                    .dots {
                      display: flex; gap: 6px; flex-wrap: wrap;
                      justify-content: center;
                      max-width: 540px;
                    }
                    .dot {
                      width: 8px; height: 8px; border-radius: 50%%;
                      background: var(--border);
                      border: none; cursor: pointer;
                      padding: 0;
                      transition: background .2s, transform .2s;
                      -webkit-tap-highlight-color: transparent;
                    }
                    .dot.active { background: var(--accent); transform: scale(1.35); }
                    /* loading / error */
                    .msg {
                      width: 100%%; max-width: 540px;
                      padding: 20px; text-align: center;
                      color: var(--muted); font-size: 15px;
                    }
                    .err {
                      color: #b91c1c; background: #fee2e2;
                      border: 1px solid #fecaca;
                      border-radius: 10px; padding: 12px 16px;
                    }
                  </style>
                </head>
                <body>
                  <div class="head">
                    <h1 id="title">Pharmacy Photos</h1>
                    <div id="sub" class="sub">Loading…</div>
                  </div>

                  <div class="carousel">
                    <div id="msg" class="msg">Loading photos…</div>

                    <!-- filled by JS -->
                    <div id="slide-wrap" class="slide-wrap" style="display:none">
                      <img id="slide-img" class="slide-img" alt="Pharmacy photo" />
                      <div id="main-badge" class="main-badge" style="display:none">⭐ Main</div>
                      <button id="btn-prev" class="arrow prev" aria-label="Previous">&#8249;</button>
                      <button id="btn-next" class="arrow next" aria-label="Next">&#8250;</button>
                      <div class="slide-footer">
                        <span id="caption" class="caption"></span>
                        <span id="counter" class="counter"></span>
                      </div>
                    </div>

                    <div id="dots" class="dots"></div>
                  </div>

                  <script>
                    (async function () {
                      const fallbackId = %d;
                      const qpId = Number(new URLSearchParams(window.location.search).get('pharmacyId'));
                      const pharmacyId = Number.isFinite(qpId) && qpId > 0 ? qpId : fallbackId;

                      const titleEl   = document.getElementById('title');
                      const subEl     = document.getElementById('sub');
                      const msgEl     = document.getElementById('msg');
                      const wrapEl    = document.getElementById('slide-wrap');
                      const imgEl     = document.getElementById('slide-img');
                      const badgeEl   = document.getElementById('main-badge');
                      const prevBtn   = document.getElementById('btn-prev');
                      const nextBtn   = document.getElementById('btn-next');
                      const captionEl = document.getElementById('caption');
                      const counterEl = document.getElementById('counter');
                      const dotsEl    = document.getElementById('dots');

                      let photos = [];
                      let current = 0;

                      function imgUrl(photo) {
                        return '/api/miniapp/pharmacies/' + pharmacyId + '/photo/' + photo.photoId + '/image';
                      }

                      function show(index) {
                        current = Math.max(0, Math.min(index, photos.length - 1));
                        const photo = photos[current];

                        imgEl.style.opacity = '0';
                        imgEl.src = imgUrl(photo);
                        imgEl.onload = function () { imgEl.style.opacity = '1'; };

                        badgeEl.style.display = photo.mainPhoto ? 'block' : 'none';
                        captionEl.textContent  = photo.caption || '';
                        counterEl.textContent  = (current + 1) + ' / ' + photos.length;

                        prevBtn.disabled = current === 0;
                        nextBtn.disabled = current === photos.length - 1;

                        dotsEl.querySelectorAll('.dot').forEach(function (d, i) {
                          d.classList.toggle('active', i === current);
                        });
                      }

                      prevBtn.addEventListener('click', function () { show(current - 1); });
                      nextBtn.addEventListener('click', function () { show(current + 1); });

                      // swipe support
                      let touchX = null;
                      wrapEl.addEventListener('touchstart', function (e) { touchX = e.touches[0].clientX; }, { passive: true });
                      wrapEl.addEventListener('touchend', function (e) {
                        if (touchX === null) return;
                        const dx = e.changedTouches[0].clientX - touchX;
                        touchX = null;
                        if (Math.abs(dx) < 40) return;
                        show(dx < 0 ? current + 1 : current - 1);
                      }, { passive: true });

                      try {
                        const res = await fetch('/api/miniapp/pharmacies/' + pharmacyId + '/photos', {
                          headers: { 'ngrok-skip-browser-warning': 'true' }
                        });
                        if (!res.ok) throw new Error('Failed to load photos (' + res.status + ')');

                        const data = await res.json();
                        titleEl.textContent = data.pharmacyName ? data.pharmacyName + ' — Photos' : 'Pharmacy Photos';
                        subEl.textContent   = data.pharmacyName || ('Pharmacy #' + pharmacyId);

                        photos = Array.isArray(data.photos) ? data.photos : [];
                        if (!photos.length) {
                          msgEl.textContent = 'No photos available yet.';
                          return;
                        }

                        // build dots
                        photos.forEach(function (_, i) {
                          const dot = document.createElement('button');
                          dot.className = 'dot' + (i === 0 ? ' active' : '');
                          dot.setAttribute('aria-label', 'Photo ' + (i + 1));
                          dot.addEventListener('click', function () { show(i); });
                          dotsEl.appendChild(dot);
                        });

                        msgEl.style.display  = 'none';
                        wrapEl.style.display = 'block';
                        show(0);

                      } catch (err) {
                        msgEl.innerHTML = '<div class="err">' + (err.message || 'Unable to load photos.') + '</div>';
                      }
                    })();
                  </script>
                </body>
                </html>
                """.formatted(pharmacyId);
    }

    @GetMapping(value = "/medicine/photos", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String medicinePhotosPage() {
        return medicinePhotosPageHtml(0L);
    }

    @GetMapping(value = "/medicine/{medicineId}/photos", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String medicinePhotosPageById(@PathVariable Long medicineId) {
        return medicinePhotosPageHtml(medicineId == null ? 0L : medicineId);
    }

    private String medicinePhotosPageHtml(Long fallbackMedicineId) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>Medicine Photos</title>
                  <style>
                    :root {
                      --bg: #f0f4f8;
                      --surface: #ffffff;
                      --text: #0f172a;
                      --muted: #475569;
                      --accent: #0ea5a4;
                      --border: #d9e2ec;
                      --arrow-bg: rgba(255,255,255,0.85);
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                      min-height: 100dvh;
                      display: flex;
                      flex-direction: column;
                      background: var(--bg);
                      font-family: "Segoe UI", Tahoma, Geneva, Verdana, sans-serif;
                      color: var(--text);
                    }
                    .head {
                      padding: 14px 16px 12px;
                      background: var(--surface);
                      border-bottom: 1px solid var(--border);
                    }
                    h1 { font-size: 18px; font-weight: 700; }
                    .sub { margin-top: 4px; font-size: 13px; color: var(--muted); }
                    .carousel {
                      flex: 1;
                      display: flex;
                      flex-direction: column;
                      align-items: center;
                      justify-content: center;
                      padding: 16px;
                      gap: 12px;
                    }
                    .slide-wrap {
                      position: relative;
                      width: 100%%;
                      max-width: 540px;
                      background: var(--surface);
                      border-radius: 18px;
                      overflow: hidden;
                      box-shadow: 0 8px 32px rgba(15,23,42,0.10);
                    }
                    .slide-img {
                      display: block;
                      width: 100%%;
                      height: 360px;
                      object-fit: cover;
                      background: #e2e8f0;
                      transition: opacity .25s ease;
                    }
                    .main-badge {
                      position: absolute;
                      top: 12px; left: 12px;
                      padding: 4px 10px;
                      border-radius: 999px;
                      font-size: 12px; font-weight: 700;
                      color: #065f46; background: #d1fae5;
                      pointer-events: none;
                    }
                    .arrow {
                      position: absolute;
                      top: 50%%; transform: translateY(-50%%);
                      width: 40px; height: 40px;
                      border-radius: 50%%;
                      border: none;
                      background: var(--arrow-bg);
                      box-shadow: 0 2px 8px rgba(0,0,0,.18);
                      cursor: pointer;
                      display: flex; align-items: center; justify-content: center;
                      font-size: 18px; color: var(--text);
                      transition: background .15s;
                      -webkit-tap-highlight-color: transparent;
                    }
                    .arrow:active { background: #e2e8f0; }
                    .arrow.prev { left: 10px; }
                    .arrow.next { right: 10px; }
                    .arrow:disabled { opacity: .3; cursor: default; }
                    .slide-footer {
                      padding: 10px 14px;
                      display: flex;
                      align-items: center;
                      justify-content: space-between;
                      background: var(--surface);
                      font-size: 13px;
                    }
                    .caption { color: var(--muted); flex: 1; }
                    .counter { color: var(--muted); white-space: nowrap; margin-left: 10px; }
                    .dots {
                      display: flex; gap: 6px; flex-wrap: wrap;
                      justify-content: center;
                      max-width: 540px;
                    }
                    .dot {
                      width: 8px; height: 8px; border-radius: 50%%;
                      background: var(--border);
                      border: none; cursor: pointer;
                      padding: 0;
                      transition: background .2s, transform .2s;
                      -webkit-tap-highlight-color: transparent;
                    }
                    .dot.active { background: var(--accent); transform: scale(1.35); }
                    .msg {
                      width: 100%%; max-width: 540px;
                      padding: 20px; text-align: center;
                      color: var(--muted); font-size: 15px;
                    }
                    .err {
                      color: #b91c1c; background: #fee2e2;
                      border: 1px solid #fecaca;
                      border-radius: 10px; padding: 12px 16px;
                    }
                  </style>
                </head>
                <body>
                  <div class="head">
                    <h1 id="title">Medicine Photos</h1>
                    <div id="sub" class="sub">Loading…</div>
                  </div>

                  <div class="carousel">
                    <div id="msg" class="msg">Loading photos…</div>

                    <div id="slide-wrap" class="slide-wrap" style="display:none">
                      <img id="slide-img" class="slide-img" alt="Medicine photo" />
                      <div id="main-badge" class="main-badge" style="display:none">⭐ Main</div>
                      <button id="btn-prev" class="arrow prev" aria-label="Previous">&#8249;</button>
                      <button id="btn-next" class="arrow next" aria-label="Next">&#8250;</button>
                      <div class="slide-footer">
                        <span id="caption" class="caption"></span>
                        <span id="counter" class="counter"></span>
                      </div>
                    </div>

                    <div id="dots" class="dots"></div>
                  </div>

                  <script>
                    (async function () {
                      const params = new URLSearchParams(window.location.search);
                      const fallbackMedicineId = %d;
                      const qpMedicineId = Number(params.get('medicineId'));
                      const qpPharmacyId = Number(params.get('pharmacyId'));
                      const qpMedicineName = params.get('medicineName');

                      const titleEl   = document.getElementById('title');
                      const subEl     = document.getElementById('sub');
                      const msgEl     = document.getElementById('msg');
                      const wrapEl    = document.getElementById('slide-wrap');
                      const imgEl     = document.getElementById('slide-img');
                      const badgeEl   = document.getElementById('main-badge');
                      const prevBtn   = document.getElementById('btn-prev');
                      const nextBtn   = document.getElementById('btn-next');
                      const captionEl = document.getElementById('caption');
                      const counterEl = document.getElementById('counter');
                      const dotsEl    = document.getElementById('dots');

                      let photos = [];
                      let current = 0;
                      let medicineId = Number.isFinite(qpMedicineId) && qpMedicineId > 0 ? qpMedicineId : fallbackMedicineId;

                      async function resolveMedicineId() {
                        if (medicineId > 0) {
                          return medicineId;
                        }

                        if (!Number.isFinite(qpPharmacyId) || qpPharmacyId <= 0 || !qpMedicineName) {
                          throw new Error('medicineId or (pharmacyId + medicineName) is required');
                        }

                        const url = '/api/miniapp/medicines/resolve?pharmacyId='
                            + qpPharmacyId
                            + '&medicineName='
                            + encodeURIComponent(qpMedicineName);

                        const res = await fetch(url, { headers: { 'ngrok-skip-browser-warning': 'true' } });
                        if (!res.ok) {
                          throw new Error('Failed to resolve medicine id (' + res.status + ')');
                        }
                        const body = await res.json();
                        const resolved = Number(body.medicineId);
                        if (!Number.isFinite(resolved) || resolved <= 0) {
                          throw new Error('Invalid medicine id response');
                        }

                        medicineId = resolved;
                        return medicineId;
                      }

                      function imgUrl(photo) {
                        return '/api/miniapp/medicines/' + medicineId + '/photo/' + photo.photoId + '/image';
                      }

                      function show(index) {
                        current = Math.max(0, Math.min(index, photos.length - 1));
                        const photo = photos[current];

                        imgEl.style.opacity = '0';
                        imgEl.src = imgUrl(photo);
                        imgEl.onload = function () { imgEl.style.opacity = '1'; };

                        badgeEl.style.display = photo.mainPhoto ? 'block' : 'none';
                        captionEl.textContent = photo.caption || '';
                        counterEl.textContent = (current + 1) + ' / ' + photos.length;

                        prevBtn.disabled = current === 0;
                        nextBtn.disabled = current === photos.length - 1;

                        dotsEl.querySelectorAll('.dot').forEach(function (d, i) {
                          d.classList.toggle('active', i === current);
                        });
                      }

                      prevBtn.addEventListener('click', function () { show(current - 1); });
                      nextBtn.addEventListener('click', function () { show(current + 1); });

                      let touchX = null;
                      wrapEl.addEventListener('touchstart', function (e) { touchX = e.touches[0].clientX; }, { passive: true });
                      wrapEl.addEventListener('touchend', function (e) {
                        if (touchX === null) return;
                        const dx = e.changedTouches[0].clientX - touchX;
                        touchX = null;
                        if (Math.abs(dx) < 40) return;
                        show(dx < 0 ? current + 1 : current - 1);
                      }, { passive: true });

                      try {
                        await resolveMedicineId();

                        const res = await fetch('/api/miniapp/medicines/' + medicineId + '/photos', {
                          headers: { 'ngrok-skip-browser-warning': 'true' }
                        });
                        if (!res.ok) throw new Error('Failed to load photos (' + res.status + ')');

                        const data = await res.json();
                        titleEl.textContent = data.medicineName ? data.medicineName + ' — Photos' : 'Medicine Photos';
                        subEl.textContent = data.medicineName || ('Medicine #' + medicineId);

                        photos = Array.isArray(data.photos) ? data.photos : [];
                        if (!photos.length) {
                          msgEl.textContent = 'No medicine photos available yet.';
                          return;
                        }

                        photos.forEach(function (_, i) {
                          const dot = document.createElement('button');
                          dot.className = 'dot' + (i === 0 ? ' active' : '');
                          dot.setAttribute('aria-label', 'Photo ' + (i + 1));
                          dot.addEventListener('click', function () { show(i); });
                          dotsEl.appendChild(dot);
                        });

                        msgEl.style.display = 'none';
                        wrapEl.style.display = 'block';
                        show(0);
                      } catch (err) {
                        msgEl.innerHTML = '<div class="err">' + (err.message || 'Unable to load photos.') + '</div>';
                      }
                    })();
                  </script>
                </body>
                </html>
                """.formatted(fallbackMedicineId);
    }

}
