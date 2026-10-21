// Copy buttons, a confirm on risky buttons, and the SMS length counter on the send form.
document.addEventListener('click', async (event) => {
  const copy = event.target.closest('[data-copy]');
  if (copy) {
    const source = document.getElementById(copy.dataset.copy);
    try {
      await navigator.clipboard.writeText(source.textContent.trim());
      const label = copy.textContent;
      copy.textContent = 'Copied';
      setTimeout(() => { copy.textContent = label; }, 1500);
    } catch (e) {
      window.getSelection().selectAllChildren(source);
    }
    return;
  }
  const confirmed = event.target.closest('[data-confirm]');
  if (confirmed && !window.confirm(confirmed.dataset.confirm)) {
    event.preventDefault();
  }
});

document.querySelectorAll('[data-sms-counter]').forEach((textarea) => {
  const out = document.getElementById(textarea.dataset.smsCounter);
  const update = () => {
    const text = textarea.value;
    const unicode = /[^\x00-\x7F]/.test(text);
    const single = unicode ? 70 : 160;
    const part = unicode ? 67 : 153;
    const parts = text.length === 0 ? 0 : text.length <= single ? 1 : Math.ceil(text.length / part);
    out.textContent = `${text.length} characters · ${parts} SMS${parts === 1 ? '' : ' parts'} · ${unicode ? 'Unicode (Khmer)' : 'plain text'}`;
  };
  textarea.addEventListener('input', update);
  update();
});
