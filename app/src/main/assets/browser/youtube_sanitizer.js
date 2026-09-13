/**
 * BlockAds - YouTube Player Sanitizer & Ad Blocker Scriptlet
 * Inspired by AdGuard CoreLibs & uBlock Origin scriptlets.
 * Strips ad metadata from ytInitialPlayerResponse, hooks fetch/XHR,
 * provides high-speed fallback skip, and auto-adapts video to Picture-in-Picture window.
 */
(function() {
    'use strict';
    if (window.__blockads_yt_sanitizer_injected) return;
    window.__blockads_yt_sanitizer_injected = true;

    // Neutralize YouTube ServiceWorker so fetch/XHR hooks intercept player responses
    if (typeof navigator !== "undefined" && "serviceWorker" in navigator && location.hostname.indexOf("youtube.com") !== -1) {
        try {
            navigator.serviceWorker.getRegistrations().then(function(regs) {
                for (var i = 0; i < regs.length; i++) { regs[i].unregister(); }
            }).catch(function(){});
            navigator.serviceWorker.register = function() {
                return Promise.reject(new Error("ServiceWorker blocked by BlockAds"));
            };
        } catch (e) {}
    }

    function stripFeedAdRenderers(obj) {
        if (!obj || typeof obj !== 'object') return;
        if (Array.isArray(obj)) {
            for (var i = obj.length - 1; i >= 0; i--) {
                var item = obj[i];
                if (item && typeof item === 'object') {
                    if (item.adSlotRenderer || item.promotedSparklesWebRenderer ||
                        item.inFeedAdLayoutRenderer || item.statementBannerRenderer ||
                        item.brandVideoSingletonRenderer || item.promotedVideoRenderer ||
                        item.brandVideoShelfRenderer) {
                        obj.splice(i, 1);
                        continue;
                    }
                }
                stripFeedAdRenderers(obj[i]);
            }
        } else {
            for (var k in obj) {
                if (Object.prototype.hasOwnProperty.call(obj, k)) {
                    if (k === 'adSlotRenderer' || k === 'promotedSparklesWebRenderer' ||
                        k === 'inFeedAdLayoutRenderer' || k === 'statementBannerRenderer' ||
                        k === 'brandVideoSingletonRenderer' || k === 'promotedVideoRenderer' ||
                        k === 'brandVideoShelfRenderer') {
                        delete obj[k];
                    } else if (typeof obj[k] === 'object') {
                        stripFeedAdRenderers(obj[k]);
                    }
                }
            }
        }
    }

    function sanitizeData(data) {
        if (!data || typeof data !== 'object') return data;
        try {
            if (data.playerAds) delete data.playerAds;
            if (data.adPlacements) delete data.adPlacements;
            if (data.adSlots) delete data.adSlots;
            if (data.adPlacementConfig) delete data.adPlacementConfig;
            if (data.masthead) delete data.masthead;

            // Remove ads in auxiliaryUi (interstitial dialogs / upsells)
            if (data.auxiliaryUi && data.auxiliaryUi.messageRenderers) {
                var mr = data.auxiliaryUi.messageRenderers;
                if (mr.upsellDialogRenderer) delete mr.upsellDialogRenderer;
            }

            // Clean playback tracking telemetry
            if (data.playbackTracking) {
                delete data.playbackTracking.videostatsPlaybackUrl;
                delete data.playbackTracking.videostatsDelayplayUrl;
                delete data.playbackTracking.videostatsWatchtimeUrl;
            }

            // Strip home feed, search, and browse ad renderers
            stripFeedAdRenderers(data);
        } catch (e) {}
        return data;
    }

    // 1. Trap window.ytInitialPlayerResponse
    var _ytInitialPlayerResponse = window.ytInitialPlayerResponse;
    try {
        Object.defineProperty(window, 'ytInitialPlayerResponse', {
            configurable: true,
            get: function() { return _ytInitialPlayerResponse; },
            set: function(val) { _ytInitialPlayerResponse = sanitizeData(val); }
        });
    } catch(e) {}
    if (window.ytInitialPlayerResponse) {
        sanitizeData(window.ytInitialPlayerResponse);
    }

    // 2. Trap window.ytInitialData
    var _ytInitialData = window.ytInitialData;
    try {
        Object.defineProperty(window, 'ytInitialData', {
            configurable: true,
            get: function() { return _ytInitialData; },
            set: function(val) { _ytInitialData = sanitizeData(val); }
        });
    } catch(e) {}
    if (window.ytInitialData) {
        sanitizeData(window.ytInitialData);
    }

    // 3. Hook window.fetch for dynamic /youtubei/v1/player calls
    var originalFetch = window.fetch;
    if (originalFetch) {
        window.fetch = async function() {
            var response = await originalFetch.apply(this, arguments);
            var url = typeof arguments[0] === 'string' ? arguments[0] : (arguments[0] && arguments[0].url);
            if (url && typeof url === 'string' && (url.indexOf('/youtubei/v1/player') !== -1 || url.indexOf('/youtubei/v1/browse') !== -1 || url.indexOf('/youtubei/v1/next') !== -1 || url.indexOf('/youtubei/v1/search') !== -1)) {
                try {
                    var clone = response.clone();
                    var json = await clone.json();
                    var clean = sanitizeData(json);
                    return new Response(JSON.stringify(clean), {
                        status: response.status,
                        statusText: response.statusText,
                        headers: response.headers
                    });
                } catch(err) {}
            }
            return response;
        };
    }

    // 4. Hook XMLHttpRequest for player responses
    var originalXHROpen = XMLHttpRequest.prototype.open;
    var originalXHRSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function(method, url) {
        this._blockads_url = url;
        return originalXHROpen.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function() {
        if (this._blockads_url && typeof this._blockads_url === 'string' && (this._blockads_url.indexOf('/youtubei/v1/player') !== -1 || this._blockads_url.indexOf('/youtubei/v1/browse') !== -1 || this._blockads_url.indexOf('/youtubei/v1/next') !== -1 || this._blockads_url.indexOf('/youtubei/v1/search') !== -1)) {
            this.addEventListener('readystatechange', function() {
                if (this.readyState === 4 && this.responseText) {
                    try {
                        var data = JSON.parse(this.responseText);
                        sanitizeData(data);
                        var cleanStr = JSON.stringify(data);
                        Object.defineProperty(this, 'responseText', { value: cleanStr });
                        Object.defineProperty(this, 'response', { value: cleanStr });
                    } catch(e) {}
                }
            });
        }
        return originalXHRSend.apply(this, arguments);
    };

    // 5. Fallback auto-skip & fast-forward loop for live or stitched ads
    setInterval(function() {
        try {
            // Purge home feed & masthead ad banners from DOM
            var bannerSelectors = [
                'ytm-promoted-sparkles-web-renderer',
                'ytm-companion-ad-renderer',
                'ytm-ad-slot-renderer',
                'ytm-statement-banner-renderer',
                'ytm-brand-video-singleton-renderer',
                'ytm-in-feed-ad-layout-renderer',
                '#masthead-ad'
            ];
            for (var b = 0; b < bannerSelectors.length; b++) {
                var bEls = document.querySelectorAll(bannerSelectors[b]);
                for (var k = 0; k < bEls.length; k++) {
                    var p = bEls[k].closest('ytm-rich-item-renderer, ytm-rich-section-renderer, ytm-item-section-renderer') || bEls[k];
                    p.remove();
                }
            }
            var badges = document.querySelectorAll('yt-metadata-badge-renderer, ytm-badge-and-byline-renderer, badge-shape, .badge');
            for (var bi = 0; bi < badges.length; bi++) {
                var bt = (badges[bi].textContent || '').trim().toLowerCase();
                if (bt === 'sponsored' || bt === 'được tài trợ' || bt === 'quảng cáo' || bt === 'ad') {
                    var card = badges[bi].closest('ytm-rich-item-renderer, ytm-video-with-context-renderer, ytm-item-section-renderer, ytm-rich-section-renderer');
                    if (card) card.remove();
                }
            }

            // Click skip buttons
            var skipSelectors = [
                '.ytp-ad-skip-button',
                '.ytp-ad-skip-button-modern',
                '.ytp-skip-ad-button',
                '.ytm-skip-ad-button',
                '.ytp-ad-overlay-close-button'
            ];
            for (var i = 0; i < skipSelectors.length; i++) {
                var btn = document.querySelector(skipSelectors[i]);
                if (btn) {
                    btn.click();
                    break;
                }
            }

            // Speed up and mute ad video if showing
            var adShowing = document.querySelector('.ad-showing, .ytp-ad-player-overlay');
            var video = document.querySelector('video');
            if (adShowing && video && !isNaN(video.duration) && video.duration > 0) {
                video.muted = true;
                video.playbackRate = 16.0;
                video.currentTime = video.duration;
            }
        } catch (e) {}
    }, 250);

    // 6. Picture-in-Picture Video Box & Reparenting
    window.__blockads_set_pip = function(enable) {
        window.__blockads_force_pip = enable;
        var pipBox = document.getElementById('__blockads_pip_box');
        var v = document.querySelector('video');

        if (enable) {
            if (!v) return;
            if (!pipBox) {
                pipBox = document.createElement('div');
                pipBox.id = '__blockads_pip_box';
                pipBox.style.cssText = 'position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;z-index:2147483647!important;background:#000!important;display:flex!important;align-items:center!important;justify-content:center!important;margin:0!important;padding:0!important;overflow:hidden!important;';
                document.body.appendChild(pipBox);
            }
            if (!v._blockadsOrigParent) {
                v._blockadsOrigParent = v.parentNode;
                v._blockadsOrigSibling = v.nextSibling;
                v._blockadsOrigCss = v.style.cssText;
            }
            pipBox.appendChild(v);
            v.style.cssText = 'position:static!important;width:100vw!important;height:100vh!important;max-width:100vw!important;max-height:100vh!important;object-fit:contain!important;background:#000!important;display:block!important;margin:auto!important;visibility:visible!important;opacity:1!important;';
            if (v.paused) {
                v.play().catch(function(){});
            }
        } else {
            if (v && v._blockadsOrigParent) {
                v.style.cssText = v._blockadsOrigCss || '';
                try {
                    v._blockadsOrigParent.insertBefore(v, v._blockadsOrigSibling);
                } catch(e) {
                    v._blockadsOrigParent.appendChild(v);
                }
                delete v._blockadsOrigParent;
                delete v._blockadsOrigSibling;
                delete v._blockadsOrigCss;
            }
            if (pipBox) {
                pipBox.remove();
            }
        }
    };

    function checkPipResize() {
        var isPip = window.innerWidth <= 450 && window.innerHeight <= 350;
        if (isPip && !window.__blockads_force_pip) {
            window.__blockads_set_pip(true);
        } else if (!isPip && window.__blockads_force_pip === undefined) {
            window.__blockads_set_pip(false);
        }
    }

    window.addEventListener('resize', checkPipResize, { passive: true });
    window.addEventListener('orientationchange', checkPipResize, { passive: true });
})();
