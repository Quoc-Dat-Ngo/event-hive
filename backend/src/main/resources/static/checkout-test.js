const API_BASE = "http://localhost:8181/api/v1";

let accessToken = null;

const loginForm = document.getElementById("login-form");
const bookingForm = document.getElementById("booking-form");
const bookingSubmit = document.getElementById("booking-submit");
const loginStatus = document.getElementById("login-status");
const bookingStatus = document.getElementById("booking-status");
const output = document.getElementById("output");

function setStatus(el, message, ok) {
  el.textContent = message;
  el.className = "status " + (ok ? "ok" : "error");
}

function show(data) {
  output.hidden = false;
  output.textContent = JSON.stringify(data, null, 2);
}

async function readBody(response) {
  const text = await response.text();
  try {
    return text ? JSON.parse(text) : null;
  } catch {
    return text;
  }
}

loginForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const form = new FormData(loginForm);

  try {
    const response = await fetch(`${API_BASE}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        username: form.get("username"),
        password: form.get("password"),
      }),
    });
    const body = await readBody(response);

    if (!response.ok) {
      accessToken = null;
      bookingSubmit.disabled = true;
      setStatus(loginStatus, `Login failed (${response.status})`, false);
      show(body);
      return;
    }

    accessToken = body.accessToken;
    bookingSubmit.disabled = false;
    setStatus(loginStatus, `Logged in as ${form.get("username")}`, true);
  } catch (err) {
    setStatus(loginStatus, `Network error: ${err.message}`, false);
  }
});

bookingForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  if (!accessToken) {
    setStatus(bookingStatus, "Log in first", false);
    return;
  }

  const form = new FormData(bookingForm);
  bookingSubmit.disabled = true;
  setStatus(bookingStatus, "Creating booking…", true);

  try {
    const response = await fetch(`${API_BASE}/bookings`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify({
        priceCents: Number(form.get("priceCents")),
        eventId: form.get("eventId").trim(),
        seatId: form.get("seatId").trim(),
      }),
    });
    const body = await readBody(response);
    show(body);

    if (!response.ok) {
      setStatus(bookingStatus, `Booking failed (${response.status})`, false);
      return;
    }

    if (!body || !body.url) {
      setStatus(bookingStatus, "Booking created but no checkout URL returned", false);
      return;
    }

    setStatus(bookingStatus, "Redirecting to Stripe Checkout…", true);
    window.location.assign(body.url);
  } catch (err) {
    setStatus(bookingStatus, `Network error: ${err.message}`, false);
  } finally {
    bookingSubmit.disabled = !accessToken;
  }
});
