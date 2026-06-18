# Tailwind CSS Implementation Plan — EduFlow CRM

> **Status:** In Progress
> **Created:** 2026-06-15
> **Scope:** Replace per-page CDN `<script>` tags with a proper build-time Tailwind CSS pipeline integrated into the Maven build, and migrate all student templates to the shared `layout.html` sidebar shell.

---

## 1. Problem Statement

The student pages (`list.html`, `detail.html`, `form.html`) currently:

- Load Tailwind CSS via the CDN play-script (`cdn.tailwindcss.com`) directly in each page `<head>`.
- Use a separate `navbar.html` top-bar fragment instead of the new sidebar `layout.html` fragment.
- Define an inline `tailwind.config` block that hard-codes `brand` colours as blue (`#2563eb`), which conflicts with the teal (`#0e5c54`) used in `eduflow.css`.
- Include a duplicated `<footer>` and standalone `<html>/<head>/<body>` boilerplate.

**Problems with the CDN approach in production:**

| Issue | Impact |
|---|---|
| No purging | Ships 3 MB+ of unused CSS |
| No caching | New script download on every page load |
| Inconsistent brand colour | Blue vs. teal mismatch with sidebar/design tokens |
| Layout drift | Each page is an island — no shared shell |

---

## 2. Target Architecture

```
Maven generate-resources phase
  └── frontend-maven-plugin
        ├── Install Node 20 LTS + npm
        ├── npm install  (installs tailwindcss)
        └── npm run build:css
              └── tailwindcss -i src/main/frontend/tailwind.css
                             -o src/main/resources/static/css/tailwind.css
                             --minify

Spring Boot serves:
  /css/eduflow.css    ← custom design-system classes (.sidebar, .card, .btn, …)
  /css/tailwind.css   ← generated utility CSS (purged to only used classes)

layout.html fragment:
  <link rel="stylesheet" href="/css/eduflow.css">
  <link rel="stylesheet" href="/css/tailwind.css">
```

All pages extend the shared `layout.html` fragment and pick up both stylesheets automatically.

---

## 3. Colour Token Mapping

`eduflow.css` defines teal design tokens. The `tailwind.config.js` maps the `brand` colour
extension to these same teal values so that classes like `bg-brand-600` and the CSS variable
`--brand` render the same colour everywhere.

| Tailwind class | Hex | CSS variable |
|---|---|---|
| `brand-50`  | `#e7f1ef` | `--brand-soft` |
| `brand-100` | `#cce3df` | — |
| `brand-500` | `#177367` | — |
| `brand-600` | `#0e5c54` | `--brand` |
| `brand-700` | `#0a443e` | `--brand-strong` |
| `brand-800` | `#072e2a` | — |

---

## 4. Files Created / Modified

| File | Action | Purpose |
|---|---|---|
| `package.json` | **Create** | npm scripts + Tailwind dev dependency |
| `tailwind.config.js` | **Create** | Content globs + brand colour tokens |
| `src/main/frontend/tailwind.css` | **Create** | Tailwind directives input file |
| `src/main/resources/static/css/tailwind.css` | **Generated** | Purged output (not committed to git) |
| `pom.xml` | **Update** | Add `frontend-maven-plugin` for build-time CSS generation |
| `templates/fragments/layout.html` | **Update** | Add `tailwind.css` link; add Tailwind CDN as dev fallback |
| `templates/students/list.html` | **Migrate** | Use `layout.html` fragment, remove CDN script + navbar + footer |
| `templates/students/detail.html` | **Migrate** | Same as above |
| `templates/students/form.html` | **Migrate** | Same as above |

---

## 5. Build Pipeline Setup

### 5.1 `package.json` (project root)

```json
{
  "name": "eduflow-frontend",
  "version": "1.0.0",
  "scripts": {
    "build:css":  "tailwindcss -i ./src/main/frontend/tailwind.css -o ./src/main/resources/static/css/tailwind.css --minify",
    "watch:css":  "tailwindcss -i ./src/main/frontend/tailwind.css -o ./src/main/resources/static/css/tailwind.css --watch"
  },
  "devDependencies": {
    "tailwindcss": "^3.4.17",
    "@tailwindcss/forms": "^0.5.9"
  }
}
```

