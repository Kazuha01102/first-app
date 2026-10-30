document.getElementById('calculator').addEventListener('submit', async event => {
  event.preventDefault();
  const result = document.getElementById('result');
  const button = event.target.querySelector('button');
  button.disabled = true;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 4000);
  try {
    const params = new URLSearchParams({
      a: document.getElementById('a').value,
      b: document.getElementById('b').value,
      op: document.getElementById('op').value
    });
    const response = await fetch('/calculate?' + params, { signal: controller.signal });
    if (!response.ok) throw new Error('HTTP ' + response.status);
    result.textContent = await response.text();
  } catch (error) {
    result.textContent = '計算APIに接続できません。再試行してください。';
  } finally {
    clearTimeout(timeout);
    button.disabled = false;
  }
});
