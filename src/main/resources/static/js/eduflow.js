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
        // Re-wire upload modal buttons inside freshly swapped content.
        wireUploadModal();
        // Re-wire verify modal decision logic if the modal was just populated.
        wireVerifyDecision();
        // Re-wire colour pickers inside freshly swapped content (e.g. settings card).
        wireColorPicker();
        // Surface any flash messages carried in the swapped fragment as toasts.
        scanToasts();
    });

    // ── HTMX: re-render Lucide icons after OOB swaps ─────────────────────────
    document.addEventListener("htmx:oobAfterSwap", function () {
        renderIcons();
        scanToasts();
    });

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
        wireFileInputs();
        wireUploadModal();
        wireColorPicker();
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