### 5.2 `tailwind.config.js` (project root)

```js
/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './src/main/resources/templates/**/*.html',
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          50:  '#e7f1ef',
          100: '#cce3df',
          200: '#9dc7c0',
          300: '#6daba1',
          400: '#3e8f82',
          500: '#177367',
          600: '#0e5c54',
          700: '#0a443e',
          800: '#072e2a',
          900: '#041e1b',
        }
      }
    }
  },
  plugins: [
    require('@tailwindcss/forms'),
  ],
}
```

### 5.3 `src/main/frontend/tailwind.css` (input)

```css
@tailwind base;
@tailwind components;
@tailwind utilities;
```

### 5.4 `pom.xml` — `frontend-maven-plugin`

Add inside `<build><plugins>`:

```xml
<plugin>
    <groupId>com.github.eirslett</groupId>
    <artifactId>frontend-maven-plugin</artifactId>
    <version>1.15.0</version>
    <configuration>
        <nodeVersion>v20.19.0</nodeVersion>
        <npmVersion>10.8.2</npmVersion>
        <workingDirectory>${project.basedir}</workingDirectory>
    </configuration>
    <executions>
        <execution>
            <id>install-node-and-npm</id>
            <goals><goal>install-node-and-npm</goal></goals>
            <phase>generate-resources</phase>
        </execution>
        <execution>
            <id>npm-install</id>
            <goals><goal>npm</goal></goals>
            <phase>generate-resources</phase>
            <configuration><arguments>install</arguments></configuration>
        </execution>
        <execution>
            <id>build-tailwind-css</id>
            <goals><goal>npm</goal></goals>
            <phase>generate-resources</phase>
            <configuration><arguments>run build:css</arguments></configuration>
        </execution>
    </executions>
</plugin>
```

---

## 6. Git Configuration

Add to `.gitignore`:

```
# Generated Tailwind CSS (rebuilt by Maven)
src/main/resources/static/css/tailwind.css

# npm / Node artefacts
node/
node_modules/
```

> **Note:** The generated `tailwind.css` is excluded from git. Every developer and CI run
> must execute `./mvnw generate-resources` (or `npm run build:css`) before the first
> `spring-boot:run`.

---

## 7. Developer Workflow

```bash
# One-time: generate the CSS (required before first run after a fresh clone)
./mvnw generate-resources

# Watch mode for template changes during development
npm run watch:css

# Standard build (CSS is regenerated automatically)
./mvnw package

# Start the app (database must be running)
./mvnw spring-boot:run
```

---

## 8. layout.html CDN Fallback

During development, if the generated `tailwind.css` does not yet exist (e.g. after a fresh
clone before running `mvnw generate-resources`), a CDN fallback `<script>` is included with
`th:if` in `layout.html` to keep the UI functional. This fallback should be **removed for
production deployments**.

---

## 9. Template Migration Pattern

Every student (and future) page follows this shell:

```html
<!DOCTYPE html>
<html th:replace="~{fragments/layout :: layout('Page Title', 'Breadcrumb', 'activeNav', ~{::content})}"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
<body>
<th:block th:fragment="content">

    <!-- page content — rendered inside layout's <main class="canvas"> -->
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <!-- ... -->
    </div>

</th:block>
</body>
</html>
```

`activeNav` values: `dashboard` | `students` | `applications` | `visa` | `tasks` |
`staff` | `reports` | `tenants`

---

## 10. Checklist

- [x] `docs/PLAN-tailwind.md` created
- [x] `package.json` created
- [x] `tailwind.config.js` created
- [x] `src/main/frontend/tailwind.css` created
- [x] `pom.xml` updated with `frontend-maven-plugin`
- [x] `layout.html` updated — Tailwind CSS link + CDN fallback
- [x] `students/list.html` migrated to layout fragment
- [x] `students/detail.html` migrated to layout fragment
- [x] `students/form.html` migrated to layout fragment
- [ ] `.gitignore` updated (manual step)
- [ ] `npm run build:css` run to generate initial `tailwind.css`
- [ ] Dashboard page updated to use Tailwind utilities (future)

