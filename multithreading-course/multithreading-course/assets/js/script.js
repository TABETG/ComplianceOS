
document.addEventListener('DOMContentLoaded', () => {
  const current = window.location.pathname.split('/').pop() || 'index.html';
  document.querySelectorAll('.nav-link').forEach(link => {
    const href = link.getAttribute('href');
    if (href.endsWith(current)) {
      link.classList.add('active');
    }
  });

  const article = document.querySelector('.article');
  const tocContainer = document.getElementById('toc-container');
  if (article && tocContainer) {
    const headings = [...article.querySelectorAll('section h2, section h3')];
    headings.forEach((heading, index) => {
      if (!heading.id) {
        heading.id = heading.textContent
          .toLowerCase()
          .normalize('NFD')
          .replace(/[\u0300-\u036f]/g, '')
          .replace(/[^a-z0-9]+/g, '-')
          .replace(/(^-|-$)/g, '') + '-' + index;
      }
      const a = document.createElement('a');
      a.href = `#${heading.id}`;
      a.textContent = heading.textContent;
      a.style.paddingLeft = heading.tagName === 'H3' ? '14px' : '0';
      tocContainer.appendChild(a);
    });
  }

  const toggle = document.getElementById('toc-toggle');
  const toc = document.getElementById('toc');
  if (toggle && toc) {
    toggle.addEventListener('click', () => {
      toc.style.display = toc.style.display === 'block' ? 'none' : 'block';
    });
  }

  document.querySelectorAll('[data-toggle]').forEach(button => {
    const target = document.getElementById(button.dataset.toggle);
    if (!target) return;
    button.addEventListener('click', () => {
      const hidden = target.hasAttribute('hidden');
      if (hidden) {
        target.removeAttribute('hidden');
        button.textContent = 'Masquer';
      } else {
        target.setAttribute('hidden', '');
        button.textContent = 'Afficher';
      }
    });
  });
});
