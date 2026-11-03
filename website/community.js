// Optional clipboard enhancement; the full addresses remain available without JS.
function enableCopy(copyButton, address, shortAddress, copyStatus) {
  if (!copyButton || !address || !shortAddress || !copyStatus || !navigator.clipboard?.writeText) return;
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

for (const [button, input, status] of [['#copy-address', '#token-address', '#copy-status'], ['#copy-second-address', '#second-token-address', '#second-copy-status']]) {
  const address = document.querySelector(input);
  enableCopy(document.querySelector(button), address, address?.parentElement.querySelector('.token-short'), document.querySelector(status));
}
