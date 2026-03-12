
document.addEventListener('DOMContentLoaded', () => {
  const toggle = document.querySelector('.menu-toggle');
  const nav = document.querySelector('.nav');
  if (toggle && nav) {
    toggle.addEventListener('click', () => nav.classList.toggle('open'));
  }

  const content = document.querySelector('.content');
  const toc = document.getElementById('toc');
  if (content && toc) {
    const headings = [...content.querySelectorAll('section[id] > h2')];
    if (headings.length) {
      toc.innerHTML = headings
        .map((heading) => `<a href="#${heading.parentElement.id}">${heading.textContent}</a>`)
        .join('');

      const links = [...toc.querySelectorAll('a')];
      const activate = () => {
        let currentId = '';
        headings.forEach((heading) => {
          const rect = heading.getBoundingClientRect();
          if (rect.top <= 140) currentId = heading.parentElement.id;
        });
        links.forEach((link) => {
          const isActive = link.getAttribute('href') === `#${currentId}`;
          link.classList.toggle('active', isActive);
        });
      };
      activate();
      document.addEventListener('scroll', activate, { passive: true });
    }
  }

  document.querySelectorAll('[data-toggle-target]').forEach((button) => {
    button.addEventListener('click', () => {
      const target = document.getElementById(button.dataset.toggleTarget);
      if (!target) return;
      target.hidden = !target.hidden;
      button.textContent = target.hidden ? 'Afficher' : 'Masquer';
    });
  });
});
