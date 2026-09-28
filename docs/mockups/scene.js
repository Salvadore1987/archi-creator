/* Archi Creator — общая сцена для всех шаблонов.
   Фрагмент модели Hamkorbank AS-IS (имена взяты из docs/Hamkorbank_AS_IS_strict.archimate).
   Одна и та же диаграмма во всех макетах — чтобы сравнивалась компоновка, а не содержимое. */
(function () {
  const W = 800, H = 500;

  const NODES = [
    { id:'n-actor', layer:'biz',  icon:'i-actor',        name:'Физическое лицо',
      type:'Business Actor',        x: 40, y: 24, w:170, h:56 },
    { id:'n-fn',    layer:'biz',  icon:'i-function',     name:'Розничный бизнес',
      type:'Business Function',     x:280, y: 24, w:180, h:56, shape:'behavior' },
    { id:'n-svc',   layer:'app',  icon:'i-app-service',  name:'Сервис интернет-банкинга',
      type:'Application Service',   x:265, y:130, w:210, h:60, shape:'service' },
    { id:'n-ib',    layer:'app',  icon:'i-app-component',name:'Интернет банкинг ФЛ',
      type:'Application Component', x: 40, y:232, w:180, h:62 },
    { id:'n-iabs',  layer:'app',  icon:'i-app-component',name:'IABS',
      type:'Application Component', x:280, y:232, w:180, h:62 },
    { id:'n-crm',   layer:'app',  icon:'i-app-component',name:'CRM Creatio 7.13',
      type:'Application Component', x:520, y:232, w:180, h:62 },
    { id:'n-card',  layer:'app',  icon:'i-app-component',name:'Карт инфо сервис',
      type:'Application Component', x:520, y:318, w:180, h:56 },
    { id:'n-ora',   layer:'tech', icon:'i-sys-software', name:'Oracle 19c',
      type:'System Software',       x: 40, y:392, w:180, h:62 },
    { id:'n-esb',   layer:'tech', icon:'i-sys-software', name:'BANK ESB (WSO2 EI)',
      type:'System Software',       x:280, y:392, w:180, h:62 },
    { id:'n-vm',    layer:'tech', icon:'i-device',       name:'Сервера виртуализации ×7',
      type:'Device',                x:520, y:392, w:180, h:62 }
  ];

  /* Ортогональная маршрутизация задана вручную — как её посчитал бы роутер редактора */
  const EDGES = [
    { d:'M210 52 H280',              kind:'assignment',  label:'' },
    { d:'M370 130 V 80',             kind:'serving',     label:'serving',     lx:396, ly:108 },
    { d:'M130 232 V 160 H 265',      kind:'realization', label:'realization', lx:190, ly:154 },
    { d:'M280 263 H 220',            kind:'serving',     label:'' },
    { d:'M520 263 H 460',            kind:'flow',        label:'flow',        lx:490, ly:256 },
    { d:'M520 346 H 340 V 294',      kind:'flow',        label:'' },
    { d:'M400 392 V 294',            kind:'serving',     label:'' },
    { d:'M220 423 H 280',            kind:'serving',     label:'' },
    { d:'M520 423 H 460',            kind:'assignment',  label:'' }
  ];

  const KIND = {
    serving:     { cls:'e',            start:'',                     end:'url(#m-open)' },
    flow:        { cls:'e e--dash',    start:'',                     end:'url(#m-solid)' },
    realization: { cls:'e e--dash',    start:'',                     end:'url(#m-hollow)' },
    assignment:  { cls:'e',            start:'url(#m-ball)',         end:'url(#m-solid)' },
    composition: { cls:'e',            start:'url(#m-diamond-fill)', end:'' },
    aggregation: { cls:'e',            start:'url(#m-diamond-open)', end:'' },
    access:      { cls:'e e--dot',     start:'',                     end:'url(#m-open)' }
  };

  const BANDS = [
    { label:'Business',    y:0,   h:110, color:'rgba(201,162,39,.10)' },
    { label:'Application', y:110, h:272, color:'rgba(74,144,194,.10)' },
    { label:'Technology',  y:382, h:118, color:'rgba(62,155,98,.10)' }
  ];

  function nodeHtml(n, o) {
    const cls = ['an', 'an--' + n.layer];
    if (n.shape === 'behavior') cls.push('an--behavior');
    if (n.shape === 'service')  cls.push('an--service');
    if (o.selected === n.id)    cls.push('is-sel');
    const ports = o.selected === n.id
      ? '<i class="an__port an__port--t"></i><i class="an__port an__port--l"></i>' +
        '<i class="an__port an__port--r"></i><i class="an__port an__port--b"></i>'
      : '';
    const type = o.hideTypes ? '' : `<div class="an__type">${n.type}</div>`;
    return `<div class="${cls.join(' ')}" id="${n.id}"
      style="--x:${n.x};--y:${n.y};--w:${n.w};--h:${n.h}">
      <svg class="an__icon"><use href="#${n.icon}"/></svg>
      <div class="an__name">${n.name}</div>
      ${type}${ports}
    </div>`;
  }

  function edgesSvg(o) {
    const paths = EDGES.map((e, i) => {
      const k = KIND[e.kind];
      const hi = o.highlight && o.highlight.includes(i) ? ' e--hi' : '';
      const ms = k.start ? ` marker-start="${k.start}"` : '';
      const me = k.end ? ` marker-end="${k.end}"` : '';
      return `<path class="${k.cls}${hi}" d="${e.d}"${ms}${me}/>`;
    }).join('');

    const labels = o.hideTypes ? '' : EDGES.filter(e => e.label).map(e => {
      const w = e.label.length * 5.4 + 8;
      return `<g><rect class="e-label-bg" x="${e.lx - w / 2}" y="${e.ly - 7}"
        width="${w}" height="12" rx="2"/>
        <text class="e-label" x="${e.lx}" y="${e.ly + 2}" text-anchor="middle">${e.label}</text></g>`;
    }).join('');

    return `<svg class="edges" viewBox="0 0 ${W} ${H}" preserveAspectRatio="none">
      ${paths}${labels}</svg>`;
  }

  function bandsHtml() {
    return BANDS.map(b => `
      <div class="layer-band" style="top:${b.y}px;height:${b.h}px;--band:${b.color}"></div>
      <div class="layer-tag" style="top:${b.y + 7}px">${b.label}</div>`).join('');
  }

  /* Меню «какие связи допустимы» — демонстрация FR-13 */
  function connMenu() {
    const mini = (kind) => {
      const k = KIND[kind];
      const ms = k.start ? ` marker-start="${k.start}"` : '';
      const me = k.end ? ` marker-end="${k.end}"` : '';
      return `<svg class="mini" viewBox="0 0 34 10" style="--edge-color:currentColor">
        <path class="${k.cls}" d="M2 5 H30"${ms}${me}/></svg>`;
    };
    return `<div class="conn-menu" style="left:482px;top:34px">
      <div class="conn-menu__head">IABS → Розничный бизнес</div>
      <div class="conn-menu__i is-on">${mini('serving')}<span>Serving</span><span class="tag">↵</span></div>
      <div class="conn-menu__i">${mini('realization')}<span>Realization</span></div>
      <div class="conn-menu__i">${mini('flow')}<span>Flow</span></div>
      <div class="conn-menu__i">${mini('access')}<span>Access</span></div>
      <div class="conn-menu__i is-off">${mini('assignment')}<span>Assignment</span>
        <span class="tag">не разрешена</span></div>
    </div>`;
  }

  /**
   * @param {HTMLElement} el     контейнер (получит класс .canvas)
   * @param {Object} o
   *   selected   id выделенного узла
   *   scale      масштаб сцены
   *   hideTypes  скрыть подписи типов и связей (режим просмотра / мелкий масштаб)
   *   highlight  индексы подсвеченных связей
   *   connMenu   показать меню допустимых связей
   *   bands      показать полосы слоёв
   */
  window.renderScene = function (el, o) {
    o = o || {};
    const scale = o.scale || 1;
    el.classList.add('canvas');
    el.innerHTML = `
      <div class="canvas__scene" style="transform:scale(${scale});width:${W}px;height:${H}px">
        ${o.bands === false ? '' : bandsHtml()}
        ${edgesSvg(o)}
        ${NODES.map(n => nodeHtml(n, o)).join('')}
        ${o.connMenu ? connMenu() : ''}
      </div>`;
  };

  window.SCENE_NODES = NODES;
})();
