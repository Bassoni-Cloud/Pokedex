import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Main {
    static List<String[]> pokemonListe = new ArrayList<>();
    static Set<String> gefangene = new HashSet<>();
    static final String STATUS_DATEI = "status.csv";

    public static void main(String[] args) throws IOException {
        ladePokedex("pokedex_bereinigt.csv");
        ladeStatus();

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", new HtmlHandler());
        server.createContext("/save", new SaveHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Server gestartet auf http://localhost:8080");
    }

    static void ladePokedex(String datei) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(datei));
        String zeile;
        while ((zeile = reader.readLine()) != null) {
            String[] teile = zeile.split(",", 2);
            if (teile.length == 2) {
                pokemonListe.add(new String[]{teile[0].trim(), teile[1].trim()});
            }
        }
        reader.close();
    }

    static void ladeStatus() {
        File file = new File(STATUS_DATEI);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                gefangene.add(line.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void speichereStatus(Set<String> neueGefangene) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(STATUS_DATEI))) {
            for (String id : neueGefangene) {
                writer.write(id);
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        gefangene = neueGefangene;
    }

    static class HtmlHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder html = new StringBuilder();
            html.append("<html><head><meta charset='UTF-8'><title>Pokédex</title>");
            html.append("<style>table{border-collapse:collapse;width:100%;}th,td{border:1px solid #ccc;padding:5px;}input[type='text']{margin:10px 0;}</style>");
            html.append("<script>");
            html.append("function searchPokemon() {");
            html.append("let input = document.getElementById('searchInput').value.toLowerCase();");
            html.append("let rows = document.querySelectorAll('table tr');");
            html.append("for (let i = 1; i < rows.length; i++) {");
            html.append("let name = rows[i].cells[1].textContent.toLowerCase();");
            html.append("rows[i].style.display = name.includes(input) ? '' : 'none';");
            html.append("}}");
            html.append("function speichern() {");
            html.append("let checked = document.querySelectorAll('input[type=checkbox]:checked');");
            html.append("let ids = Array.from(checked).map(cb => cb.dataset.id);");
            html.append("fetch('/save', {method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:'ids='+ids.join(',')})");
            html.append(".then(() => alert('Gespeichert!'));");
            html.append("}");
            html.append("</script></head><body>");

            html.append("<h1>Mein Pokédex</h1>");
            html.append("<input type='text' id='searchInput' oninput='searchPokemon()' placeholder='Nach Name suchen...'>");
            html.append("<button onclick='speichern()'>Speichern</button>");

            html.append("<table><tr><th>Nr.</th><th>Name</th><th>Gefangen</th></tr>");
            for (String[] p : pokemonListe) {
                String id = p[0];
                String name = p[1];
                boolean hat = gefangene.contains(id);
                html.append("<tr><td>").append(id).append("</td><td>").append(name)
                        .append("</td><td><input type='checkbox' data-id='").append(id).append("'")
                        .append(hat ? " checked" : "").append("></td></tr>");
            }
            html.append("</table></body></html>");

            byte[] response = html.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        }
    }

    static class SaveHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
                return;
            }

            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            String formData = br.readLine();
            br.close();

            Set<String> neueGefangene = new HashSet<>();
            if (formData != null && formData.startsWith("ids=")) {
                String csv = URLDecoder.decode(formData.substring(4), "UTF-8");
                neueGefangene.addAll(Arrays.asList(csv.split(",")));
            }

            speichereStatus(neueGefangene);

            exchange.sendResponseHeaders(200, -1);
        }
    }
}
