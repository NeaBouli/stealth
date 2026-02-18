// ═══════════════════════════════════════════════════════════
// SecureCall — stealthx.app
// Main JavaScript — Navigation, FAQ, Animations
// ═══════════════════════════════════════════════════════════

// ─── Navbar Scroll Effect ────────────────────────────────
const navbar = document.getElementById('navbar');

if (navbar && !navbar.classList.contains('scrolled')) {
    window.addEventListener('scroll', () => {
        if (window.scrollY > 50) {
            navbar.classList.add('scrolled');
        } else {
            navbar.classList.remove('scrolled');
        }
    }, { passive: true });
}

// ─── Mobile Menu Toggle ─────────────────────────────────
const navToggle = document.getElementById('navToggle');
const navLinks = document.getElementById('navLinks');

if (navToggle && navLinks) {
    navToggle.addEventListener('click', () => {
        navLinks.classList.toggle('active');
        navToggle.classList.toggle('open');
    });

    navLinks.querySelectorAll('a').forEach(link => {
        link.addEventListener('click', () => {
            navLinks.classList.remove('active');
            navToggle.classList.remove('open');
        });
    });
}

// ─── Smooth Scroll for Anchor Links ─────────────────────
document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function (e) {
        const href = this.getAttribute('href');
        if (href === '#') return;
        const target = document.querySelector(href);
        if (target) {
            e.preventDefault();
            const offset = navbar ? navbar.offsetHeight : 0;
            const top = target.getBoundingClientRect().top + window.scrollY - offset;
            window.scrollTo({ top, behavior: 'smooth' });
        }
    });
});

// ─── FAQ Accordion ──────────────────────────────────────
document.querySelectorAll('.faq-question').forEach(btn => {
    btn.addEventListener('click', () => {
        const item = btn.closest('.faq-item');
        const isOpen = item.classList.contains('open');

        // Close all FAQ items in the same list
        const list = item.closest('.faq-list');
        if (list) {
            list.querySelectorAll('.faq-item.open').forEach(openItem => {
                openItem.classList.remove('open');
            });
        }

        // Toggle clicked item
        if (!isOpen) {
            item.classList.add('open');
        }
    });
});

// ─── Reveal Animations (Intersection Observer) ──────────
const revealObserver = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
        if (entry.isIntersecting) {
            entry.target.classList.add('visible');
            revealObserver.unobserve(entry.target);
        }
    });
}, {
    threshold: 0.1,
    rootMargin: '0px 0px -40px 0px'
});

document.querySelectorAll('.reveal').forEach(el => {
    revealObserver.observe(el);
});
