const formatNumber = (value, digits = 1) =>
  new Intl.NumberFormat("en-US", {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(value);

const formatCurrency = (value) =>
  new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
  }).format(value);

const toast = document.querySelector("#toast");
let activePeriod = "day";
let toastTimer;

function showToast(message, isError = false) {
  toast.textContent = message;
  toast.classList.toggle("error", isError);
  toast.classList.add("visible");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove("visible"), 2600);
}

async function getJson(path) {
  const response = await fetch(path, {
    headers: { Accept: "application/json" },
  });
  if (!response.ok) throw new Error(`Request failed (${response.status})`);
  return response.json();
}

function renderSummary(summary) {
  document.querySelector("#current-power").textContent = formatNumber(
    summary.currentPowerKw,
    2,
  );
  document.querySelector("#today-usage").textContent = formatNumber(
    summary.todayKwh,
  );
  document.querySelector("#month-cost").textContent = formatNumber(
    summary.monthCost,
    2,
  );
  document.querySelector("#carbon-saved").textContent = formatNumber(
    summary.carbonSavedKg,
  );
  const change = document.querySelector("#usage-change");
  change.textContent = `${Math.abs(summary.changePercent)}% ${summary.changePercent <= 0 ? "less" : "more"}`;
  change.classList.toggle("trend-down", summary.changePercent <= 0);
}

function renderChart(usage) {
  const width = 640;
  const height = 166;
  const left = 31;
  const right = width - 12;
  const top = 9;
  const bottom = 132;
  const max = Math.max(...usage.values) * 1.18;
  const points = usage.values.map((value, index) => {
    const x =
      left + ((right - left) * index) / Math.max(usage.values.length - 1, 1);
    const y = bottom - (value / max) * (bottom - top);
    return { x, y, value };
  });
  const line = points
    .map(
      (point, index) =>
        `${index ? "L" : "M"}${point.x.toFixed(1)},${point.y.toFixed(1)}`,
    )
    .join(" ");
  const area = `${line} L${right},${bottom} L${left},${bottom} Z`;
  const labelIndexes = [
    ...new Set([
      0,
      Math.floor((usage.labels.length - 1) / 3),
      Math.floor(((usage.labels.length - 1) * 2) / 3),
      usage.labels.length - 1,
    ]),
  ];
  const labels = labelIndexes
    .map((index) => {
      const point = points[index];
      const anchor =
        index === 0
          ? "start"
          : index === usage.labels.length - 1
            ? "end"
            : "middle";
      return `<text class="chart-label" x="${point.x}" y="158" text-anchor="${anchor}">${usage.labels[index]}</text>`;
    })
    .join("");
  const grid = [0, 1, 2, 3]
    .map((step) => {
      const y = top + ((bottom - top) * step) / 3;
      return `<line class="chart-grid-line" x1="${left}" y1="${y}" x2="${right}" y2="${y}"/>`;
    })
    .join("");
  const last = points.at(-1);
  document.querySelector("#usage-chart").innerHTML =
    `<svg viewBox="0 0 ${width} ${height}" preserveAspectRatio="none" aria-hidden="true"><defs><linearGradient id="usageFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#c4e88c" stop-opacity=".58"/><stop offset="100%" stop-color="#c4e88c" stop-opacity=".03"/></linearGradient></defs>${grid}<path class="chart-area" d="${area}"/><path class="chart-line" d="${line}"/><circle class="chart-point" cx="${last.x}" cy="${last.y}" r="4"/>${labels}</svg>`;
  const total = usage.values.reduce((sum, value) => sum + value, 0);
  document.querySelector("#chart-total").textContent = formatNumber(total);
  document.querySelector("#chart-range-label").textContent =
    activePeriod === "day" ? "today" : `this ${activePeriod}`;
  const peak = Math.max(...usage.values);
  const peakIndex = usage.values.indexOf(peak);
  document.querySelector("#chart-peak").textContent =
    `Peak ${formatNumber(peak)} kWh · ${usage.labels[peakIndex]}`;
}

function renderCategories(categories) {
  const slices = categories
    .map((category) => `${category.color} ${category.percent}%`)
    .join(", ");
  document.querySelector(".donut").style.background =
    `conic-gradient(${slices})`;
  document.querySelector("#category-list").innerHTML = categories
    .map(
      (category) =>
        `<div class="category-row"><i class="category-swatch" style="background:${category.color}"></i><span class="category-name">${category.name}</span><strong class="category-percent">${category.percent}%</strong></div>`,
    )
    .join("");
}

function renderDevices(devices) {
  const rows = devices
    .map((device) => {
      const statusClass = device.online
        ? "device-status"
        : "device-status offline";
      const statusText = device.online ? "Running" : "Standby";
      const initial = device.name.slice(0, 1).toUpperCase();
      return `<tr><td><div class="device-name-cell"><span class="device-glyph" aria-hidden="true">${initial}</span><span>${device.name}</span></div></td><td class="room-cell">${device.room}</td><td><span class="${statusClass}"><i></i>${statusText}</span></td><td class="power-cell">${formatNumber(device.powerKw, 2)} kW</td><td><button class="row-menu" type="button" aria-label="More options for ${device.name}">···</button></td></tr>`;
    })
    .join("");
  document.querySelector("#device-rows").innerHTML = rows;
  document.querySelector("#device-total").textContent = String(
    devices.length,
  ).padStart(2, "0");
  document.querySelector("#nav-device-count").textContent = String(
    devices.length,
  ).padStart(2, "0");
}

async function loadDashboard(showNotice = false) {
  try {
    const [summary, usage, devices, categories] = await Promise.all([
      getJson("/api/summary"),
      getJson(`/api/usage?period=${activePeriod}`),
      getJson("/api/devices"),
      getJson("/api/categories"),
    ]);
    renderSummary(summary);
    renderChart(usage);
    renderDevices(devices);
    renderCategories(categories);
    if (showNotice) showToast("Energy data refreshed");
  } catch (error) {
    showToast(
      "Couldn't load energy data. Check that the Java API is running.",
      true,
    );
    document.querySelector("#usage-chart").innerHTML =
      '<div class="chart-loading">Energy data is unavailable</div>';
    document.querySelector("#device-rows").innerHTML =
      '<tr><td colspan="5" class="table-message">Could not connect to the energy API.</td></tr>';
    console.error(error);
  }
}

document.querySelectorAll(".range-button").forEach((button) => {
  button.addEventListener("click", () => {
    activePeriod = button.dataset.period;
    document.querySelectorAll(".range-button").forEach((item) => {
      const selected = item === button;
      item.classList.toggle("active", selected);
      item.setAttribute("aria-pressed", String(selected));
    });
    loadDashboard();
  });
});

document
  .querySelector("#refresh-button")
  .addEventListener("click", () => loadDashboard(true));
document
  .querySelector("#all-devices-button")
  .addEventListener("click", () =>
    document
      .querySelector("#device-rows")
      .scrollIntoView({ behavior: "smooth", block: "center" }),
  );

loadDashboard();
setInterval(() => loadDashboard(), 60_000);
