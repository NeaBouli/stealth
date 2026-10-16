(function () {
    'use strict';

    function reverse(part) {
        return part.split('').reverse().join('');
    }

    function address(element) {
        var parts = (element || document.querySelector('.js-mail')).dataset;
        return reverse(parts.u) + '@' + parts.d.split('|').map(reverse).join('.');
    }

    window.stxMail = address;

    document.querySelectorAll('.js-mail').forEach(function (element) {
        var addr = address(element);
        var link = document.createElement('a');
        link.textContent = addr;
        link.href = 'mai' + 'lto:' + addr;
        element.appendChild(link);
    });
})();
