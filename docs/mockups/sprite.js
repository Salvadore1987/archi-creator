/* Archi Creator — макеты. Инъекция SVG-спрайта: иконки нотации, UI-иконки и маркеры связей.
   Один файл на все шаблоны, чтобы визуальный язык был идентичен и сравнивалась только компоновка. */
(function () {
  const SPRITE = `
<svg xmlns="http://www.w3.org/2000/svg" aria-hidden="true"
     style="position:absolute;width:0;height:0;overflow:hidden">
  <defs>
    <!-- ===== Маркеры связей ArchiMate ===== -->
    <marker id="m-open" markerWidth="11" markerHeight="11" refX="9.5" refY="5.5"
            orient="auto" markerUnits="userSpaceOnUse">
      <path d="M3 1.5 L9.5 5.5 L3 9.5" fill="none" stroke="context-stroke" stroke-width="1.4"
            stroke-linecap="round" stroke-linejoin="round"/>
    </marker>
    <marker id="m-solid" markerWidth="11" markerHeight="10" refX="9.5" refY="5"
            orient="auto" markerUnits="userSpaceOnUse">
      <path d="M1.5 1 L9.5 5 L1.5 9 Z" fill="context-stroke"/>
    </marker>
    <marker id="m-hollow" markerWidth="13" markerHeight="12" refX="11.5" refY="6"
            orient="auto" markerUnits="userSpaceOnUse">
      <path d="M1.5 1 L11.5 6 L1.5 11 Z" fill="var(--paper)" stroke="context-stroke" stroke-width="1.3"
            stroke-linejoin="round"/>
    </marker>
    <marker id="m-diamond-fill" markerWidth="16" markerHeight="10" refX="1" refY="5"
            orient="auto" markerUnits="userSpaceOnUse">
      <path d="M1 5 L7 1.4 L13 5 L7 8.6 Z" fill="context-stroke"/>
    </marker>
    <marker id="m-diamond-open" markerWidth="16" markerHeight="10" refX="1" refY="5"
            orient="auto" markerUnits="userSpaceOnUse">
      <path d="M1 5 L7 1.4 L13 5 L7 8.6 Z" fill="var(--paper)" stroke="context-stroke" stroke-width="1.3"
            stroke-linejoin="round"/>
    </marker>
    <marker id="m-ball" markerWidth="9" markerHeight="9" refX="4.5" refY="4.5"
            orient="auto" markerUnits="userSpaceOnUse">
      <circle cx="4.5" cy="4.5" r="3" fill="context-stroke"/>
    </marker>
  </defs>

  <!-- ===== Иконки нотации (16×16, currentColor) ===== -->
  <symbol id="i-app-component" viewBox="0 0 16 16">
    <rect x="4.5" y="2.5" width="9" height="11" rx="1" fill="none" stroke="currentColor" stroke-width="1.3"/>
    <rect x="1.5" y="4.5" width="5" height="2.6" rx=".6" fill="var(--node-bg,#fff)" stroke="currentColor" stroke-width="1.3"/>
    <rect x="1.5" y="8.9" width="5" height="2.6" rx=".6" fill="var(--node-bg,#fff)" stroke="currentColor" stroke-width="1.3"/>
  </symbol>
  <symbol id="i-app-service" viewBox="0 0 16 16">
    <rect x="1.5" y="4.5" width="13" height="7" rx="3.5" fill="none" stroke="currentColor" stroke-width="1.3"/>
  </symbol>
  <symbol id="i-function" viewBox="0 0 16 16">
    <path d="M2.5 2.5 H10 L13.5 8 L10 13.5 H2.5 L6 8 Z" fill="none" stroke="currentColor"
          stroke-width="1.3" stroke-linejoin="round"/>
  </symbol>
  <symbol id="i-process" viewBox="0 0 16 16">
    <path d="M1.5 5.5 H9 V3 L14.5 8 L9 13 V10.5 H1.5 Z" fill="none" stroke="currentColor"
          stroke-width="1.3" stroke-linejoin="round"/>
  </symbol>
  <symbol id="i-actor" viewBox="0 0 16 16">
    <circle cx="8" cy="3.6" r="2.1" fill="none" stroke="currentColor" stroke-width="1.3"/>
    <path d="M8 5.9 V10.4 M4 7.6 H12 M8 10.4 L5.2 13.8 M8 10.4 L10.8 13.8"
          fill="none" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
  </symbol>
  <symbol id="i-node" viewBox="0 0 16 16">
    <path d="M1.5 5.5 H11 V14 H1.5 Z M1.5 5.5 L5 2 H14.5 V10.5 L11 14 M11 5.5 L14.5 2"
          fill="none" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/>
  </symbol>
  <symbol id="i-device" viewBox="0 0 16 16">
    <rect x="2" y="2.5" width="12" height="8" rx="1.4" fill="none" stroke="currentColor" stroke-width="1.3"/>
    <path d="M4.5 13.5 L6 10.5 H10 L11.5 13.5 Z" fill="none" stroke="currentColor"
          stroke-width="1.3" stroke-linejoin="round"/>
  </symbol>
  <symbol id="i-sys-software" viewBox="0 0 16 16">
    <circle cx="6.6" cy="9.4" r="4.4" fill="none" stroke="currentColor" stroke-width="1.3"/>
    <path d="M7.4 5.1 A4.4 4.4 0 1 1 11.3 9.9" fill="none" stroke="currentColor"
          stroke-width="1.3" stroke-linecap="round"/>
  </symbol>
  <symbol id="i-network" viewBox="0 0 16 16">
    <circle cx="3.4" cy="8" r="2" fill="none" stroke="currentColor" stroke-width="1.3"/>
    <circle cx="12.6" cy="8" r="2" fill="none" stroke="currentColor" stroke-width="1.3"/>
    <path d="M5.4 8 H10.6" stroke="currentColor" stroke-width="1.3"/>
  </symbol>
  <symbol id="i-capability" viewBox="0 0 16 16">
    <rect x="1.5" y="1.5" width="6" height="6" rx="1.2" fill="none" stroke="currentColor" stroke-width="1.3"/>
    <rect x="8.5" y="1.5" width="6" height="6" rx="1.2" fill="none" stroke="currentColor" stroke-width="1.3"/>
    <rect x="1.5" y="8.5" width="6" height="6" rx="1.2" fill="none" stroke="currentColor" stroke-width="1.3"/>
  </symbol>
  <symbol id="i-location" viewBox="0 0 16 16">
    <path d="M8 14.2 C8 14.2 3 9.3 3 6.4 A5 5 0 0 1 13 6.4 C13 9.3 8 14.2 8 14.2 Z"
          fill="none" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/>
    <circle cx="8" cy="6.3" r="1.7" fill="none" stroke="currentColor" stroke-width="1.3"/>
  </symbol>
  <symbol id="i-data-object" viewBox="0 0 16 16">
    <rect x="1.5" y="2.5" width="13" height="11" rx="1" fill="none" stroke="currentColor" stroke-width="1.3"/>
    <path d="M1.5 6 H14.5" stroke="currentColor" stroke-width="1.3"/>
  </symbol>
  <symbol id="i-artifact" viewBox="0 0 16 16">
    <path d="M2.5 1.5 H9.5 L13.5 5.5 V14.5 H2.5 Z M9.5 1.5 V5.5 H13.5"
          fill="none" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/>
  </symbol>
  <symbol id="i-group" viewBox="0 0 16 16">
    <path d="M1.5 4.5 H6.5 V2.5 H14.5 V13.5 H1.5 Z" fill="none" stroke="currentColor"
          stroke-width="1.3" stroke-linejoin="round"/>
  </symbol>
  <symbol id="i-goal" viewBox="0 0 16 16">
    <circle cx="8" cy="8" r="6.2" fill="none" stroke="currentColor" stroke-width="1.3"/>
    <circle cx="8" cy="8" r="3.2" fill="none" stroke="currentColor" stroke-width="1.3"/>
    <circle cx="8" cy="8" r="1" fill="currentColor"/>
  </symbol>
  <symbol id="i-junction" viewBox="0 0 16 16">
    <circle cx="8" cy="8" r="4" fill="currentColor"/>
  </symbol>

  <!-- ===== UI-иконки ===== -->
  <symbol id="u-search" viewBox="0 0 16 16">
    <circle cx="7" cy="7" r="4.6" fill="none" stroke="currentColor" stroke-width="1.5"/>
    <path d="M10.4 10.4 L14 14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
  </symbol>
  <symbol id="u-cursor" viewBox="0 0 16 16">
    <path d="M3 2 L12.5 7.6 L8.2 8.6 L6.6 13 Z" fill="none" stroke="currentColor"
          stroke-width="1.4" stroke-linejoin="round"/>
  </symbol>
  <symbol id="u-hand" viewBox="0 0 16 16">
    <path d="M5 8.5 V4.2 a1.2 1.2 0 0 1 2.4 0 V8 V3.2 a1.2 1.2 0 0 1 2.4 0 V8 V4.6 a1.2 1.2 0 0 1 2.4 0 V10
             c0 2.6-1.6 4.2-4 4.2 -2.2 0-3.3-1-4.2-2.6 L2.6 9.6 a1.2 1.2 0 0 1 2-1.3 Z"
          fill="none" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/>
  </symbol>
  <symbol id="u-connect" viewBox="0 0 16 16">
    <circle cx="3.4" cy="12.6" r="2.2" fill="none" stroke="currentColor" stroke-width="1.4"/>
    <circle cx="12.6" cy="3.4" r="2.2" fill="none" stroke="currentColor" stroke-width="1.4"/>
    <path d="M5 11 L11 5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
  </symbol>
  <symbol id="u-layout" viewBox="0 0 16 16">
    <rect x="1.5" y="2" width="13" height="3.4" rx=".8" fill="none" stroke="currentColor" stroke-width="1.4"/>
    <rect x="1.5" y="7.4" width="7" height="3.4" rx=".8" fill="none" stroke="currentColor" stroke-width="1.4"/>
    <rect x="1.5" y="12.8" width="13" height="1.6" rx=".8" fill="none" stroke="currentColor" stroke-width="1.4"/>
  </symbol>
  <symbol id="u-undo" viewBox="0 0 16 16">
    <path d="M2.5 7 H9.5 a3.6 3.6 0 0 1 0 7.2 H6" fill="none" stroke="currentColor"
          stroke-width="1.4" stroke-linecap="round"/>
    <path d="M5.4 3.6 L2 7 L5.4 10.4" fill="none" stroke="currentColor" stroke-width="1.4"
          stroke-linecap="round" stroke-linejoin="round"/>
  </symbol>
  <symbol id="u-redo" viewBox="0 0 16 16">
    <path d="M13.5 7 H6.5 a3.6 3.6 0 0 0 0 7.2 H10" fill="none" stroke="currentColor"
          stroke-width="1.4" stroke-linecap="round"/>
    <path d="M10.6 3.6 L14 7 L10.6 10.4" fill="none" stroke="currentColor" stroke-width="1.4"
          stroke-linecap="round" stroke-linejoin="round"/>
  </symbol>
  <symbol id="u-sparkle" viewBox="0 0 16 16">
    <path d="M8 1 L9.5 5.8 L14 7.4 L9.5 9 L8 14 L6.5 9 L2 7.4 L6.5 5.8 Z"
          fill="none" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/>
  </symbol>
  <symbol id="u-lock" viewBox="0 0 16 16">
    <rect x="3" y="6.8" width="10" height="7.4" rx="1.4" fill="none" stroke="currentColor" stroke-width="1.4"/>
    <path d="M5.4 6.8 V4.8 a2.6 2.6 0 0 1 5.2 0 V6.8" fill="none" stroke="currentColor" stroke-width="1.4"/>
  </symbol>
  <symbol id="u-eye" viewBox="0 0 16 16">
    <path d="M1 8 C3 4.4 5.4 2.8 8 2.8 S13 4.4 15 8 C13 11.6 10.6 13.2 8 13.2 S3 11.6 1 8 Z"
          fill="none" stroke="currentColor" stroke-width="1.3"/>
    <circle cx="8" cy="8" r="2.2" fill="none" stroke="currentColor" stroke-width="1.3"/>
  </symbol>
  <symbol id="u-pencil" viewBox="0 0 16 16">
    <path d="M2 14 L2.9 10.8 L10.7 3 a1.8 1.8 0 0 1 2.5 2.5 L5.4 13.2 Z"
          fill="none" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/>
  </symbol>
  <symbol id="u-chevron" viewBox="0 0 16 16">
    <path d="M6 3.5 L10.5 8 L6 12.5" fill="none" stroke="currentColor" stroke-width="1.5"
          stroke-linecap="round" stroke-linejoin="round"/>
  </symbol>
  <symbol id="u-warn" viewBox="0 0 16 16">
    <path d="M8 1.8 L15 14 H1 Z" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/>
    <path d="M8 6.2 V9.6 M8 11.6 v.1" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
  </symbol>
  <symbol id="u-grid" viewBox="0 0 16 16">
    <path d="M1.5 5.8 H14.5 M1.5 10.2 H14.5 M5.8 1.5 V14.5 M10.2 1.5 V14.5"
          stroke="currentColor" stroke-width="1.3"/>
  </symbol>
  <symbol id="u-tree" viewBox="0 0 16 16">
    <path d="M2 3 H14 M5 8 H14 M5 13 H14 M2 3 V13 H5" fill="none" stroke="currentColor"
          stroke-width="1.3" stroke-linecap="round"/>
  </symbol>
</svg>`;

  function inject() {
    const holder = document.createElement('div');
    holder.style.cssText = 'position:absolute;width:0;height:0;overflow:hidden';
    holder.innerHTML = SPRITE;
    document.body.insertBefore(holder, document.body.firstChild);
  }
  /* Вставляем сразу, как только доступен body: символы должны существовать
     до того, как страница отрисует свои <use href="#i-…">. */
  if (document.body) {
    inject();
  } else {
    document.addEventListener('DOMContentLoaded', inject);
  }
})();
