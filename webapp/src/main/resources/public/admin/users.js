async function postJson(url, body) {
  const response = await fetch(url, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    let message = 'Error saving';
    try {
      message = (await response.json()).error || message;
    } catch (ignored) {
    }
    throw new Error(message);
  }
}

async function whitelistUser() {
  const phone = document.getElementById('new-phone').value;
  const name = document.getElementById('new-name').value;
  const messageDiv = document.getElementById('whitelist-message');
  messageDiv.classList.remove('errorMessage');
  try {
    await postJson('/admin/users/whitelist', {phone: phone, name: name});
    location.reload();
  } catch (error) {
    messageDiv.classList.add('errorMessage');
    messageDiv.innerHTML = error.message;
  }
}

async function updateName(userId, input) {
  try {
    await postJson('/admin/users/set-name', {userId: userId, name: input.value});
    flashSaved(input);
  } catch (error) {
    input.classList.add('missingData');
  }
}

async function toggleRole(button, userId, role) {
  const enabled = !button.classList.contains('on');
  try {
    await postJson('/admin/users/toggle-role', {userId: userId, role: role, enabled: enabled});
    button.classList.toggle('on', enabled);
  } catch (error) {
    flashError(button);
  }
}

async function toggleRemoved(button, userId) {
  const currentlyActive = button.classList.contains('active');
  const removed = currentlyActive;
  try {
    await postJson('/admin/users/set-removed', {userId: userId, removed: removed});
    button.classList.toggle('active', !removed);
    button.classList.toggle('inactive', removed);
    button.textContent = removed ? 'Inactive' : 'Active';
    button.closest('tr').classList.toggle('user-removed', removed);
  } catch (error) {
    flashError(button);
  }
}

function flashSaved(element) {
  element.classList.add('saved');
  setTimeout(() => element.classList.remove('saved'), 1200);
}

function flashError(element) {
  element.classList.add('save-error');
  setTimeout(() => element.classList.remove('save-error'), 1500);
}
