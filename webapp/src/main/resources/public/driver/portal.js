// Driver-portal profile-save feedback. htmx handles the success path natively (2xx responses
// swap into #confirmation); this file only covers what htmx doesn't do for us out of the box --
// see driver-portal UX review F5.
(function () {
  var form = document.getElementById("profile-form");
  var confirmation = document.getElementById("confirmation");
  if (!form || !confirmation) {
    return;
  }

  // htmx only swaps 2xx responses by default. A 400 (validation) still carries a real error
  // fragment in its body -- swap it in by hand so the driver sees *what* was wrong, not silence.
  // A network failure or 5xx has no usable fragment, so show one fixed, honest message instead of
  // leaking a stack trace or leaving the region empty.
  form.addEventListener("htmx:responseError", function (evt) {
    var xhr = evt.detail.xhr;
    if (xhr && xhr.status === 400 && xhr.responseText) {
      confirmation.innerHTML = xhr.responseText;
    } else {
      confirmation.innerHTML =
        '<span role="alert" class="confirm-error">' +
        "Something went wrong saving your info on our end. Please try again." +
        "</span>";
    }
  });

  form.addEventListener("htmx:sendError", function () {
    confirmation.innerHTML =
      '<span role="alert" class="confirm-error">' +
      "Couldn't reach the server -- check your connection and try again." +
      "</span>";
  });

  form.addEventListener("htmx:timeout", function () {
    confirmation.innerHTML =
      '<span role="alert" class="confirm-error">' +
      "That took too long and timed out. Please try again." +
      "</span>";
  });

  // Clear stale confirmation state as the driver edits again, but not too eagerly: a success
  // checkmark is stale the moment *anything* changes, but a validation error should stay legible
  // until either the flagged field is corrected or the form is resubmitted (ER-R2-2).
  form.addEventListener("input", function (evt) {
    var status = confirmation.querySelector('[role="status"]');
    var alertEl = confirmation.querySelector('[role="alert"]');
    if (status) {
      confirmation.innerHTML = "";
      return;
    }
    if (alertEl) {
      var fieldId = alertEl.getAttribute("data-error-field");
      var target = evt.target;
      if (fieldId && target && (target.id === fieldId || target.name === fieldId)) {
        confirmation.innerHTML = "";
      }
    }
  });
})();
