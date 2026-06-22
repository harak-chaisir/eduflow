/* ==========================================================================
   EduFlow CRM — UI behaviour
   Small, dependency-free enhancements for the server-rendered pages.
   ========================================================================== */
(function () {
    "use strict";

    // ── HTMX: inject Spring Security CSRF header on every AJAX request ────────
    document.addEventListener("htmx:configRequest", function (evt) {
        var tokenEl  = document.querySelector('meta[name="csrf-token"]');
        var headerEl = document.querySelector('meta[name="csrf-header"]');
        if (tokenEl && headerEl) {
            evt.detail.headers[headerEl.content] = tokenEl.content;
        }
    });

    // ── HTMX: re-render Lucide icons after any partial swap ───────────────────
    document.addEventListener("htmx:afterSwap", function () {
        renderIcons();
        // Re-wire detail-page tabs (e.g. after an outerHTML panel swap).
        wireTabs();
        // Re-wire upload modal buttons inside freshly swapped content.
        wireUploadModal();
        // Re-wire verify modal decision logic if the modal was just populated.
        wireVerifyDecision();
        // Re-wire colour pickers inside freshly swapped content (e.g. settings card).
        wireColorPicker();
        // Re-wire the right-side drawer (open/close triggers may live in swapped content).
        wireDrawer();
        // Surface any flash messages carried in the swapped fragment as toasts.
        scanToasts();
    });

    // ── HTMX: close the apply-to-course drawer once the applications panel updates ──
    document.addEventListener("htmx:afterSwap", function (evt) {
        if (evt.detail.target && evt.detail.target.id === "applicationsPanel") {
            var drawer = document.getElementById("applyDrawer");
            if (drawer && drawer.classList.contains("is-open") && drawer._close) {
                drawer._close();
                // Reveal the Applications tab so the new application is visible.
                var appsTab = document.querySelector('[data-tab-target="#tab-applications"]');
                if (appsTab) { appsTab.click(); }
            }
        }
    });

    // ── HTMX: re-render Lucide icons after OOB swaps ─────────────────────────
    document.addEventListener("htmx:oobAfterSwap", function () {
        renderIcons();
        scanToasts();
    });

    // ── HTMX: surface a retry state if the lazy Documents tab fails to load ───
    // The tab uses `hx-trigger="click once"`, so a failed request leaves the
    // spinner stranded with no way to retry. Catch the failure and offer a retry
    // that re-issues the request directly (bypassing the spent `once` trigger).
    ["htmx:responseError", "htmx:sendError", "htmx:timeout"].forEach(function (ev) {
        document.addEventListener(ev, function (evt) {
            var target = evt.detail && evt.detail.target;
            if (!target || target.id !== "tab-documents") { return; }
            renderDocTabError(target);
        });
    });

    function renderDocTabError(panel) {
        var btn = document.getElementById("tab-documents-btn");
        var url = btn ? btn.getAttribute("hx-get") : null;
        panel.innerHTML =
            '<div class="status-note">' +
            '<i data-lucide="alert-triangle"></i>' +
            '<p>Couldn’t load documents. ' +
            '<button type="button" class="btn btn--ghost btn--sm" data-doc-retry>Retry</button></p>' +
            '</div>';
        var retry = panel.querySelector("[data-doc-retry]");
        if (retry && url && window.htmx) {
            retry.addEventListener("click", function () {
                panel.innerHTML =
                    '<div class="tab-loading"><i data-lucide="loader-circle" aria-hidden="true"></i>' +
                    '<span>Loading documents…</span></div>';
                renderIcons();
                window.htmx.ajax("GET", url, "#tab-documents");
            });
        }
        renderIcons();
    }

    // ── HTMX: close the upload modal after a successful dossierContent swap ───
    document.addEventListener("htmx:afterSwap", function (evt) {
        if (evt.detail.target && evt.detail.target.id === "dossierContent") {
            var uploadModal = document.getElementById("uploadModal");
            if (uploadModal && uploadModal.classList.contains("is-open")) {
                closeModal(uploadModal);
            }
            var verifyModal = document.getElementById("verifyModal");
            if (verifyModal && verifyModal.classList.contains("is-open")) {
                closeModal(verifyModal);
            }
        }
    });

    // ── HTMX: open the verify modal once its innerHTML has been swapped ────────
    document.addEventListener("htmx:afterSwap", function (evt) {
        if (evt.detail.target && evt.detail.target.id === "verifyModal") {
            var modal = document.getElementById("verifyModal");
            if (modal) {
                openModal(modal);
                wireVerifyDecision();
                renderIcons();
            }
        }
    });

    document.addEventListener("DOMContentLoaded", function () {
        renderIcons();
        wireSidebarToggle();
        wireUserMenu();
        wireTabs();
        wireFileInputs();
        wireUploadModal();
        wireColorPicker();
        wireDrawer();
        scanToasts();
        // Verify modal is now HTMX-loaded on demand — no static wire needed.
    });

    /* ── Toasts (Toastify) ────────────────────────────────────────────────────
       Pages/fragments emit hidden <span data-toast data-toast-type data-toast-message>
       markers wherever they previously rendered an inline flash. We pick them up on
       load and after each HTMX swap, show a toast, and remove the marker. */
    var TOAST_COLORS = {
        success: "#047857",   // --status-approved-fg
        error:   "#be123c",   // --status-rejected-fg
        info:    "#4f46e5"    // brand-600
    };

    function showToast(message, type) {
        if (!message || !window.Toastify) { return; }
        window.Toastify({
            text: message,
            duration: 4500,
            gravity: "top",
            position: "right",
            close: true,
            stopOnFocus: true,
            style: {
                background: TOAST_COLORS[type] || TOAST_COLORS.info,
                borderRadius: "10px",
                boxShadow: "0 6px 20px rgba(15, 23, 42, .18)",
                fontWeight: "600"
            }
        }).showToast();
    }

    function scanToasts(root) {
        (root || document).querySelectorAll("[data-toast]").forEach(function (el) {
            showToast(el.getAttribute("data-toast-message"), el.getAttribute("data-toast-type"));
            if (el.parentNode) { el.parentNode.removeChild(el); }
        });
    }

    /* Two-way sync between a native colour <input type="color"> and its editable hex text
       field, so the swatch and the typed value always agree. Idempotent — safe to re-run
       after HTMX swaps. */
    function wireColorPicker() {
        document.querySelectorAll("[data-color-field]").forEach(function (field) {
            if (field.dataset.colorWired === "1") { return; }
            var picker = field.querySelector("[data-color-picker]");
            var hex = field.querySelector("[data-color-hex]");
            if (!picker || !hex) { return; }

            picker.addEventListener("input", function () {
                hex.value = picker.value;
            });
            hex.addEventListener("input", function () {
                var v = hex.value.trim();
                if (/^#?[0-9a-fA-F]{6}$/.test(v)) {
                    picker.value = v.charAt(0) === "#" ? v : "#" + v;
                }
            });
            field.dataset.colorWired = "1";
        });
    }

    /* Topbar account dropdown: toggle on click, close on outside-click / Escape. */
    function wireUserMenu() {
        var menu = document.querySelector("[data-user-menu]");
        if (!menu) { return; }
        var trigger = menu.querySelector("[data-user-menu-toggle]");
        var panel = menu.querySelector("[data-user-menu-panel]");
        if (!trigger || !panel) { return; }

        function open() {
            panel.hidden = false;
            trigger.setAttribute("aria-expanded", "true");
        }
        function close() {
            panel.hidden = true;
            trigger.setAttribute("aria-expanded", "false");
        }

        trigger.addEventListener("click", function (e) {
            e.stopPropagation();
            if (panel.hidden) { open(); } else { close(); }
        });
        document.addEventListener("click", function (e) {
            if (!menu.contains(e.target)) { close(); }
        });
        document.addEventListener("keydown", function (e) {
            if (e.key === "Escape") { close(); }
        });
    }

    /* Right-side slide-over drawer.
       A [data-drawer] element is the backdrop; its inner .drawer is the panel.
       [data-drawer-open="#drawerId"] buttons open it; [data-drawer-close] closes it,
       as does a backdrop click or Escape. Open/close methods are stashed on the
       element (_open/_close) so other handlers (e.g. HTMX swaps) can drive it.
       Idempotent — safe to re-run on DOMContentLoaded and after every HTMX swap. */
    function wireDrawer() {
        var FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]):not([hidden]),' +
            'select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

        document.querySelectorAll("[data-drawer]").forEach(function (backdrop) {
            if (backdrop.dataset.drawerWired === "1") { return; }
            backdrop.dataset.drawerWired = "1";

            backdrop._open = function () {
                backdrop._previousFocus = document.activeElement;
                backdrop.classList.add("is-open");
                backdrop.removeAttribute("aria-hidden");
                var focusable = backdrop.querySelectorAll(FOCUSABLE);
                if (focusable.length) { focusable[0].focus(); }
                var trap = makeFocusTrap(backdrop);
                backdrop._focusTrap = trap;
                document.addEventListener("keydown", trap);
            };
            backdrop._close = function () {
                backdrop.classList.remove("is-open");
                backdrop.setAttribute("aria-hidden", "true");
                if (backdrop._focusTrap) {
                    document.removeEventListener("keydown", backdrop._focusTrap);
                    backdrop._focusTrap = null;
                }
                if (backdrop._previousFocus && backdrop._previousFocus.focus) {
                    backdrop._previousFocus.focus();
                    backdrop._previousFocus = null;
                }
            };

            // Click on the backdrop itself (not the panel) closes the drawer.
            backdrop.addEventListener("click", function (e) {
                if (e.target === backdrop) { backdrop._close(); }
            });
            document.addEventListener("keydown", function (e) {
                if (e.key === "Escape" && backdrop.classList.contains("is-open")) {
                    backdrop._close();
                }
            });
        });

        document.querySelectorAll("[data-drawer-open]").forEach(function (btn) {
            if (btn.dataset.drawerTriggerWired === "1") { return; }
            btn.dataset.drawerTriggerWired = "1";
            btn.addEventListener("click", function () {
                var target = document.querySelector(btn.getAttribute("data-drawer-open"));
                if (target && target._open) { target._open(); }
            });
        });

        document.querySelectorAll("[data-drawer-close]").forEach(function (btn) {
            if (btn.dataset.drawerCloseWired === "1") { return; }
            btn.dataset.drawerCloseWired = "1";
            btn.addEventListener("click", function () {
                var backdrop = btn.closest("[data-drawer]");
                if (backdrop && backdrop._close) { backdrop._close(); }
            });
        });
    }

    // ── Focus trap ────────────────────────────────────────────────────────────
    function makeFocusTrap(container) {
        return function (e) {
            if (e.key !== "Tab") { return; }
            var focusable = container.querySelectorAll(
                'a[href], button:not([disabled]), input:not([disabled]):not([hidden]),' +
                'select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
            );
            var first = focusable[0];
            var last  = focusable[focusable.length - 1];
            if (!first) { e.preventDefault(); return; }
            if (e.shiftKey) {
                if (document.activeElement === first) { e.preventDefault(); last.focus(); }
            } else {
                if (document.activeElement === last)  { e.preventDefault(); first.focus(); }
            }
        };
    }

    /* Replace every <i data-lucide="…"> with its SVG icon. */
    function renderIcons() {
        if (window.lucide && typeof window.lucide.createIcons === "function") {
            window.lucide.createIcons();
        }
    }

    /* Detail-page sub-navigation (tabbed panels).
       A [data-tabs] container holds [data-tab-target="#panelId"] buttons; clicking one
       reveals its target panel and hides the siblings — no page reload. The active tab is
       mirrored into location.hash (via data-tab-name) so it survives HTMX panel swaps.
       Idempotent: safe to re-run on DOMContentLoaded and after every HTMX swap. */
    function wireTabs() {
        document.querySelectorAll("[data-tabs]").forEach(function (group) {
            var tabs = Array.prototype.slice.call(group.querySelectorAll("[data-tab-target]"));
            if (!tabs.length) { return; }

            function show(tab, updateHash) {
                tabs.forEach(function (t) {
                    var isActive = t === tab;
                    t.classList.toggle("is-active", isActive);
                    t.setAttribute("aria-selected", isActive ? "true" : "false");
                    var panel = document.querySelector(t.getAttribute("data-tab-target"));
                    if (panel) { panel.hidden = !isActive; }
                });
                var name = tab.getAttribute("data-tab-name");
                if (updateHash && name) {
                    history.replaceState(null, "", "#" + name);
                }
            }

            tabs.forEach(function (tab) {
                if (tab.dataset.tabWired !== "1") {
                    tab.dataset.tabWired = "1";
                    tab.addEventListener("click", function () { show(tab, true); });
                }
            });

            // Initial selection: location.hash > server-marked is-active > first tab.
            var initial = null;
            var hash = (location.hash || "").replace(/^#/, "");
            if (hash) {
                initial = tabs.filter(function (t) {
                    return t.getAttribute("data-tab-name") === hash;
                })[0] || null;
            }
            if (!initial) {
                initial = tabs.filter(function (t) {
                    return t.classList.contains("is-active");
                })[0] || tabs[0];
            }
            show(initial, false);
        });
    }

    /* Mobile: show/hide the sidebar. */
    function wireSidebarToggle() {
        var toggle = document.querySelector('[data-toggle="sidebar"]');
        var sidebar = document.getElementById("sidebar");
        if (!toggle || !sidebar) { return; }
        toggle.addEventListener("click", function () {
            sidebar.classList.toggle("is-open");
        });
    }

    /* Reflect the chosen file name next to a styled file button, and auto-submit
       re-upload forms as soon as a file is picked. */
    function wireFileInputs() {
        document.querySelectorAll("[data-file-input]").forEach(function (input) {
            input.addEventListener("change", function () {
                var fileName = input.files && input.files.length
                    ? input.files[0].name
                    : "No file selected";

                var field = input.closest(".file-field");
                if (field) {
                    var label = field.querySelector("[data-file-name]");
                    if (label) { label.textContent = fileName; }
                }

                if (input.hasAttribute("data-autosubmit") && input.files && input.files.length) {
                    input.closest("form").submit();
                }
            });
        });
    }

    /* Upload modal with drag-and-drop.
       Called on DOMContentLoaded AND after every HTMX swap so new
       [data-upload-open] buttons injected into swapped content are wired. */
    function wireUploadModal() {
        var modal = document.getElementById("uploadModal");
        if (!modal) { return; }
        var typeSelect = document.getElementById("uploadType");
        var fileInput  = document.getElementById("uploadFile");
        var nameEl     = modal.querySelector("[data-upload-filename]");
        var submitBtn  = modal.querySelector("[data-upload-submit]");
        var dropzone   = modal.querySelector("[data-dropzone]");

        function reset() {
            if (fileInput)  { fileInput.value = ""; }
            if (nameEl)     { nameEl.textContent = "No file selected"; nameEl.classList.remove("is-set"); }
            if (submitBtn)  { submitBtn.disabled = true; }
            if (dropzone)   { dropzone.classList.remove("is-dragover"); }
        }

        function showSelectedFile() {
            var file = fileInput && fileInput.files && fileInput.files[0];
            if (file) {
                nameEl.textContent = file.name + " (" + formatBytes(file.size) + ")";
                nameEl.classList.add("is-set");
                submitBtn.disabled = false;
                submitBtn.removeAttribute("aria-disabled");
            } else {
                reset();
            }
        }

        // Avoid duplicate listeners — remove old ones via cloneNode trick.
        document.querySelectorAll("[data-upload-open]").forEach(function (button) {
            var fresh = button.cloneNode(true);
            button.parentNode.replaceChild(fresh, button);
            fresh.addEventListener("click", function () {
                reset();
                var type = fresh.getAttribute("data-type");
                if (type && typeSelect) { typeSelect.value = type; }
                openModal(modal);
            });
        });

        if (fileInput) {
            fileInput.addEventListener("change", showSelectedFile);
        }

        if (dropzone) {
            ["dragenter", "dragover"].forEach(function (ev) {
                dropzone.addEventListener(ev, function (e) {
                    e.preventDefault();
                    dropzone.classList.add("is-dragover");
                });
            });
            ["dragleave", "dragend"].forEach(function (ev) {
                dropzone.addEventListener(ev, function () {
                    dropzone.classList.remove("is-dragover");
                });
            });
            dropzone.addEventListener("drop", function (e) {
                e.preventDefault();
                dropzone.classList.remove("is-dragover");
                if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length) {
                    fileInput.files = e.dataTransfer.files;
                    showSelectedFile();
                }
            });
        }

        modal.querySelectorAll("[data-modal-close]").forEach(function (closer) {
            closer.addEventListener("click", function () { closeModal(modal); });
        });
        modal.addEventListener("click", function (event) {
            if (event.target === modal) { closeModal(modal); }
        });
        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape" && modal.classList.contains("is-open")) {
                closeModal(modal);
            }
        });
    }

    /**
     * Wires the dynamic `required` attribute on the remarks field inside
     * the verify form. Called after HTMX loads the verify form into the modal.
     */
    function wireVerifyDecision() {
        var modal          = document.getElementById("verifyModal");
        if (!modal) { return; }
        var decisionSelect = modal.querySelector("[data-verify-decision]");
        var remarksArea    = modal.querySelector("[data-verify-remarks]");
        var remarksHint    = modal.querySelector("#remarksRequiredHint");

        function syncRemarksRequired() {
            if (!decisionSelect || !remarksArea) { return; }
            var needsRemarks = decisionSelect.value !== "APPROVE";
            remarksArea.required = needsRemarks;
            if (remarksHint) {
                remarksHint.textContent = needsRemarks ? "(required)" : "(optional for Approve)";
            }
        }

        if (decisionSelect) {
            decisionSelect.addEventListener("change", syncRemarksRequired);
        }

        modal.querySelectorAll("[data-modal-close]").forEach(function (closer) {
            closer.addEventListener("click", function () { closeModal(modal); });
        });
        modal.addEventListener("click", function (event) {
            if (event.target === modal) { closeModal(modal); }
        });
        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape" && modal.classList.contains("is-open")) {
                closeModal(modal);
            }
        });
    }

    function formatBytes(bytes) {
        if (bytes < 1024)    { return bytes + " B"; }
        if (bytes < 1048576) { return (bytes / 1024).toFixed(1) + " KB"; }
        return (bytes / 1048576).toFixed(1) + " MB";
    }

    // ── Modal open / close with focus management ──────────────────────────────

    function openModal(modal) {
        var previouslyFocused = document.activeElement;
        modal.classList.add("is-open");
        modal.removeAttribute("aria-hidden");
        var focusable = modal.querySelectorAll(
            'a[href], button:not([disabled]), input:not([disabled]):not([hidden]),' +
            'select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
        );
        if (focusable.length) { focusable[0].focus(); }
        var trap = makeFocusTrap(modal);
        modal._focusTrap = trap;
        modal._previousFocus = previouslyFocused;
        document.addEventListener("keydown", trap);
    }

    function closeModal(modal) {
        modal.classList.remove("is-open");
        modal.setAttribute("aria-hidden", "true");
        if (modal._focusTrap) {
            document.removeEventListener("keydown", modal._focusTrap);
            modal._focusTrap = null;
        }
        if (modal._previousFocus && modal._previousFocus.focus) {
            modal._previousFocus.focus();
            modal._previousFocus = null;
        }
    }
})();
