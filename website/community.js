// Optional clipboard enhancement; the full address remains available without JS.
const copyButton = document.querySelector('#copy-address');
const address = document.querySelector('#token-address');
const shortAddress = document.querySelector('.token-short');
const copyStatus = document.querySelector('#copy-status');
if (copyButton && address && shortAddress && copyStatus && navigator.clipboard?.writeText) {
  copyButton.hidden = false;
  shortAddress.hidden = false;
  address.classList.add('address-compact');
  let resetTimer;
  copyButton.addEventListener('click', async () => {
    clearTimeout(resetTimer);
    copyButton.classList.remove('is-copied');
    copyButton.disabled = true;
    copyStatus.textContent = '';
    try {
      await navigator.clipboard.writeText(address.value);
      copyButton.classList.add('is-copied');
      resetTimer = setTimeout(() => copyButton.classList.remove('is-copied'), 1500);
    } catch {
      address.classList.remove('address-compact');
      shortAddress.hidden = true;
      address.focus();
      address.select();
      copyStatus.textContent = 'Couldn’t copy. Select the address to copy it manually.';
    } finally {
      copyButton.disabled = false;
    }
  });
}
