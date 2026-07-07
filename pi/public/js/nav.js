// Spatial (D-pad / arrow-key) focus manager for the 10-foot UI. Any element
// with class "focusable" participates; movement picks the nearest candidate in
// the pressed direction. Enter activates, Backspace/Escape emit "nav-back".

export const Nav = (() => {
  let current = null;

  const isVisible = (el) => el.offsetParent !== null && el.getClientRects().length > 0;
  const collect = () => Array.from(document.querySelectorAll('.focusable')).filter(isVisible);
  const center = (el) => {
    const r = el.getBoundingClientRect();
    return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
  };

  function setFocus(el) {
    if (!el || el === current) {
      if (el) {
        el.classList.add('focused');
        current = el;
      }
      return;
    }
    if (current) current.classList.remove('focused');
    el.classList.add('focused');
    current = el;
    el.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
  }

  // Focus the preferred selector if present, else the first focusable.
  function refresh(preferred) {
    const list = collect();
    if (current && list.includes(current) && isVisible(current)) {
      setFocus(current);
      return;
    }
    if (current) current.classList.remove('focused');
    current = null;
    let target = null;
    if (preferred) {
      target = Array.from(document.querySelectorAll(preferred)).find((e) => list.includes(e));
    }
    setFocus(target || list[0] || null);
  }

  function move(dir) {
    const list = collect();
    if (!current || !list.includes(current)) {
      setFocus(list[0]);
      return;
    }
    const c = center(current);
    let best = null;
    let bestScore = Infinity;
    for (const el of list) {
      if (el === current) continue;
      const t = center(el);
      const dx = t.x - c.x;
      const dy = t.y - c.y;
      let ok, primary, off;
      if (dir === 'left') { ok = dx < -8; primary = -dx; off = Math.abs(dy); }
      else if (dir === 'right') { ok = dx > 8; primary = dx; off = Math.abs(dy); }
      else if (dir === 'up') { ok = dy < -8; primary = -dy; off = Math.abs(dx); }
      else { ok = dy > 8; primary = dy; off = Math.abs(dx); }
      if (!ok) continue;
      const score = primary + off * 2;
      if (score < bestScore) { bestScore = score; best = el; }
    }
    if (best) setFocus(best);
  }

  function onKey(e) {
    const typing = current && current.tagName === 'INPUT';
    switch (e.key) {
      case 'ArrowLeft': if (typing) return; e.preventDefault(); move('left'); break;
      case 'ArrowRight': if (typing) return; e.preventDefault(); move('right'); break;
      case 'ArrowUp': e.preventDefault(); move('up'); break;
      case 'ArrowDown': e.preventDefault(); move('down'); break;
      case 'Enter':
        if (typing) return;
        e.preventDefault();
        if (current) current.click();
        break;
      case 'Backspace':
        if (typing) return;
        e.preventDefault();
        window.dispatchEvent(new CustomEvent('nav-back'));
        break;
      case 'Escape':
        e.preventDefault();
        window.dispatchEvent(new CustomEvent('nav-back'));
        break;
      default:
        break;
    }
  }

  window.addEventListener('keydown', onKey);
  // Mouse hover also moves focus, so the UI works with a pointer too.
  document.addEventListener('mouseover', (e) => {
    const f = e.target.closest && e.target.closest('.focusable');
    if (f && isVisible(f)) setFocus(f);
  });

  return { refresh, move, setFocus };
})();
