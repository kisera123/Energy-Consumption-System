import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public final class EnergyApiServer {
    private static final Path FRONTEND = Path.of("src", "frontend", "public").toAbsolutePath().normalize();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final List<Device> DEVICES = List.of(
        new Device("device-1", "HVAC System", "Climate", 1.42, true),
        new Device("device-2", "Water Heater", "Water", 0.86, true),
        new Device("device-3", "Refrigerator", "Kitchen", 0.18, true),
        new Device("device-4", "Washer & Dryer", "Laundry", 0.0, false),
        new Device("device-5", "Lighting", "Living spaces", 0.31, true),
        new Device("device-6", "EV Charger", "Garage", 0.0, false)
    );

    private EnergyApiServer() {}

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors())));
        server.createContext("/api/", EnergyApiServer::handleApi);
        server.createContext("/health", exchange -> sendJson(exchange, 200, "{\"status\":\"ok\"}"));
        server.createContext("/", EnergyApiServer::serveFrontend);
        server.start();
        System.out.println("Energy system available at http://localhost:" + port);
    }

    private static void handleApi(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        switch (path) {
            case "/api/summary" -> sendJson(exchange, 200, summaryJson());
            case "/api/usage" -> sendJson(exchange, 200, usageJson(exchange.getRequestURI().getRawQuery()));
            case "/api/devices" -> sendJson(exchange, 200, devicesJson());
            case "/api/readings" -> sendJson(exchange, 200, readingsJson());
            case "/api/categories" -> sendJson(exchange, 200, categoriesJson());
            default -> sendJson(exchange, 404, "{\"error\":\"Endpoint not found\"}");
        }
    }

    private static String summaryJson() {
        return "{\"currentPowerKw\":2.77,\"todayKwh\":18.6,\"monthKwh\":486.2,\"monthCost\":84.29,\"changePercent\":-8.4,\"carbonSavedKg\":7.9,\"activeDevices\":4,\"totalDevices\":" + DEVICES.size() + "}";
    }

    private static String usageJson(String query) {
        String period = query == null ? "day" : query.replace("period=", "");
        double[] values;
        String[] labels;
        if ("week".equals(period)) {
            values = new double[] {15.4, 18.2, 16.8, 21.5, 19.1, 24.3, 18.6};
            labels = new String[] {"Tue", "Wed", "Thu", "Fri", "Sat", "Sun", "Mon"};
        } else if ("month".equals(period)) {
            values = new double[] {14.2, 16.7, 15.8, 18.4, 17.1, 19.8, 18.9, 16.6, 20.1, 17.6, 19.3, 18.6};
            labels = new String[] {"1", "3", "5", "7", "9", "11", "13", "15", "17", "19", "21", "Today"};
        } else {
            values = new double[] {1.2, 0.9, 1.4, 1.0, 0.8, 2.2, 1.6, 1.5, 2.4, 2.1, 1.7, 1.8};
            labels = new String[] {"7 AM", "8 AM", "9 AM", "10 AM", "11 AM", "12 PM", "1 PM", "2 PM", "3 PM", "4 PM", "5 PM", "Now"};
        }
        StringBuilder json = new StringBuilder("{\"unit\":\"kWh\",\"labels\":[");
        appendStrings(json, labels);
        json.append("],\"values\":[");
        appendNumbers(json, values);
        return json.append("]}").toString();
    }

    private static String categoriesJson() {
        return "[{\"name\":\"Climate\",\"percent\":36,\"color\":\"#a9d95d\"},"
            + "{\"name\":\"Kitchen\",\"percent\":22,\"color\":\"#7899df\"},"
            + "{\"name\":\"Water\",\"percent\":18,\"color\":\"#ff907a\"},"
            + "{\"name\":\"Lighting\",\"percent\":13,\"color\":\"#f4c65f\"},"
            + "{\"name\":\"Other\",\"percent\":11,\"color\":\"#c8d3c7\"}]";
    }

    private static String devicesJson() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < DEVICES.size(); i++) {
            Device device = DEVICES.get(i);
            if (i > 0) json.append(',');
            json.append("{\"id\":\"").append(device.id()).append("\",\"name\":\"")
                .append(escape(device.name())).append("\",\"room\":\"").append(escape(device.room()))
                .append("\",\"powerKw\":").append(device.powerKw())
                .append(",\"online\":").append(device.online()).append('}');
        }
        return json.append(']').toString();
    }

    private static String readingsJson() {
        List<String> dates = new ArrayList<>();
        for (int offset = 6; offset >= 0; offset--) dates.add(LocalDate.now().minusDays(offset).format(DATE_FORMAT));
        StringBuilder json = new StringBuilder("{\"readings\":[");
        for (int i = 0; i < dates.size(); i++) {
            if (i > 0) json.append(',');
            json.append("{\"date\":\"").append(dates.get(i)).append("\",\"consumptionKwh\":")
                .append(String.format(Locale.US, "%.1f", 17.2 + ((i * 7) % 13) * 0.8)).append('}');
        }
        return json.append("]}").toString();
    }

    private static void serveFrontend(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();
        Path requested = FRONTEND.resolve(requestPath.equals("/") ? "index.html" : requestPath.substring(1)).normalize();
        if (!requested.startsWith(FRONTEND) || !Files.isRegularFile(requested)) requested = FRONTEND.resolve("index.html");
        if (!Files.isRegularFile(requested)) {
            sendText(exchange, 404, "Dashboard files not found. Start the server from the project root.", "text/plain; charset=utf-8");
            return;
        }
        String type = Files.probeContentType(requested);
        if (type == null) type = "application/octet-stream";
        if (type.startsWith("text/") || type.equals("application/javascript")) type += "; charset=utf-8";
        byte[] body = Files.readAllBytes(requested);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static void appendStrings(StringBuilder json, String[] values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) json.append(',');
            json.append('\"').append(escape(values[i])).append('\"');
        }
    }

    private static void appendNumbers(StringBuilder json, double[] values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) json.append(',');
            json.append(String.format(Locale.US, "%.1f", values[i]));
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        sendText(exchange, status, json, "application/json; charset=utf-8");
    }

    private static void sendText(HttpExchange exchange, int status, String text, String contentType) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private record Device(String id, String name, String room, double powerKw, boolean online) {}
}