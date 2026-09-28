const express = require("express");
const path = require("node:path");

const app = express();
const frontendPath = path.join(__dirname, "..", "frontend", "public");

app.disable("x-powered-by");
app.use(express.static(frontendPath));

app.get("/health", (_request, response) => {
  response.json({ status: "ok" });
});

app.get("*path", (_request, response) => {
  response.sendFile(path.join(frontendPath, "index.html"));
});

module.exports = app;
