
document.addEventListener('DOMContentLoaded', () => {
  const toc = document.getElementById('toc');
  if (toc) {
    const headers = [...document.querySelectorAll('.article h2, .article h3')];
    headers.forEach((header, index) => {
      if (!header.id) {
        header.id = header.textContent
          .toLowerCase()
          .normalize('NFD')
          .replace(/[\u0300-\u036f]/g, '')
          .replace(/[^a-z0-9]+/g, '-')
          .replace(/^-|-$/g, '') + '-' + index;
      }
      const a = document.createElement('a');
      a.href = '#' + header.id;
      a.textContent = header.textContent;
      a.className = header.tagName === 'H3' ? 'toc-sub' : 'toc-main';
      if (header.tagName === 'H3') a.style.paddingLeft = '22px';
      toc.appendChild(a);
    });
  }

  document.querySelectorAll('[data-toggle-target]').forEach(button => {
    button.addEventListener('click', () => {
      const target = document.getElementById(button.dataset.toggleTarget);
      if (!target) return;
      const hidden = target.hasAttribute('hidden');
      if (hidden) {
        target.removeAttribute('hidden');
        button.textContent = button.dataset.hideText || 'Masquer';
      } else {
        target.setAttribute('hidden', '');
        button.textContent = button.dataset.showText || 'Afficher';
      }
    });
  });
});
