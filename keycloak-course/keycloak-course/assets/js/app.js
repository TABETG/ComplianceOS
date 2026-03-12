
document.addEventListener('DOMContentLoaded', () => {
  const path = window.location.pathname.split('/').pop();
  document.querySelectorAll('.nav-group a').forEach(a => {
    const href = a.getAttribute('href');
    if (href && href.endsWith(path)) a.classList.add('active');
  });

  const tocLinks = [...document.querySelectorAll('main h2')].map(h2 => {
    if (!h2.id) {
      h2.id = h2.textContent.toLowerCase()
        .normalize('NFD').replace(/[\u0300-\u036f]/g,'')
        .replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'');
    }
    return `<a href="#${h2.id}" class="card">${h2.textContent}</a>`;
  }).join('');

  const toc = document.getElementById('toc-auto');
  if (toc && tocLinks) toc.innerHTML = tocLinks;

  const btn = document.getElementById('toggle-details');
  const panel = document.getElementById('details-panel');
  if (btn && panel) {
    btn.addEventListener('click', () => {
      panel.hidden = !panel.hidden;
      btn.textContent = panel.hidden ? 'Afficher le guide de lecture' : 'Masquer le guide de lecture';
    });
  }
});
